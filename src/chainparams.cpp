// Copyright (c) 2010 Satoshi Nakamoto
// Copyright (c) 2009-2012 The Bitcoin developers
// Distributed under the MIT/X11 software license, see the accompanying
// file COPYING or http://www.opensource.org/licenses/mit-license.php.

#include "assert.h"

#include "chainparams.h"
#include "main.h"
#include "util.h"

#include <boost/assign/list_of.hpp>

using namespace boost::assign;

struct SeedSpec6 {
    uint8_t addr[16];
    uint16_t port;
};

#include "chainparamsseeds.h"
/*
#include <stdio.h>
#include "hash.h"
#include "scrypt.h"
bool static ScanHash(CBlock* pblock, uint256& thash)
{
    uint256 hashTarget = CBigNum().SetCompact(pblock->nBits).getuint256();
    while(true)
    {
        pblock->nNonce +=1;
        thash = scrypt_blockhash(CVOIDBEGIN(pblock->nVersion));
        if(thash <= hashTarget)
            break;
    }
    return true;
}
        uint256 hash;
        if(ScanHash(&genesis, hash))
        {
            printf("nonce: %lld\n", genesis.nNonce);
            printf("hash: %s\n", hash.ToString().data());
            printf("genesis.hash: %s\n", genesis.GetHash().ToString().data());
            printf("genesis.merkle: %s\n", genesis.hashMerkleRoot.ToString().data());
        }
*/
//
// Main network
//

// Convert the pnSeeds6 array into usable address objects.
static void convertSeed6(std::vector<CAddress> &vSeedsOut, const SeedSpec6 *data, unsigned int count)
{
    // It'll only connect to one or two seed nodes because once it connects,
    // it'll get a pile of addresses with newer timestamps.
    // Seed nodes are given a random 'last seen time' of between one and two
    // weeks ago.
    const int64_t nOneWeek = 7*24*60*60;
    for (unsigned int i = 0; i < count; i++)
    {
        struct in6_addr ip;
        memcpy(&ip, data[i].addr, sizeof(ip));
        CAddress addr(CService(ip, data[i].port));
        addr.nTime = GetTime() - GetRand(nOneWeek) - nOneWeek;
        vSeedsOut.push_back(addr);
    }
}

class CMainParams : public CChainParams {
public:
    CMainParams() {
        // The message start string is designed to be unlikely to occur in normal data.
        // The characters are rarely used upper ASCII, not valid as UTF-8, and produce
        // a large 4-byte int at any alignment.
        pchMessageStart[0] = 0x10;
        pchMessageStart[1] = 0x11;
        pchMessageStart[2] = 0x12;
        pchMessageStart[3] = 0x13;
        vAlertPubKey = ParseHex("04c1d77b732ec0f4d58d4d5ec93653ab67ba99fdf079c46b1561a7ba1dffa8e1e21c3260009f4cfa6b00fe8d584d214c5cfff74e3aa2ef1a933b5d672c1b128d6e");
        nDefaultPort = 13001;
        nRPCPort = 13002;
        bnProofOfWorkLimit = CBigNum(~uint256(0) >> 12);

        // Build the genesis block. Note that the output of the genesis coinbase cannot
        // be spent as it did not originally exist in the database.
        const char* pszTimestamp = "fashion chain";
        std::vector<CTxIn> vin;
        vin.resize(1);
        vin[0].scriptSig = CScript() << 0 << CBigNum(42) << vector<unsigned char>((const unsigned char*)pszTimestamp, (const unsigned char*)pszTimestamp + strlen(pszTimestamp));
        std::vector<CTxOut> vout;
        vout.resize(1);
        vout[0].SetEmpty();
        CTransaction txNew(1, 1531735402, vin, vout, 0);
        genesis.vtx.push_back(txNew);
        genesis.hashPrevBlock = 0;
        genesis.hashMerkleRoot = genesis.BuildMerkleTree();
        genesis.nVersion = 1;
        genesis.nTime    = 1531735402;
        genesis.nBits    = bnProofOfWorkLimit.GetCompact();
        genesis.nNonce   = 6288;
        hashGenesisBlock = genesis.GetHash();				
			
 printf("---------1-------------------------------------------\ngenesis.hash: %s\ngenesis.hasMerkleRoot:%s\n----------------------------------------------------\n",hashGenesisBlock.ToString().data(),genesis.hashMerkleRoot.ToString().data());
				assert(hashGenesisBlock == uint256("0xe9b2d91eeaf53da0ffd0c1e662b56794f2b514deca4909456e056eb4ead8da83"));
        assert(genesis.hashMerkleRoot == uint256("0x4f91987f625d770dd9539a4794fdf020550f4ed8c79e2d79454b9dff52bd07a4"));

        vSeeds.push_back(CDNSSeedData("125.62.69.98", "125.62.69.98"));
        vSeeds.push_back(CDNSSeedData("125.62.69.122", "125.62.69.122"));
        vSeeds.push_back(CDNSSeedData("125.62.69.66", "125.62.69.66"));
        vSeeds.push_back(CDNSSeedData("125.62.69.82", "125.62.69.82"));
        vSeeds.push_back(CDNSSeedData("125.62.69.74", "125.62.69.74"));
        vSeeds.push_back(CDNSSeedData("125.62.69.42", "125.62.69.42"));
        vSeeds.push_back(CDNSSeedData("125.62.69.58", "125.62.69.58"));
        vSeeds.push_back(CDNSSeedData("125.62.69.50", "125.62.69.50"));
        vSeeds.push_back(CDNSSeedData("125.62.69.114", "125.62.69.114"));
         

        base58Prefixes[PUBKEY_ADDRESS] = list_of(33).convert_to_container<std::vector<unsigned char> >();
        base58Prefixes[SCRIPT_ADDRESS] = list_of(93).convert_to_container<std::vector<unsigned char> >();
        base58Prefixes[SECRET_KEY] =     list_of(251).convert_to_container<std::vector<unsigned char> >();
        base58Prefixes[EXT_PUBLIC_KEY] = list_of(0x04)(0x88)(0xB2)(0x1E).convert_to_container<std::vector<unsigned char> >();
        base58Prefixes[EXT_SECRET_KEY] = list_of(0x04)(0x88)(0xAD)(0xE4).convert_to_container<std::vector<unsigned char> >();

        convertSeed6(vFixedSeeds, pnSeed6_main, ARRAYLEN(pnSeed6_main));

        nLastPOWBlock = 3000;
    }

    virtual const CBlock& GenesisBlock() const { return genesis; }
    virtual Network NetworkID() const { return CChainParams::MAIN; }

    virtual const vector<CAddress>& FixedSeeds() const {
        return vFixedSeeds;
    }
protected:
    CBlock genesis;
    vector<CAddress> vFixedSeeds;
};
static CMainParams mainParams;


//
// Testnet
//

class CTestNetParams : public CMainParams {
public:
    CTestNetParams() {
        // The message start string is designed to be unlikely to occur in normal data.
        // The characters are rarely used upper ASCII, not valid as UTF-8, and produce
        // a large 4-byte int at any alignment.
        pchMessageStart[0] = 0xc0;
        pchMessageStart[1] = 0xc1;
        pchMessageStart[2] = 0xc2;
        pchMessageStart[3] = 0xc3;
        bnProofOfWorkLimit = CBigNum(~uint256(0) >> 10);
        vAlertPubKey = ParseHex("04de7a7b5a1232e98065ee47cca9350700b69a35ed9386a1e29174f951200985962747d9209b521e1049021ae917f55294ce6f7b44abe2d21726b5db7e8b90a041");
        nDefaultPort = 23001;
        nRPCPort = 23002;
        strDataDir = "testnet";

        // Modify the testnet genesis block so the timestamp is valid for a later start.
        genesis.nBits  = bnProofOfWorkLimit.GetCompact();
        genesis.nNonce = 411;
        hashGenesisBlock = genesis.GetHash();

 printf("----------2------------------------------------------\nTest Net genesis.hash: %s\n----------------------------------------------------\n",hashGenesisBlock.ToString().data());
	    assert(hashGenesisBlock == uint256("0xfa03a30abe402a7928dd091a002e607c9c55f4fe093fdb791c226d6e7eb04528"));

        vFixedSeeds.clear();
        vSeeds.clear();

        base58Prefixes[PUBKEY_ADDRESS] = list_of(33).convert_to_container<std::vector<unsigned char> >();
        base58Prefixes[SCRIPT_ADDRESS] = list_of(93).convert_to_container<std::vector<unsigned char> >();
        base58Prefixes[SECRET_KEY]     = list_of(251).convert_to_container<std::vector<unsigned char> >();
        base58Prefixes[EXT_PUBLIC_KEY] = list_of(0x04)(0x88)(0xB2)(0x1E).convert_to_container<std::vector<unsigned char> >();
        base58Prefixes[EXT_SECRET_KEY] = list_of(0x04)(0x88)(0xAD)(0xE4).convert_to_container<std::vector<unsigned char> >();

        convertSeed6(vFixedSeeds, pnSeed6_test, ARRAYLEN(pnSeed6_test));

        nLastPOWBlock = 0xffffffff;
    }
    virtual Network NetworkID() const { return CChainParams::TESTNET; }
};
static CTestNetParams testNetParams;


//
// Regression test
//
class CRegTestParams : public CTestNetParams {
public:
    CRegTestParams() {
        pchMessageStart[0] = 0x40;
        pchMessageStart[1] = 0x41;
        pchMessageStart[2] = 0x42;
        pchMessageStart[3] = 0x44;
        bnProofOfWorkLimit = CBigNum(~uint256(0) >> 1);
        genesis.nBits  = bnProofOfWorkLimit.GetCompact();
        genesis.nNonce = 1;
        hashGenesisBlock = genesis.GetHash();
        nDefaultPort = 33001;
        nRPCPort = 33002;
        strDataDir = "regtest";
        
 printf("----------3------------------------------------------\nREG TEST Net genesis.hash: %s\n----------------------------------------------------\n",hashGenesisBlock.ToString().data());
		assert(hashGenesisBlock == uint256("0x3bffde0d25be27de378b6092e269d2667e2e6b96525587f1e3bae0685fff9610"));

        vSeeds.clear();  // Regtest mode doesn't have any DNS seeds.
    }

    virtual bool RequireRPCPassword() const { return false; }
    virtual Network NetworkID() const { return CChainParams::REGTEST; }
};
static CRegTestParams regTestParams;

static CChainParams *pCurrentParams = &mainParams;

const CChainParams &Params() {
    return *pCurrentParams;
}

void SelectParams(CChainParams::Network network) {
    switch (network) {
        case CChainParams::MAIN:
            pCurrentParams = &mainParams;
            break;
        case CChainParams::TESTNET:
            pCurrentParams = &testNetParams;
            break;
        case CChainParams::REGTEST:
            pCurrentParams = &regTestParams;
            break;
        default:
            assert(false && "Unimplemented network");
            return;
    }
}

bool SelectParamsFromCommandLine() {
    bool fRegTest = GetBoolArg("-regtest", false);
    bool fTestNet = GetBoolArg("-testnet", false);

    if (fTestNet && fRegTest) {
        return false;
    }

    if (fRegTest) {
        SelectParams(CChainParams::REGTEST);
    } else if (fTestNet) {
        SelectParams(CChainParams::TESTNET);
    } else {
        SelectParams(CChainParams::MAIN);
    }
    return true;
}
