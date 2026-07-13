// sigao_tower_intercept — what an interceptor at the tower can (and cannot) do.
//
// Reads the tower's ciphertext capture and reconstructs audio two ways:
//   1) WITHOUT the key: treat the captured ciphertext as Codec2 directly
//      (best a passive tap can do) -> garbage / noise.
//   2) WITH the key (simulated key compromise): X25519/XSalsa20-Poly1305
//      decrypt -> Codec2 decode -> the real conversation.
//
//   usage: sigao_tower_intercept <capture.bin> <key.hex|-> <out_dir>
//
// This demonstrates that end-to-end encryption defeats tower interception
// unless the session key leaks.

#include "sigao_core.h"
#include "codec2.h"

#include <cstdio>
#include <cstdint>
#include <cstring>
#include <cstdlib>
#include <string>
#include <vector>
#include <algorithm>

static const int N_SAMP = 320;

static void write_wav(const std::string& p, const std::vector<int16_t>& pcm) {
    FILE* f=fopen(p.c_str(),"wb"); if(!f) return; uint32_t db=pcm.size()*2,riff=36+db,br=8000*2; uint16_t ch=1,bps=16,fmt=1,ba=2;
    fwrite("RIFF",1,4,f);fwrite(&riff,4,1,f);fwrite("WAVE",1,4,f);fwrite("fmt ",1,4,f);uint32_t f16=16;fwrite(&f16,4,1,f);
    fwrite(&fmt,2,1,f);fwrite(&ch,2,1,f);uint32_t s32=8000;fwrite(&s32,4,1,f);fwrite(&br,4,1,f);fwrite(&ba,2,1,f);fwrite(&bps,2,1,f);
    fwrite("data",1,4,f);fwrite(&db,4,1,f);fwrite(pcm.data(),2,pcm.size(),f);fclose(f);
}

static std::vector<int16_t> mix(const std::vector<int16_t>& a, const std::vector<int16_t>& b){
    size_t n=std::max(a.size(),b.size()); std::vector<int16_t> o(n,0);
    for(size_t i=0;i<n;++i){int v=(i<a.size()?a[i]:0)+(i<b.size()?b[i]:0); o[i]=(int16_t)std::max(-32768,std::min(32767,v));}
    return o;
}

int main(int argc,char**argv){
    if(argc<4){fprintf(stderr,"usage: %s capture.bin key.hex|- out_dir\n",argv[0]);return 2;}
    std::string capP=argv[1],keyArg=argv[2],outDir=argv[3];

    FILE* f=fopen(capP.c_str(),"rb"); if(!f){fprintf(stderr,"cannot open %s\n",capP.c_str());return 1;}
    fseek(f,0,SEEK_END); long sz=ftell(f); fseek(f,0,SEEK_SET);
    std::vector<uint8_t> buf(sz); fread(buf.data(),1,sz,f); fclose(f);

    unsigned char key[32]; bool haveKey=false;
    if(keyArg!="-"){
        std::string h=keyArg;
        if(h.size()>=64){ for(int i=0;i<32;++i) key[i]=(unsigned char)strtol(h.substr(i*2,2).c_str(),nullptr,16); haveKey=true; }
    }

    struct CODEC2* decNoKey[2] = { codec2_create(CODEC2_MODE_1300), codec2_create(CODEC2_MODE_1300) };
    struct CODEC2* decWithKey[2] = { codec2_create(CODEC2_MODE_1300), codec2_create(CODEC2_MODE_1300) };
    int nbyte=(codec2_bits_per_frame(decNoKey[0])+7)/8;

    // Per-direction PCM for both attack modes.
    std::vector<int16_t> nokey[2], withkey[2];
    int dataFrames=0, helloSeen=0;

    size_t off=0;
    while(off+11<=(size_t)sz){
        uint8_t dir=buf[off];
        uint64_t ts=0; for(int i=0;i<8;++i) ts=(ts<<8)|buf[off+1+i];
        uint16_t len=(buf[off+9]<<8)|buf[off+10];
        size_t pstart=off+11;
        if(pstart+len>(size_t)sz) break;
        const uint8_t* p=&buf[pstart];
        int d = dir & 1;
        if(len>=1 && p[0]=='H') helloSeen++;
        if(len>=5 && p[0]=='D'){
            dataFrames++;
            const uint8_t* ct=p+5; int ctlen=(int)len-5;
            short speech[N_SAMP];
            // (1) WITHOUT key: naive Codec2 over raw ciphertext bytes (per-direction decoder).
            { unsigned char c2[64]; memset(c2,0,sizeof(c2)); memcpy(c2,ct,std::min(ctlen,nbyte));
              codec2_decode(decNoKey[d],speech,c2); nokey[d].insert(nokey[d].end(),speech,speech+N_SAMP); }
            // (2) WITH key: decrypt -> codec2 (per-direction decoder, preserves inter-frame state).
            if(haveKey){ unsigned char pt[64]; int pl=sigao_decrypt(key,ct,ctlen,pt,sizeof(pt));
                if(pl>=8+nbyte){ codec2_decode(decWithKey[d],speech,pt+8); withkey[d].insert(withkey[d].end(),speech,speech+N_SAMP); }
            }
        }
        off=pstart+len;
    }

    printf("Tower capture: %ld bytes, %d HELLO, %d DATA frames\n",sz,helloSeen,dataFrames);

    auto nk=mix(nokey[0],nokey[1]);
    write_wav(outDir+"/tower_without_key.wav", nk);
    printf("  wrote tower_without_key.wav (%.2fs) -- unintelligible noise\n", nk.size()/8000.0);

    if(haveKey){
        auto wk=mix(withkey[0],withkey[1]);
        write_wav(outDir+"/tower_with_key.wav", wk);
        printf("  wrote tower_with_key.wav (%.2fs) -- RECOVERED conversation (key compromised)\n", wk.size()/8000.0);
    } else {
        printf("  no key provided: tower cannot recover the conversation.\n");
    }

    codec2_destroy(decNoKey[0]); codec2_destroy(decNoKey[1]);
    codec2_destroy(decWithKey[0]); codec2_destroy(decWithKey[1]);
    return 0;
}
