// Copyright (c) 2009-2010 Satoshi Nakamoto
// Copyright (c) 2009-2012 The Bitcoin developers
// Copyright (c) 2012-2014 The NovaCoin developers
// Copyright (c) 2014-2016 The BlackCoin developers
// Copyright (c) 2017 The Factoken developers
// Distributed under the MIT/X11 software license, see the accompanying
// file COPYING or http://www.opensource.org/licenses/mit-license.php.

#include "txdb.h"
#include "miner.h"
#include "kernel.h"
#include "base58.h"
#include "sync.h"
#include "ntp_client.h"

using namespace std;

//////////////////////////////////////////////////////////////////////////////
//
// FactokenMiner
//

extern unsigned int nMinerSleep;

#define POW_MINER_SIZE          5
static string strMiners[POW_MINER_SIZE] = {
"EHXmW4BXAuTCtJZ6HJL5RzsHMc3abcD2eK",
"EeUj2HwmJUckA5FCvVp2WDRP4Fj1vqxg6C",
"ENejx5GuxGSJxbVoouWzFTQMv64NTtj31d",
"EbMuGjnebwiciU4dUnHQPxdStGktq9MSpD",
"EUsVpW2P9Wqo3QdaLM1S7co79ofgQ3J3Pm"
};

static int nRewardPercents[POW_MINER_SIZE] = {20, 20, 20, 20, 20};

static CCriticalSection cs_bNtpFirst;
static bool bNtpFirst = false;

int static FormatHashBlocks(void* pbuffer, unsigned int len)
{
    unsigned char* pdata = (unsigned char*)pbuffer;
    unsigned int blocks = 1 + ((len + 8) / 64);
    unsigned char* pend = pdata + 64 * blocks;
    memset(pdata + len, 0, 64 * blocks - len);
    pdata[len] = 0x80;
    unsigned int bits = len * 8;
    pend[-1] = (bits >> 0) & 0xff;
    pend[-2] = (bits >> 8) & 0xff;
    pend[-3] = (bits >> 16) & 0xff;
    pend[-4] = (bits >> 24) & 0xff;
    return blocks;
}

static const unsigned int pSHA256InitState[8] =
{0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a, 0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19};

void SHA256Transform(void* pstate, void* pinput, const void* pinit)
{
    SHA256_CTX ctx;
    unsigned char data[64];

    SHA256_Init(&ctx);

    for (int i = 0; i < 16; i++)
        ((uint32_t*)data)[i] = ByteReverse(((uint32_t*)pinput)[i]);

    for (int i = 0; i < 8; i++)
        ctx.h[i] = ((uint32_t*)pinit)[i];

    SHA256_Update(&ctx, data, sizeof(data));
    for (int i = 0; i < 8; i++)
        ((uint32_t*)pstate)[i] = ctx.h[i];
}

// Some explaining would be appreciated
class COrphan
{
public:
    CTransaction* ptx;
    set<uint256> setDependsOn;
    int64_t nTxFee;

    COrphan(CTransaction* ptxIn)
    {
        ptx = ptxIn;
        nTxFee = 0;
    }
};


uint64_t nLastBlockTx = 0;
uint64_t nLastBlockSize = 0;
int64_t nLastCoinStakeSearchInterval = 0;
 
// We want to sort transactions by fee, so:
typedef boost::tuple<int64_t, CTransaction*> TxPriority;
class TxPriorityCompare
{
public:
    bool operator()(const TxPriority& a, const TxPriority& b)
    {
        return a.get<0>() < b.get<0>();
    }
};

// CreateNewBlock: create new block (without proof-of-work/proof-of-stake)
CBlock* CreateNewBlock(CReserveKey& reservekey, bool fProofOfStake, int64_t* pFees)
{
    // Create new block
    auto_ptr<CBlock> pblock(new CBlock());
    if (!pblock.get())
        return NULL;

    CBlockIndex* pindexPrev = pindexBest;
    int nHeight = pindexPrev->nHeight + 1;

    // Create coinbase tx
    CTransaction txNew;
    txNew.vin.resize(1);
    txNew.vin[0].prevout.SetNull();

    if (!fProofOfStake)
    {
        if(!TestNet())
        {
            txNew.vout.resize(POW_MINER_SIZE);
            for(int i = 0; i < POW_MINER_SIZE; i++)
                txNew.vout[i].scriptPubKey.SetDestination(CBitcoinAddress(strMiners[i]).Get());
        }
        else
        {
            txNew.vout.resize(1);
            CPubKey pubkey;
            if (!reservekey.GetReservedKey(pubkey))
                return NULL;
            txNew.vout[0].scriptPubKey.SetDestination(pubkey.GetID());
        }
    }
    else
    {
        txNew.vout.resize(1);
        // Height first in coinbase required for block.version=2
        txNew.vin[0].scriptSig = (CScript() << nHeight) + COINBASE_FLAGS;
        assert(txNew.vin[0].scriptSig.size() <= 100);

        txNew.vout[0].SetEmpty();
    }

    // Add our coinbase tx as first transaction
    pblock->vtx.push_back(txNew);

    // Largest block you're willing to create:
    unsigned int nBlockMaxSize = GetArg("-blockmaxsize", MAX_BLOCK_SIZE_GEN/2);
    // Limit to betweeen 1K and MAX_BLOCK_SIZE-1K for sanity:
    nBlockMaxSize = std::max((unsigned int)1000, std::min((unsigned int)(MAX_BLOCK_SIZE-1000), nBlockMaxSize));

    // Minimum block size you want to create; block will be filled with free transactions
    // until there are no more or the block reaches this size:
    unsigned int nBlockMinSize = GetArg("-blockminsize", 0);
    nBlockMinSize = std::min(nBlockMaxSize, nBlockMinSize);

    // Fee-per-kilobyte amount considered the same as "free"
    // Be careful setting this: if you set it to zero then
    // a transaction spammer can cheaply fill blocks using
    // 1-satoshi-fee transactions. It should be set above the real
    // cost to you of processing a transaction.
    int64_t nMinTxFee = MIN_TX_FEE;
    if (mapArgs.count("-mintxfee"))
        ParseMoney(mapArgs["-mintxfee"], nMinTxFee);
    if(nMinTxFee < MIN_TX_FEE)
        nMinTxFee = MIN_TX_FEE;

    pblock->nBits = GetNextTargetRequired(pindexPrev, fProofOfStake);

    // Collect memory pool transactions into the block
    int64_t nFees = 0;
    {
        LOCK2(cs_main, mempool.cs);
        CTxDB txdb("r");

        // Priority order to process transactions
        list<COrphan> vOrphan; // list memory doesn't move
        map<uint256, vector<COrphan*> > mapDependers;

        // This vector will be sorted into a priority queue:
        vector<TxPriority> vecPriority;
        vecPriority.reserve(mempool.mapTx.size());
        for (map<uint256, CTransaction>::iterator mi = mempool.mapTx.begin(); mi != mempool.mapTx.end(); ++mi)
        {
            CTransaction& tx = (*mi).second;
            if (tx.IsCoinBase() || tx.IsCoinStake() || !IsFinalTx(tx, nHeight))
                continue;

            COrphan* porphan = NULL;
            int64_t nTotalIn = 0;
            bool fMissingInputs = false;
            BOOST_FOREACH(const CTxIn& txin, tx.vin)
            {
                // Read prev transaction
                CTransaction txPrev;
                CTxIndex txindex;
                if (!txPrev.ReadFromDisk(txdb, txin.prevout, txindex))
                {
                    // This should never happen; all transactions in the memory
                    // pool should connect to either transactions in the chain
                    // or other transactions in the memory pool.
                    if (!mempool.mapTx.count(txin.prevout.hash))
                    {
                        LogPrintf("ERROR: mempool transaction missing input\n");
                        if (fDebug) assert("mempool transaction missing input" == 0);
                        fMissingInputs = true;
                        if (porphan)
                            vOrphan.pop_back();
                        break;
                    }

                    // Has to wait for dependencies
                    if (!porphan)
                    {
                        // Use list for automatic deletion
                        vOrphan.push_back(COrphan(&tx));
                        porphan = &vOrphan.back();
                    }
                    mapDependers[txin.prevout.hash].push_back(porphan);
                    porphan->setDependsOn.insert(txin.prevout.hash);
                    nTotalIn += mempool.mapTx[txin.prevout.hash].vout[txin.prevout.n].nValue;
                    continue;
                }
                int64_t nValueIn = txPrev.vout[txin.prevout.n].nValue;
                nTotalIn += nValueIn;
            }
            if (fMissingInputs) continue;

            // This is a more accurate fee-per-kilobyte than is used by the client code, because the
            // client code rounds up the size to the nearest 1K. That's good, because it gives an
            // incentive to create smaller transactions.
            int64_t nTxFee = nTotalIn - tx.GetValueOut();

            if (porphan)
            {
                porphan->nTxFee = nTxFee;
            }
            else
                vecPriority.push_back(TxPriority(nTxFee, &(*mi).second));
        }

        // Collect transactions into block
        map<uint256, CTxIndex> mapTestPool;
        uint64_t nBlockSize = 1000;
        uint64_t nBlockTx = 0;
        int nBlockSigOps = 100;

        TxPriorityCompare comparer;
        std::make_heap(vecPriority.begin(), vecPriority.end(), comparer);

        while (!vecPriority.empty())
        {
            // Take highest priority transaction off the priority queue:
            int64_t nTxFee = vecPriority.front().get<0>();
            CTransaction& tx = *(vecPriority.front().get<1>());

            std::pop_heap(vecPriority.begin(), vecPriority.end(), comparer);
            vecPriority.pop_back();

            // Size limits
            unsigned int nTxSize = ::GetSerializeSize(tx, SER_NETWORK, PROTOCOL_VERSION);
            if (nBlockSize + nTxSize >= nBlockMaxSize)
                continue;

            // Legacy limits on sigOps:
            unsigned int nTxSigOps = GetLegacySigOpCount(tx);
            if (nBlockSigOps + nTxSigOps >= MAX_BLOCK_SIGOPS)
                continue;

            // Timestamp limit
            if (tx.nTime > GetAdjustedTime() || (fProofOfStake && tx.nTime > pblock->vtx[0].nTime))
                continue;

            // Skip free transactions if we're past the minimum block size:
            if ((nTxFee < nMinTxFee) && (nBlockSize + nTxSize >= nBlockMinSize))
                continue;

            // Connecting shouldn't fail due to dependency on other memory pool transactions
            // because we're already processing them in order of dependency
            map<uint256, CTxIndex> mapTestPoolTmp(mapTestPool);
            MapPrevTx mapInputs;
            bool fInvalid;
            if (!tx.FetchInputs(txdb, mapTestPoolTmp, false, true, mapInputs, fInvalid))
                continue;

            int64_t nTxFees = tx.GetValueIn(mapInputs)-tx.GetValueOut();
            if (nTxFees < MIN_TX_FEE)
                continue;

            nTxSigOps += GetP2SHSigOpCount(tx, mapInputs);
            if (nBlockSigOps + nTxSigOps >= MAX_BLOCK_SIGOPS)
                continue;

            // Note that flags: we don't want to set mempool/IsStandard()
            // policy here, but we still have to ensure that the block we
            // create only contains transactions that are valid in new blocks.
            if (!tx.ConnectInputs(txdb, mapInputs, mapTestPoolTmp, CDiskTxPos(1,1,1), pindexPrev, false, true, MANDATORY_SCRIPT_VERIFY_FLAGS))
                continue;
            mapTestPoolTmp[tx.GetHash()] = CTxIndex(CDiskTxPos(1,1,1), tx.vout.size());
            swap(mapTestPool, mapTestPoolTmp);

            // Added
            pblock->vtx.push_back(tx);
            nBlockSize += nTxSize;
            ++nBlockTx;
            nBlockSigOps += nTxSigOps;
            nFees += nTxFees;

            if (fDebug)
                LogPrintf("txfee %lld txid %s\n", nTxFee, tx.GetHash().ToString());

            // Add transactions that depend on this one to the priority queue
            uint256 hash = tx.GetHash();
            if (mapDependers.count(hash))
            {
                BOOST_FOREACH(COrphan* porphan, mapDependers[hash])
                {
                    if (!porphan->setDependsOn.empty())
                    {
                        porphan->setDependsOn.erase(hash);
                        if (porphan->setDependsOn.empty())
                        {
                            vecPriority.push_back(TxPriority(porphan->nTxFee, porphan->ptx));
                            std::push_heap(vecPriority.begin(), vecPriority.end(), comparer);
                        }
                    }
                }
            }
        }

        nLastBlockTx = nBlockTx;
        nLastBlockSize = nBlockSize;

        if (fDebug)
            LogPrintf("CreateNewBlock(): total size %u\n", nBlockSize);

        if (!fProofOfStake)
        {
            int64_t nPoWReward = GetProofOfWorkReward(0);
            if(!TestNet())
            {
                for(int i = 0; i < POW_MINER_SIZE; i++)
                    pblock->vtx[0].vout[i].nValue = nPoWReward * nRewardPercents[i] / 100;
                pblock->vtx[0].vout[0].nValue += nFees;
            }
            else
                pblock->vtx[0].vout[0].nValue = GetProofOfWorkReward(nFees);
        }

        if (pFees)
            *pFees = nFees;

        // Fill in header
        pblock->hashPrevBlock  = pindexPrev->GetBlockHash();
        pblock->nTime          = max(pindexPrev->GetPastTimeLimit()+1, pblock->GetMaxTransactionTime());
        if (!fProofOfStake)
            pblock->UpdateTime(pindexPrev);
        pblock->nNonce         = 0;
    }

    return pblock.release();
}


void IncrementExtraNonce(CBlock* pblock, CBlockIndex* pindexPrev, unsigned int& nExtraNonce)
{
    // Update nExtraNonce
    static uint256 hashPrevBlock;
    if (hashPrevBlock != pblock->hashPrevBlock)
    {
        nExtraNonce = 0;
        hashPrevBlock = pblock->hashPrevBlock;
    }
    ++nExtraNonce;

    unsigned int nHeight = pindexPrev->nHeight+1; // Height first in coinbase required for block.version=2
    pblock->vtx[0].vin[0].scriptSig = (CScript() << nHeight << CBigNum(nExtraNonce)) + COINBASE_FLAGS;
    assert(pblock->vtx[0].vin[0].scriptSig.size() <= 100);

    pblock->hashMerkleRoot = pblock->BuildMerkleTree();
}


void FormatHashBuffers(CBlock* pblock, char* pmidstate, char* pdata, char* phash1)
{
    //
    // Pre-build hash buffers
    //
    struct
    {
        struct unnamed2
        {
            int nVersion;
            uint256 hashPrevBlock;
            uint256 hashMerkleRoot;
            unsigned int nTime;
            unsigned int nBits;
            unsigned int nNonce;
        }
        block;
        unsigned char pchPadding0[64];
        uint256 hash1;
        unsigned char pchPadding1[64];
    }
    tmp;
    memset(&tmp, 0, sizeof(tmp));

    tmp.block.nVersion       = pblock->nVersion;
    tmp.block.hashPrevBlock  = pblock->hashPrevBlock;
    tmp.block.hashMerkleRoot = pblock->hashMerkleRoot;
    tmp.block.nTime          = pblock->nTime;
    tmp.block.nBits          = pblock->nBits;
    tmp.block.nNonce         = pblock->nNonce;

    FormatHashBlocks(&tmp.block, sizeof(tmp.block));
    FormatHashBlocks(&tmp.hash1, sizeof(tmp.hash1));

    // Byte swap all the input buffer
    for (unsigned int i = 0; i < sizeof(tmp)/4; i++)
        ((unsigned int*)&tmp)[i] = ByteReverse(((unsigned int*)&tmp)[i]);

    // Precalc the first half of the first hash, which stays constant
    SHA256Transform(pmidstate, &tmp.block, pSHA256InitState);

    memcpy(pdata, &tmp.block, 128);
    memcpy(phash1, &tmp.hash1, 64);
}


bool CheckWork(CBlock* pblock, CWallet& wallet, CReserveKey& reservekey)
{
    uint256 hashBlock = pblock->GetHash();
    uint256 hashProof = pblock->GetPoWHash();
    uint256 hashTarget = CBigNum().SetCompact(pblock->nBits).getuint256();

    if(!pblock->IsProofOfWork())
        return error("CheckWork() : %s is not a proof-of-work block", hashBlock.GetHex());

    if (hashProof > hashTarget)
        return error("CheckWork() : proof-of-work not meeting target");

    //// debug print
    LogPrintf("CheckWork() : new proof-of-work block found  \n  proof hash: %s  \ntarget: %s\n", hashProof.GetHex(), hashTarget.GetHex());
    LogPrintf("%s\n", pblock->ToString());
    LogPrintf("generated %s\n", FormatMoney(pblock->vtx[0].vout[0].nValue));

    // Found a solution
    {
        LOCK(cs_main);
        if (pblock->hashPrevBlock != hashBestChain)
            return error("CheckWork() : generated block is stale");

        // Remove key from key pool
        reservekey.KeepKey();

        // Track how many getdata requests this block gets
        {
            LOCK(wallet.cs_wallet);
            wallet.mapRequestCount[hashBlock] = 0;
        }

        // Process this block the same as if we had received it from another node
        if (!ProcessBlock(NULL, pblock))
            return error("CheckWork() : ProcessBlock, block not accepted");
    }

    return true;
}

bool CheckStake(CBlock* pblock, CWallet& wallet)
{
    uint256 proofHash = 0, hashTarget = 0;
    uint256 hashBlock = pblock->GetHash();

    if(!pblock->IsProofOfStake())
        return error("CheckStake() : %s is not a proof-of-stake block", hashBlock.GetHex());

    // verify hash target and signature of coinstake tx
    if (!CheckProofOfStake(mapBlockIndex[pblock->hashPrevBlock], pblock->vtx[1], pblock->nBits, proofHash, hashTarget))
        return error("CheckStake() : proof-of-stake checking failed");

    //// debug print
    LogPrintf("CheckStake() : new proof-of-stake block found  \n  hash: %s \nproofhash: %s  \ntarget: %s\n", hashBlock.GetHex(), proofHash.GetHex(), hashTarget.GetHex());
    LogPrintf("%s\n", pblock->ToString());
    LogPrintf("out %s\n", FormatMoney(pblock->vtx[1].GetValueOut()));

    // Found a solution
    {
        LOCK(cs_main);
        if (pblock->hashPrevBlock != hashBestChain)
            return error("CheckStake() : generated block is stale");

        // Track how many getdata requests this block gets
        {
            LOCK(wallet.cs_wallet);
            wallet.mapRequestCount[hashBlock] = 0;
        }

        // Process this block the same as if we had received it from another node
        if (!ProcessBlock(NULL, pblock))
            return error("CheckStake() : ProcessBlock, block not accepted");
    }

    return true;
}

void ThreadPoWMiner(CWallet* pwallet)
{
    SetThreadPriority(THREAD_PRIORITY_LOWEST);
    RenameThread("factoken-pow-miner");
    CReserveKey reservekey(pwallet);
    bool fTryToSync = true;
    while(true)
    {		
        while(pwallet->IsLocked())
            MilliSleep(1000);
        while (vNodes.empty() || IsInitialBlockDownload())
        {
            fTryToSync = true;
            MilliSleep(1000);
        }

        if (fTryToSync)
        {
            fTryToSync = false;
            if (vNodes.size() < 3 || pindexBest->GetBlockTime() < GetTime() - (pindexBest->nHeight == 0 ? nMaxTipAge : 10 * 60))
            {
				printf("%s %d,POW   1111\n",__FILE__,__LINE__);
                MilliSleep(5000);
                continue;
            }
        }

        if(pindexBest->nHeight >= Params().LastPOWBlock())
            break;

        {
            LOCK(cs_bNtpFirst);
            if(bNtpFirst) // need to sync ntp server first
            {
                MilliSleep(1000);
                continue;
            }
        }

        //
        // Create new block
        //
        auto_ptr<CBlock> pblock(CreateNewBlock(reservekey));
        if (!pblock.get())
            return;
        static unsigned int nExtraNonce = 0;
        IncrementExtraNonce(pblock.get(), pindexBest, nExtraNonce);

        uint256 hashTarget = CBigNum().SetCompact(pblock->nBits).getuint256();
        uint256 thash;
        while(true)
        {
            pblock->nNonce +=1;
            /*
            if(pblock->nNonce % 10000 == 0)
                printf("%d\n", pblock->nNonce);
            */
            thash = scrypt_blockhash(CVOIDBEGIN(pblock->nVersion));
            if(thash <= hashTarget)
                break;
        }
        SetThreadPriority(THREAD_PRIORITY_NORMAL);
        if(!CheckWork(pblock.get(), *pwallet, reservekey))
            break;
        SetThreadPriority(THREAD_PRIORITY_LOWEST);
        MilliSleep(500);
    }
}

void ThreadStakeMiner(CWallet *pwallet)
{
    SetThreadPriority(THREAD_PRIORITY_LOWEST);

    // Make this thread recognisable as the mining thread
    RenameThread("factoken-stake-miner");

    CReserveKey reservekey(pwallet);

    bool fTryToSync = true;

    while (true)
    {
		//printf("%s %d,POs   1111\n",__FILE__,__LINE__);
        while (pwallet->IsLocked())
        {
            nLastCoinStakeSearchInterval = 0;
            MilliSleep(1000);
        }

        while (vNodes.empty() || IsInitialBlockDownload())
        {
            nLastCoinStakeSearchInterval = 0;
            fTryToSync = true;
            MilliSleep(1000);
        }

        if (fTryToSync)
        {
            fTryToSync = false;
            if (vNodes.size() < 3 || pindexBest->GetBlockTime() < GetTime() - (pindexBest->nHeight == 0 ? nMaxTipAge : 10 * 60))
            {
                MilliSleep(5000);
                continue;
            }
        }

        if (!TestNet() && pindexBest->nHeight < Params().LastPOWBlock()) // main net: need to generate enough blocks created by pow at least
        {
            MilliSleep(5000);
            continue;
        }

        {
            LOCK(cs_bNtpFirst);
            if(bNtpFirst) // need to sync ntp server first
            {
                MilliSleep(1000);
                continue;
            }
        }

        //
        // Create new block
        //
        int64_t nFees;
        auto_ptr<CBlock> pblock(CreateNewBlock(reservekey, true, &nFees));
        if (!pblock.get())
            return;

        // Trying to sign a block
        if (pblock->SignBlock(*pwallet, nFees))
        {
            SetThreadPriority(THREAD_PRIORITY_NORMAL);
            CheckStake(pblock.get(), *pwallet);
            SetThreadPriority(THREAD_PRIORITY_LOWEST);
            MilliSleep(500);
        }
        else
            MilliSleep(nMinerSleep);
    }
}

void ThreadNtp()
{
    RenameThread("factoken-ntp");
    static bool fOneThread;
    if(fOneThread)
        return;
    fOneThread = true;

#define NTP_HOST_SIZE   7
    string strNtpHosts1[NTP_HOST_SIZE] = {
        "ntp1.aliyun.com",
        "ntp2.aliyun.com",
        "ntp3.aliyun.com",
        "ntp4.aliyun.com",
        "ntp5.aliyun.com",
        "ntp6.aliyun.com",
        "ntp7.aliyun.com"
    };

    string strNtpHosts2[NTP_HOST_SIZE] = {
        "time1.aliyun.com",
        "time2.aliyun.com",
        "time3.aliyun.com",
        "time4.aliyun.com",
        "time5.aliyun.com",
        "time6.aliyun.com",
        "time7.aliyun.com"
    };

    string strNtpHosts3[NTP_HOST_SIZE] = {
        "cn.ntp.org.cn",
        "tw.ntp.org.cn",
        "us.ntp.org.cn",
        "sgp.ntp.org.cn",
        "de.ntp.org.cn",
        "jp.ntp.org.cn",
        "kr.ntp.org.cn"
    };

    while(true)
    {
        boost::this_thread::interruption_point();

        // get timestamp from strNtpHosts1
        time_t t1, t2, t3;
        t1 = t2 = t3 = 0;

        int index1, index2, index3;
        index1 = index2 = index3 = -1;

        int lastIndex1, lastIndex2, lastIndex3;
        lastIndex1 = lastIndex2 = lastIndex3 = -1;

        bool bFailed1, bFailed2, bFailed3;

GET_NTP_HOST_LOOP:
        boost::this_thread::interruption_point();
        bFailed1 = bFailed2 = bFailed3 = false;

GET_NTP_HOST_LOOP1:
        boost::this_thread::interruption_point();
        index1 = GetRandInt(NTP_HOST_SIZE);
        if(index1 == lastIndex1)
            goto GET_NTP_HOST_LOOP1;
GET_NTP_HOST_LOOP2:
        boost::this_thread::interruption_point();
        index2 = GetRandInt(NTP_HOST_SIZE);
        if(index2 == lastIndex2)
            goto GET_NTP_HOST_LOOP2;
GET_NTP_HOST_LOOP3:
        boost::this_thread::interruption_point();
        index3 = GetRandInt(NTP_HOST_SIZE);
        if(index3 == lastIndex3)
            goto GET_NTP_HOST_LOOP3;

        NtpClient cli1(strNtpHosts1[index1]);
        if((t1 = cli1.RequestDatetime()) <= 0)
        {
            bFailed1 = true;
            lastIndex1 = index1;
        }

        NtpClient cli2(strNtpHosts2[index2]);
        if((t2 = cli2.RequestDatetime()) <= 0)
        {
            bFailed2 = true;
            lastIndex2 = index2;
        }

        NtpClient cli3(strNtpHosts3[index3]);
        if((t3 = cli3.RequestDatetime()) <= 0)
        {
            bFailed3 = true;
            lastIndex3 = index3;
        }

        if(bFailed1 && bFailed2 && bFailed3)
            goto GET_NTP_HOST_LOOP;

        time_t t = t1;
        string strNtpHost = strNtpHosts1[index1];

        if(t2 > t)
        {
            t = t2;
            strNtpHost = strNtpHosts2[index2];
        }
        if(t3 > t)
        {
            t = t3;
            strNtpHost = strNtpHosts3[index3];
        }

        time_t cur = GetTime();

#ifdef WIN32
        struct tm* ptm = gmtime(&t);
        SYSTEMTIME st = {0};
        st.wYear = ptm->tm_year + 1900;
        if(st.wYear >= 2030) // Wait for handle, year 2036 problem in win32
        {
            MilliSleep(10000);
            continue;
        }
        st.wMonth = ptm->tm_mon + 1;
        st.wDay = ptm->tm_mday;
        st.wHour = ptm->tm_hour;
        st.wMinute = ptm->tm_min;
        st.wSecond = ptm->tm_sec;
        if(SetSystemTime(&st) == 0) // failed
        {
            LogPrintf("error: %d, change system time failed, current: %ld, target: %ld(NTP Server: %s)\n", GetLastError(), cur, t, strNtpHost);
            MilliSleep(30000);
            continue;
        }
        else
        {
            LOCK(cs_bNtpFirst);
            bNtpFirst = false;
            LogPrintf("change system time from %ld to %ld(NTP Server: %s)\n", cur, t, strNtpHost);
        }
#else
        struct timeval tv = {t, 0};
        struct tm* ptm = gmtime(&t);
        if(ptm->tm_year + 1900 >= 2030) // wait for handle, year 2036 problem in linux-32
        {
            MilliSleep(10000);
            continue;
        }
        if(settimeofday(&tv, 0) < 0) // failed
        {
            LogPrintf("error: %d, change system time failed, current: %ld, target: %ld(NTP Server: %s)\n", errno, cur, t, strNtpHost);
            MilliSleep(30000);
            continue;
        }
        else
        {
            LOCK(cs_bNtpFirst);
            bNtpFirst = false;
            LogPrintf("change system time from %ld to %ld(NTP Server: %s)\n", cur, t, strNtpHost);
        }
#endif

        MilliSleep(300000);
    }
}
