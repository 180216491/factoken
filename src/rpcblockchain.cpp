// Copyright (c) 2010 Satoshi Nakamoto
// Copyright (c) 2009-2012 The Bitcoin developers
// Distributed under the MIT/X11 software license, see the accompanying
// file COPYING or http://www.opensource.org/licenses/mit-license.php.

#include "rpcserver.h"
#include "main.h"
#include "kernel.h"
#include "checkpoints.h"

using namespace json_spirit;
using namespace std;

extern void TxToJSON(const CTransaction& tx, const uint256 hashBlock, json_spirit::Object& entry);

double GetDifficulty(const CBlockIndex* blockindex)
{
    // Floating point number that is a multiple of the minimum difficulty,
    // minimum difficulty = 1.0.
    if (blockindex == NULL)
    {
        if (pindexBest == NULL)
            return 1.0;
        else
            blockindex = GetLastBlockIndex(pindexBest, false);
    }

    int nShift = (blockindex->nBits >> 24) & 0xff;

    double dDiff =
        (double)0x0000ffff / (double)(blockindex->nBits & 0x00ffffff);

    while (nShift < 29)
    {
        dDiff *= 256.0;
        nShift++;
    }
    while (nShift > 29)
    {
        dDiff /= 256.0;
        nShift--;
    }

    return dDiff;
}

double GetPoWMHashPS()
{
    if (pindexBest->nHeight >= Params().LastPOWBlock())
        return 0;

    int nPoWInterval = 72;
    int64_t nTargetSpacingWorkMin = 30, nTargetSpacingWork = 30;

    CBlockIndex* pindex = pindexGenesisBlock;
    CBlockIndex* pindexPrevWork = pindexGenesisBlock;

    while (pindex)
    {
        if (pindex->IsProofOfWork())
        {
            int64_t nActualSpacingWork = pindex->GetBlockTime() - pindexPrevWork->GetBlockTime();
            nTargetSpacingWork = ((nPoWInterval - 1) * nTargetSpacingWork + nActualSpacingWork + nActualSpacingWork) / (nPoWInterval + 1);
            nTargetSpacingWork = max(nTargetSpacingWork, nTargetSpacingWorkMin);
            pindexPrevWork = pindex;
        }

        pindex = pindex->pnext;
    }

    return GetDifficulty() * 4294.967296 / nTargetSpacingWork;
}

double GetPoSKernelPS()
{
    int nPoSInterval = 72;
    double dStakeKernelsTriedAvg = 0;
    int nStakesHandled = 0, nStakesTime = 0;

    CBlockIndex* pindex = pindexBest;;
    CBlockIndex* pindexPrevStake = NULL;

    while (pindex && nStakesHandled < nPoSInterval)
    {
        if (pindex->IsProofOfStake())
        {
            if (pindexPrevStake)
            {
                dStakeKernelsTriedAvg += GetDifficulty(pindexPrevStake) * 4294967296.0;
                nStakesTime += pindexPrevStake->nTime - pindex->nTime;
                nStakesHandled++;
            }
            pindexPrevStake = pindex;
        }

        pindex = pindex->pprev;
    }

    double result = 0;

    if (nStakesTime)
        result = dStakeKernelsTriedAvg / nStakesTime;

    result *= STAKE_TIMESTAMP_MASK + 1;

    return result;
}

Object blockToJSON(const CBlock& block, const CBlockIndex* blockindex, bool fPrintTransactionDetail)
{
    Object result;
    result.push_back(Pair("hash", block.GetHash().GetHex()));
    int confirmations = -1;
    // Only report confirmations if the block is on the main chain
    if (blockindex->IsInMainChain())
        confirmations = nBestHeight - blockindex->nHeight + 1;
    result.push_back(Pair("confirmations", confirmations));
    result.push_back(Pair("size", (int)::GetSerializeSize(block, SER_NETWORK, PROTOCOL_VERSION)));
    result.push_back(Pair("height", blockindex->nHeight));
    result.push_back(Pair("version", block.nVersion));
    result.push_back(Pair("merkleroot", block.hashMerkleRoot.GetHex()));
    result.push_back(Pair("mint", ValueFromAmount(blockindex->nMint)));
    result.push_back(Pair("time", (int64_t)block.GetBlockTime()));
    result.push_back(Pair("nonce", (uint64_t)block.nNonce));
    result.push_back(Pair("bits", strprintf("%08x", block.nBits)));
    result.push_back(Pair("difficulty", GetDifficulty(blockindex)));
    result.push_back(Pair("blocktrust", leftTrim(blockindex->GetBlockTrust().GetHex(), '0')));
    result.push_back(Pair("chaintrust", leftTrim(blockindex->nChainTrust.GetHex(), '0')));
    if (blockindex->pprev)
        result.push_back(Pair("previousblockhash", blockindex->pprev->GetBlockHash().GetHex()));
    if (blockindex->pnext)
        result.push_back(Pair("nextblockhash", blockindex->pnext->GetBlockHash().GetHex()));

    result.push_back(Pair("flags", strprintf("%s%s", blockindex->IsProofOfStake()? "proof-of-stake" : "proof-of-work", blockindex->GeneratedStakeModifier()? " stake-modifier": "")));
    result.push_back(Pair("proofhash", blockindex->hashProof.GetHex()));
    result.push_back(Pair("entropybit", (int)blockindex->GetStakeEntropyBit()));
    result.push_back(Pair("modifier", strprintf("%016x", blockindex->nStakeModifier)));
    result.push_back(Pair("modifierv2", blockindex->bnStakeModifierV2.GetHex()));
    Array txinfo;
    BOOST_FOREACH (const CTransaction& tx, block.vtx)
    {
        if (fPrintTransactionDetail)
        {
            Object entry;

            entry.push_back(Pair("txid", tx.GetHash().GetHex()));
            TxToJSON(tx, 0, entry);

            txinfo.push_back(entry);
        }
        else
            txinfo.push_back(tx.GetHash().GetHex());
    }

    result.push_back(Pair("tx", txinfo));

    if (block.IsProofOfStake())
        result.push_back(Pair("signature", HexStr(block.vchBlockSig.begin(), block.vchBlockSig.end())));

    return result;
}

Value getbestblockhash(const Array& params, bool fHelp)
{
    if (fHelp || params.size() != 0)
        throw runtime_error(
            "getbestblockhash\n"
            "Returns the hash of the best block in the longest block chain.");

    return hashBestChain.GetHex();
}

Value getblockcount(const Array& params, bool fHelp)
{
    if (fHelp || params.size() != 0)
        throw runtime_error(
            "getblockcount\n"
            "Returns the number of blocks in the longest block chain.");

    return nBestHeight;
}


Value getdifficulty(const Array& params, bool fHelp)
{
    if (fHelp || params.size() != 0)
        throw runtime_error(
            "getdifficulty\n"
            "Returns the difficulty as a multiple of the minimum difficulty.");

    Object obj;
    obj.push_back(Pair("proof-of-work",        GetDifficulty()));
    obj.push_back(Pair("proof-of-stake",       GetDifficulty(GetLastBlockIndex(pindexBest, true))));
    return obj;
}


Value getrawmempool(const Array& params, bool fHelp)
{
    if (fHelp || params.size() != 0)
        throw runtime_error(
            "getrawmempool\n"
            "Returns all transaction ids in memory pool.");

    vector<uint256> vtxid;
    mempool.queryHashes(vtxid);

    Array a;
    BOOST_FOREACH(const uint256& hash, vtxid)
        a.push_back(hash.ToString());

    return a;
}

Value getblockhash(const Array& params, bool fHelp)
{
    if (fHelp || params.size() != 1)
        throw runtime_error(
            "getblockhash <index>\n"
            "Returns hash of block in best-block-chain at <index>.");

    int nHeight = params[0].get_int();
    if (nHeight < 0 || nHeight > nBestHeight)
        throw runtime_error("Block number out of range.");

    CBlockIndex* pblockindex = FindBlockByHeight(nHeight);
    return pblockindex->phashBlock->GetHex();
}

Value getblock(const Array& params, bool fHelp)
{
    if (fHelp || params.size() < 1 || params.size() > 2)
        throw runtime_error(
            "getblock <hash> [verbosity]\n"
            "\nArguments:\n"
            "1. \"blockhash\"          (string, required) The block hash\n"
            "2. verbosity              (numeric, optional, default=1) 0 for hex encoded data, 1 for a json object, and 2 for json object with transaction data\n"
            "    If verbosity is 0, returns a string that is serialized, hex-encoded data for block 'hash'.\n"
            "    If verbosity is 1, returns an Object with information about block <hash>.\n"
            "    If verbosity is 2, returns an Object with information about block <hash> and information about each transaction.\n"
            "Returns details of a block with given block-hash.");

    std::string strHash = params[0].get_str();
    uint256 hash(strHash);

    int verbosity = 1;
    if (params.size() > 1)
    {
        if(params[1].type() == int_type)
            verbosity = params[1].get_int();
        else
            verbosity = params[1].get_bool() ? 1 : 0;
    }

    if (mapBlockIndex.count(hash) == 0)
        throw JSONRPCError(RPC_INVALID_ADDRESS_OR_KEY, "Block not found");

    CBlock block;
    CBlockIndex* pblockindex = mapBlockIndex[hash];
    if(!block.ReadFromDisk(pblockindex, true))
        throw JSONRPCError(RPC_MISC_ERROR, "Can't read block from disk");

    if (verbosity <= 0)
    {
        CDataStream ssBlock(SER_NETWORK, PROTOCOL_VERSION);
        int height = pblockindex->nHeight;
        ssBlock << height;
        int confirmations = -1; 
        if(pblockindex->IsInMainChain())
            confirmations = nBestHeight - pblockindex->nHeight + 1;
        ssBlock << confirmations;
        uint256 nextHash = 0;
        if(pblockindex->pnext)
            nextHash = pblockindex->pnext->GetBlockHash();
        ssBlock << nextHash;
        ssBlock << block;
        std::string strHex = HexStr(ssBlock.begin(), ssBlock.end());
        return strHex;
    }

    return blockToJSON(block, pblockindex, verbosity >= 2);
}

Value getblockbynumber(const Array& params, bool fHelp)
{
    if (fHelp || params.size() < 1 || params.size() > 2)
        throw runtime_error(
            "getblockbynumber <number> [txinfo]\n"
            "txinfo optional to print more detailed tx info\n"
            "Returns details of a block with given block-number.");

    int nHeight = params[0].get_int();
    if (nHeight < 0 || nHeight > nBestHeight)
        throw runtime_error("Block number out of range.");

    CBlock block;
    CBlockIndex* pblockindex = mapBlockIndex[hashBestChain];
    while (pblockindex->nHeight > nHeight)
        pblockindex = pblockindex->pprev;

    uint256 hash = *pblockindex->phashBlock;

    pblockindex = mapBlockIndex[hash];
    block.ReadFromDisk(pblockindex, true);

    return blockToJSON(block, pblockindex, params.size() > 1 ? params[1].get_bool() : false);
}

// ppcoin: get information of sync-checkpoint
Value getcheckpoint(const Array& params, bool fHelp)
{
    if (fHelp || params.size() != 0)
        throw runtime_error(
            "getcheckpoint\n"
            "Show info of synchronized checkpoint.\n");

    Object result;
    const CBlockIndex* pindexCheckpoint = Checkpoints::AutoSelectSyncCheckpoint();

    result.push_back(Pair("synccheckpoint", pindexCheckpoint->GetBlockHash().ToString().c_str()));
    result.push_back(Pair("height", pindexCheckpoint->nHeight));
    result.push_back(Pair("timestamp", DateTimeStrFormat(pindexCheckpoint->GetBlockTime()).c_str()));

    result.push_back(Pair("policy", "rolling"));

    return result;
}

Value getblockchaininfo(const Array& params, bool fHelp)
{
    if (fHelp || params.size() != 0)
        throw runtime_error(
            "getblockchaininfo\n"
            "Returns an object containing various state info regarding block chain processing.\n"
            "\nResult:\n"
            "{\n"
            "  \"chain\": \"xxxx\",        (string) current network name as defined in BIP70 (main, test, regtest)\n"
            "  \"blocks\": xxxxxx,         (numeric) the current number of blocks processed in the server\n"
            "  \"headers\": xxxxxx,        (numeric) the current number of headers we have validated\n"
            "  \"bestblockhash\": \"...\", (string) the hash of the currently best block\n"
            "  \"difficulty\": xxxxxx,     (numeric) the current difficulty\n"
            "}\n"
        );

    Object obj, diff;
    string strNetwork = "unknown";
    if(Params().NetworkID() == CChainParams::MAIN)
        strNetwork = "main";
    else if(Params().NetworkID() == CChainParams::TESTNET)
        strNetwork = "test";
    else if(Params().NetworkID() == CChainParams::REGTEST)
        strNetwork = "regtest";
    obj.push_back(Pair("chain",                 strNetwork));
    obj.push_back(Pair("blocks",                (int)nBestHeight));
    obj.push_back(Pair("headers",               pindexBest ? pindexBest->nHeight : -1));
    obj.push_back(Pair("bestblockhash",         pindexBest->GetBlockHash().GetHex()));
    diff.push_back(Pair("proof-of-work",        GetDifficulty()));
    diff.push_back(Pair("proof-of-stake",       GetDifficulty(GetLastBlockIndex(pindexBest, true))));
    obj.push_back(Pair("difficulty",            diff));
    obj.push_back(Pair("verificationprogress",  Checkpoints::GetVerificationProcess()));

    return obj;
}

Value getblockhashes(const Array& params, bool fHelp)
{
    if (fHelp || params.size() != 2)
        throw runtime_error(
            "getblockhashes timestamp\n"
            "\nReturns array of hashes of blocks within the timestamp range provided.\n"
            "\nArguments:\n"
            "1. high         (numeric, required) The newer block timestamp\n"
            "2. low          (numeric, required) The older block timestamp\n"
            "{\n"
            "  Result:\n"
            "  [\n"
            "    {\n"
            "      \"hash\": hash         (string) The block hash\n"
            "      \"height\": height     (number) The block height\n"
            "      \"time \": time        (number) The block time\n"
            "      \"txLength\": length   (number) The number of transation in block\n"
            "      \"size\": size         (number) The size of block\n"
            "    },\n"
            "    ...\n"
            "  ]\n"
            "}\n"
        );

    int64_t high = params[0].get_int64();
    int64_t low = params[1].get_int64();
    if(high < 0 || low < 0 || low > high)
        throw JSONRPCError(RPC_INVALID_PARAMETER, "Invalid parameter");

    int nMaxHeight = nBestHeight;
    CBlockIndex* pBlockIndex = mapBlockIndex[hashBestChain];
    while(pBlockIndex->nHeight > nMaxHeight)
        pBlockIndex = pBlockIndex->pprev;

    Array resultInfo;
    for(int i = nMaxHeight; i >= 0; i--)
    {
        if(pBlockIndex->nTime < low)
            break;
        if(pBlockIndex->nTime > high)
        {
            pBlockIndex = pBlockIndex->pprev;
            continue;
        }
        
        CBlock block;
        if(!block.ReadFromDisk(pBlockIndex, true))
            throw JSONRPCError(RPC_MISC_ERROR, "Can't read block from disk");

        Object entry;
        entry.push_back(Pair("hash", block.GetHash().GetHex()));
        entry.push_back(Pair("height", pBlockIndex->nHeight));
        entry.push_back(Pair("time", (int64_t)block.GetBlockTime()));
        entry.push_back(Pair("txLength", (int)block.vtx.size()));
        entry.push_back(Pair("size", (int)::GetSerializeSize(block, SER_NETWORK, PROTOCOL_VERSION)));
        resultInfo.push_back(entry);

        pBlockIndex = pBlockIndex->pprev;
    }
    
    Object result;
    result.push_back(Pair("result", resultInfo));

    return result;

    /*
    if (fHelp || params.size() != 2)
        throw runtime_error(
            "getblockhashes timestamp\n"
            "\nReturns array of hashes of blocks within the timestamp range provided.\n"
            "\nArguments:\n"
            "1. high         (numeric, required) The newer block timestamp\n"
            "2. low          (numeric, required) The older block timestamp\n"
            "{\n"
            "  Result:\n"
            "  [\n"
            "    tx,"
            "    ...\n"
            "  ]\n"
            "}\n"
        );

    int64_t high = params[0].get_int64();
    int64_t low = params[1].get_int64();
    if(high < 0 || low < 0 || low > high)
        throw JSONRPCError(RPC_INVALID_PARAMETER, "Invalid parameter");

    int nMaxHeight = nBestHeight;
    CBlockIndex* pBlockIndex = mapBlockIndex[hashBestChain];
    while(pBlockIndex->nHeight > nMaxHeight)
        pBlockIndex = pBlockIndex->pprev;

    Array resultInfo;
    for(int i = nMaxHeight; i >= 0; i--)
    {
        if(pBlockIndex->nTime < low)
            break;
        if(pBlockIndex->nTime > high)
        {
            pBlockIndex = pBlockIndex->pprev;
            continue;
        }

        CBlock block;
        if(!block.ReadFromDisk(pBlockIndex, true))
            throw JSONRPCError(RPC_MISC_ERROR, "Can't read block from disk");
        resultInfo.push_back(block.GetHash().GetHex());
        pBlockIndex = pBlockIndex->pprev;
    }

    Object result;
    result.push_back(Pair("result", resultInfo));

    return result;
    */
}

Value getallblockhash(const Array& params, bool fHelp)
{
    if (fHelp || params.size() != 2)
        throw runtime_error("getallblockhash start_height end_height\n"
            "start_height: print block information start from this height\n"
            "end_height: print block information end at this height\n"
            "such as: getallblockhash 20 100\n"
            "such as: getallblockhash 0 20");

    int nStart = params[0].get_int();
    int nEnd = params[1].get_int();
    if(nStart < 0 || nEnd > nBestHeight)
        throw runtime_error("Block number out of range.");
    
    CBlockIndex* pBlockIndex = mapBlockIndex[hashBestChain];
    while(pBlockIndex->nHeight > nEnd)
        pBlockIndex = pBlockIndex->pprev;

    vector<uint256> vHash;
    for(int i = nEnd; i >= nStart; i--)
    {
        CBlock block;
        if(!block.ReadFromDisk(pBlockIndex, true))
            throw JSONRPCError(RPC_MISC_ERROR, "Can't read block from disk");
        vHash.push_back(block.GetHash());
        pBlockIndex = pBlockIndex->pprev;
    }

    Array resultInfo;
    int i = nStart;
    FILE* pFile = fopen("hash.txt", "w+");
    for(vector<uint256>::reverse_iterator it = vHash.rbegin(); it != vHash.rend(); it++)
    {
        string strKey = "";
        stringstream ss;
        ss << i++;
        ss >> strKey;
        uint256 hash = *it;
        string strContext = strKey + ": " + hash.GetHex() + "\n";
        if(pFile)
            fwrite(strContext.data(), strContext.length(), 1, pFile);
        resultInfo.push_back(strContext);
    }
    if(pFile)
        fclose(pFile);

    return resultInfo;
}
