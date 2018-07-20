#include "transactionrecord.h"

#include "base58.h"
#include "timedata.h"
#include "wallet.h"

/* Return positive answer if transaction should be shown in list.
 */
bool TransactionRecord::showTransaction(const CWalletTx &wtx)
{
    if (wtx.IsCoinBase())
    {
        // Ensures we show generated coins / mined transactions at depth 1
        if (!wtx.IsInMainChain())
        {
            return false;
        }
    }
    return true;
}

/*
 * Decompose CWallet transaction to model transaction records.
 */
QList<TransactionRecord> TransactionRecord::decomposeTransaction(const CWallet *wallet, const CWalletTx &wtx)
{
    QList<TransactionRecord> parts;
    int64_t nTime = wtx.GetTxTime();
    int64_t nCredit = wtx.GetValueOut();
    int64_t nDebit = wtx.GetDebit();
    int64_t nNet = nCredit - nDebit;
    uint256 hash = wtx.GetHash(), hashPrev = 0;
    std::map<std::string, std::string> mapValue = wtx.mapValue;

    if (wtx.IsCoinBase() || wtx.IsCoinStake())
    {
        for(unsigned int nOut = 0; nOut < wtx.vout.size(); nOut++)
        {
            const CTxOut& txout = wtx.vout[nOut];
            if(wallet->IsMine(txout))
            {
                TransactionRecord sub(hash, nTime);
                sub.idx = nOut;
                sub.credit = txout.nValue;
                sub.type = TransactionRecord::Generated;
                CTxDestination address;
                if (ExtractDestination(txout.scriptPubKey, address))
                    sub.address = CBitcoinAddress(address).ToString();
                else
                    sub.address = mapValue["from"];

                if(wtx.IsCoinStake()) // Generated (proof-of-stake)
                {
                    if (hashPrev == hash)
                        continue; // last coinstake output
                    sub.credit = nNet > 0 ? nNet : wtx.GetValueOut() - nDebit;
                    hashPrev = hash;
                }
                parts.append(sub);
            }
        }
    }
    else
    {
        bool fAllFromMe = true;
        BOOST_FOREACH(const CTxIn& txin, wtx.vin)
            fAllFromMe = fAllFromMe && wallet->IsMine(txin);

        bool fAllToMe = true;
        BOOST_FOREACH(const CTxOut& txout, wtx.vout)
            fAllToMe = fAllToMe && wallet->IsMine(txout);

        if(nNet > 0) // receive from other
        {
            for(unsigned int nOut = 0; nOut < wtx.vout.size(); nOut++)
            {
                const CTxOut& txout = wtx.vout[nOut];
                if(wallet->IsMine(txout))
                {
                    TransactionRecord sub(hash, nTime);
                    sub.idx = nOut;
                    CTxDestination address;
                    if (ExtractDestination(txout.scriptPubKey, address) && IsMine(*wallet, address))
                    {
                        sub.type = TransactionRecord::RecvWithAddress;
                        sub.address = CBitcoinAddress(address).ToString();
                    }
                    else
                    {
                        sub.type = TransactionRecord::RecvFromOther;
                        sub.address = mapValue["from"];
                    }


                    sub.credit = txout.nValue;
                    parts.append(sub);
                }
            }
        }
        else
        {
            if (fAllFromMe && fAllToMe) // pay to self
            {
                parts.append(TransactionRecord(hash, nTime, TransactionRecord::SendToSelf, "", nDebit, nCredit));
            }
            else if (fAllFromMe)
            {
                {
                    bool flag = false;
                    for(unsigned int nOut = 0; nOut < wtx.vout.size(); nOut++)
                    {
                        const CTxOut& txout = wtx.vout[nOut];
                        if(wallet->IsMine(txout)) // ignore sendtoself and change txout
                            continue;

                        TransactionRecord sub(hash, nTime);
                        sub.idx = nOut;

                        CTxDestination address;
                        if (ExtractDestination(txout.scriptPubKey, address)) // sent to Factoken address
                        {
                            sub.type = TransactionRecord::SendToAddress;
                            sub.address = CBitcoinAddress(address).ToString();
                        }
                        else // sent to IP, or other non-address transaction like OP_EVAL
                        {
                            sub.type = TransactionRecord::SendToOther;
                            sub.address = mapValue["to"];
                        }

                        sub.debit = txout.nValue;
                        sub.credit = 0;
                        if(!flag)
                        {
                            sub.fee = nDebit - nCredit;
                            flag = true;
                        }
                        parts.append(sub);
                    }
                }
            }
            else
            {
                //
                // Mixed debit transaction, can't break down payees
                //
                parts.append(TransactionRecord(hash, nTime, TransactionRecord::Other, "", nNet, 0));
            }
        }
    }

    return parts;
}

void TransactionRecord::updateStatus(const CWalletTx &wtx)
{
    AssertLockHeld(cs_main);
    // Determine transaction status

    // Find the block the tx is in
    CBlockIndex* pindex = NULL;
    std::map<uint256, CBlockIndex*>::iterator mi = mapBlockIndex.find(wtx.hashBlock);
    if (mi != mapBlockIndex.end())
        pindex = (*mi).second;

    // Sort order, unrecorded transactions sort to the top
    status.sortKey = strprintf("%010d-%01d-%010u-%03d",
        (pindex ? pindex->nHeight : std::numeric_limits<int>::max()),
        (wtx.IsCoinBase() ? 1 : 0),
        wtx.nTimeReceived,
        idx);
    status.countsForBalance = wtx.IsTrusted() && !(wtx.GetBlocksToMaturity() > 0);
    status.depth = wtx.GetDepthInMainChain();
    status.cur_num_blocks = nBestHeight;

    if (!IsFinalTx(wtx, nBestHeight + 1))
    {
        if (wtx.nLockTime < LOCKTIME_THRESHOLD)
        {
            status.status = TransactionStatus::OpenUntilBlock;
            status.open_for = wtx.nLockTime - nBestHeight;
        }
        else
        {
            status.status = TransactionStatus::OpenUntilDate;
            status.open_for = wtx.nLockTime;
        }
    }

    // For generated transactions, determine maturity
    else if(type == TransactionRecord::Generated)
    {
        if (wtx.GetBlocksToMaturity() > 0)
        {
            status.status = TransactionStatus::Immature;

            if (wtx.IsInMainChain())
            {
                status.matures_in = wtx.GetBlocksToMaturity();

                // Check if the block was requested by anyone
                if (GetAdjustedTime() - wtx.nTimeReceived > 2 * 60 && wtx.GetRequestCount() == 0)
                    status.status = TransactionStatus::MaturesWarning;
            }
            else
            {
                status.status = TransactionStatus::NotAccepted;
            }
        }
        else
        {
            status.status = TransactionStatus::Confirmed;
        }
    }
    else
    {
        if (status.depth < 0)
        {
            status.status = TransactionStatus::Conflicted;
        }
        else if (GetAdjustedTime() - wtx.nTimeReceived > 2 * 60 && wtx.GetRequestCount() == 0)
        {
            status.status = TransactionStatus::Offline;
        }
        else if (status.depth == 0)
        {
            status.status = TransactionStatus::Unconfirmed;
        }
        else if (status.depth < RecommendedNumConfirmations)
        {
            status.status = TransactionStatus::Confirming;
        }
        else
        {
            status.status = TransactionStatus::Confirmed;
        }
    }
}

bool TransactionRecord::statusUpdateNeeded()
{
    AssertLockHeld(cs_main);
    return status.cur_num_blocks != nBestHeight;
}

QString TransactionRecord::getTxID() const
{
    return formatSubTxId(hash, idx);
}

QString TransactionRecord::formatSubTxId(const uint256 &hash, int vout)
{
    return QString::fromStdString(hash.ToString() + strprintf("-%03d", vout));
}

