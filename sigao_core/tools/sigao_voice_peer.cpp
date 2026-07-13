// sigao_voice_peer — secure Codec2 voice peer over the TCP relay ("tower").
//
//   reads a track WAV -> Codec2 1300 (40ms frames) -> X25519/XSalsa20-Poly1305
//   -> relay -> peer decrypts+decodes -> writes received WAV.
//
//   usage: sigao_voice_peer <alice|bob> <host> <port> <track.wav> <out_recv.wav> <key_out.hex>
//
// Runs full-duplex: sends its own track while receiving the peer's. The relay
// only ever sees ciphertext. The derived session key is written to key_out.hex
// so the interception demo can show "with key" recovery (simulated key leak).

#include "sigao_core.h"
#include "codec2.h"

#include <cstdio>
#include <cstdint>
#include <cstring>
#include <cstdlib>
#include <string>
#include <vector>
#include <thread>
#include <atomic>
#include <chrono>

#include <unistd.h>
#include <netdb.h>
#include <arpa/inet.h>
#include <sys/socket.h>

static const int N_SAMP = 320;
using clk = std::chrono::steady_clock;
static int64_t now_ns() {
    return std::chrono::duration_cast<std::chrono::nanoseconds>(clk::now().time_since_epoch()).count();
}

// ---- tiny WAV ----
static std::vector<int16_t> read_wav(const std::string& p) {
    std::vector<int16_t> out; FILE* f = fopen(p.c_str(), "rb"); if (!f) return out;
    fseek(f,0,SEEK_END); long sz=ftell(f); fseek(f,0,SEEK_SET);
    std::vector<uint8_t> b(sz); fread(b.data(),1,sz,f); fclose(f);
    if (sz<12) return out; size_t off=12;
    while (off+8<=(size_t)sz){ uint32_t cl=b[off+4]|(b[off+5]<<8)|(b[off+6]<<16)|((uint32_t)b[off+7]<<24);
        if(!memcmp(&b[off],"data",4)){ size_t n=std::min((size_t)cl,(size_t)sz-off-8); const int16_t* s=(const int16_t*)&b[off+8]; out.assign(s,s+n/2); break;} off+=8+cl+(cl&1);} 
    return out;
}
static void write_wav(const std::string& p, const std::vector<int16_t>& pcm) {
    FILE* f=fopen(p.c_str(),"wb"); if(!f) return; uint32_t db=pcm.size()*2,riff=36+db,br=8000*2; uint16_t ch=1,bps=16,fmt=1,ba=2,sr=8000;
    fwrite("RIFF",1,4,f);fwrite(&riff,4,1,f);fwrite("WAVE",1,4,f);fwrite("fmt ",1,4,f);uint32_t f16=16;fwrite(&f16,4,1,f);
    fwrite(&fmt,2,1,f);fwrite(&ch,2,1,f);uint32_t s32=8000;fwrite(&s32,4,1,f);fwrite(&br,4,1,f);fwrite(&ba,2,1,f);fwrite(&bps,2,1,f);
    fwrite("data",1,4,f);fwrite(&db,4,1,f);fwrite(pcm.data(),2,pcm.size(),f);fclose(f);
}

// ---- framed socket I/O (2-byte BE length prefix) ----
static bool wn(int fd,const uint8_t*p,size_t n){size_t o=0;while(o<n){ssize_t r=write(fd,p+o,n-o);if(r<=0)return false;o+=r;}return true;}
static bool rn(int fd,uint8_t*p,size_t n){size_t o=0;while(o<n){ssize_t r=read(fd,p+o,n-o);if(r<=0)return false;o+=r;}return true;}
static bool send_pkt(int fd,const uint8_t*d,uint16_t n){uint8_t h[2]={(uint8_t)(n>>8),(uint8_t)n};return wn(fd,h,2)&&wn(fd,d,n);}
static bool recv_pkt(int fd,std::vector<uint8_t>&o){uint8_t h[2];if(!rn(fd,h,2))return false;uint16_t n=(h[0]<<8)|h[1];o.resize(n);return n==0||rn(fd,o.data(),n);}

static int connect_relay(const char*host,int port){
    struct addrinfo hints{},*res=nullptr; hints.ai_family=AF_UNSPEC; hints.ai_socktype=SOCK_STREAM;
    char ps[16]; snprintf(ps,sizeof(ps),"%d",port);
    if(getaddrinfo(host,ps,&hints,&res)!=0)return -1; int fd=-1;
    for(auto*p=res;p;p=p->ai_next){fd=socket(p->ai_family,p->ai_socktype,p->ai_protocol);if(fd<0)continue;if(connect(fd,p->ai_addr,p->ai_addrlen)==0)break;close(fd);fd=-1;}
    freeaddrinfo(res); return fd;
}

struct RxCtx {
    int fd; const unsigned char* key; struct CODEC2* dec; int nbyte;
    std::vector<int16_t> pcm; std::vector<double> latency_ms; std::atomic<bool> done{false};
};
static void rx_loop(RxCtx* c){
    short speech[N_SAMP]; std::vector<uint8_t> pkt; unsigned char pt[64];
    while(!c->done.load()){
        if(!recv_pkt(c->fd,pkt)){break;}
        if(pkt.empty())continue;
        if(pkt[0]=='E'){c->done.store(true);break;}
        if(pkt[0]=='D'&&pkt.size()>5){
            int plen=sigao_decrypt(c->key,pkt.data()+5,(int)pkt.size()-5,pt,sizeof(pt));
            if(plen>=8+c->nbyte){
                int64_t tx=0; for(int i=0;i<8;++i)tx=(tx<<8)|pt[i];
                c->latency_ms.push_back((now_ns()-tx)/1e6);
                codec2_decode(c->dec,speech,pt+8);
                c->pcm.insert(c->pcm.end(),speech,speech+N_SAMP);
            }
        }
    }
}

static std::string hex(const unsigned char*b,int n){static const char*h="0123456789abcdef";std::string s;for(int i=0;i<n;++i){s+=h[b[i]>>4];s+=h[b[i]&15];}return s;}

int main(int argc,char**argv){
    if(argc<7){fprintf(stderr,"usage: %s <alice|bob> <host> <port> <track.wav> <out_recv.wav> <key_out.hex>\n",argv[0]);return 2;}
    std::string role=argv[1],host=argv[2],trackP=argv[4],outP=argv[5],keyP=argv[6];
    int port=atoi(argv[3]);

    auto track=read_wav(trackP);
    if(track.empty()){fprintf(stderr,"[%s] cannot read %s\n",role.c_str(),trackP.c_str());return 1;}
    printf("[%s] core %s, track %.2fs\n",role.c_str(),sigao_version(),track.size()/8000.0);

    int fd=connect_relay(host.c_str(),port);
    if(fd<0){fprintf(stderr,"[%s] connect failed\n",role.c_str());return 1;}

    unsigned char myPub[32],myPriv[32],key[32];
    sigao_gen_keypair(myPub,myPriv);
    { uint8_t h[33]; h[0]='H'; memcpy(h+1,myPub,32); send_pkt(fd,h,33); }
    std::vector<uint8_t> peer;
    if(!recv_pkt(fd,peer)||peer.size()!=33||peer[0]!='H'){fprintf(stderr,"[%s] handshake failed\n",role.c_str());return 1;}
    sigao_compute_secret(key,myPriv,peer.data()+1);
    printf("[%s] ECDH ok; key=%s\n",role.c_str(),hex(key,8).c_str());
    { FILE* kf=fopen(keyP.c_str(),"w"); if(kf){fprintf(kf,"%s\n",hex(key,32).c_str());fclose(kf);} }

    // --- Round-trip latency probe (single clock => valid across devices) ---
    // alice sends timestamped pings; bob echoes them; alice measures RTT on its
    // own clock. Serialized before streaming so the socket isn't contended.
    if(role=="alice"){
        std::vector<double> rtt;
        for(int i=0;i<30;i++){
            unsigned char ping[9]; ping[0]='P'; int64_t t=now_ns();
            for(int b=0;b<8;++b) ping[1+b]=(t>>(8*(7-b)))&0xFF;
            send_pkt(fd,ping,9);
            std::vector<uint8_t> r; if(!recv_pkt(fd,r)) break;
            if(r.size()>=9 && r[0]=='P'){ int64_t tt=0; for(int b=0;b<8;++b) tt=(tt<<8)|r[1+b];
                rtt.push_back((now_ns()-tt)/1e6); }
            std::this_thread::sleep_for(std::chrono::milliseconds(20));
        }
        unsigned char s='S'; send_pkt(fd,&s,1);
        double sum=0,mn=1e18,mx=0; for(double x:rtt){sum+=x; if(x<mn)mn=x; if(x>mx)mx=x;}
        if(!rtt.empty()) printf("[alice] network RTT mean=%.3f ms (one-way ~%.3f ms) min=%.3f max=%.3f n=%zu\n",
                                sum/rtt.size(), sum/rtt.size()/2.0, mn, mx, rtt.size());
    } else {
        while(true){ std::vector<uint8_t> r; if(!recv_pkt(fd,r)) break; if(r.empty()) continue;
            if(r[0]=='P') send_pkt(fd,r.data(),(uint16_t)r.size());
            else if(r[0]=='S') break; }
    }

    struct CODEC2* enc=codec2_create(CODEC2_MODE_1300);
    struct CODEC2* dec=codec2_create(CODEC2_MODE_1300);
    int nbyte=(codec2_bits_per_frame(enc)+7)/8;

    RxCtx rx; rx.fd=fd; rx.key=key; rx.dec=dec; rx.nbyte=nbyte;
    std::thread rxT(rx_loop,&rx);

    std::vector<int16_t> frame(N_SAMP);
    uint32_t seq=0;
    for(size_t i=0;i<track.size();i+=N_SAMP){
        for(int j=0;j<N_SAMP;++j)frame[j]=(i+j<track.size())?track[i+j]:0;
        std::vector<unsigned char> c2(nbyte); codec2_encode(enc,c2.data(),frame.data());
        std::vector<unsigned char> plain(8+nbyte);
        int64_t t=now_ns(); for(int b=0;b<8;++b)plain[b]=(t>>(8*(7-b)))&0xFF;
        memcpy(plain.data()+8,c2.data(),nbyte);
        unsigned char ct[256]; int cl=sigao_encrypt(key,plain.data(),(int)plain.size(),ct,sizeof(ct));
        std::vector<uint8_t> pkt(5+cl); pkt[0]='D'; pkt[1]=(seq>>24)&0xFF;pkt[2]=(seq>>16)&0xFF;pkt[3]=(seq>>8)&0xFF;pkt[4]=seq&0xFF;
        memcpy(pkt.data()+5,ct,cl);
        send_pkt(fd,pkt.data(),(uint16_t)pkt.size());
        seq++;
        std::this_thread::sleep_for(std::chrono::milliseconds(40));
    }
    { uint8_t e='E'; send_pkt(fd,&e,1); }
    printf("[%s] sent %u frames\n",role.c_str(),seq);

    // Wait for peer END (or timeout).
    for(int i=0;i<200 && !rx.done.load();++i) std::this_thread::sleep_for(std::chrono::milliseconds(50));
    rx.done.store(true);
    shutdown(fd,SHUT_RDWR);
    rxT.join();

    write_wav(outP,rx.pcm);
    double sum=0,mn=1e18,mx=0; for(double x:rx.latency_ms){sum+=x;if(x<mn)mn=x;if(x>mx)mx=x;}
    if(!rx.latency_ms.empty())
        printf("[%s] received %.2fs; latency mean=%.3f ms min=%.3f max=%.3f (n=%zu)\n",
               role.c_str(), rx.pcm.size()/8000.0, sum/rx.latency_ms.size(), mn, mx, rx.latency_ms.size());
    codec2_destroy(enc); codec2_destroy(dec); close(fd);
    return 0;
}
