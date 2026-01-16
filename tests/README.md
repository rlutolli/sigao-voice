# DSP Verification Instructions

To verify the Phase 6 DSP Hardening (LDPC + Jitter Buffer), you need to run the python simulator `hostile_channel_sim.py`.
Since `sigao_core` is a C++ library, you must compile it for your host machine (Linux) first.

## 1. Build SigaoCore for Linux
```bash
cd sigao_core
mkdir -p build
cd build
cmake ..
make
```
*Note: Ensure `codec2` and `openssl` dev libraries are installed (`sudo apt install libcodec2-dev libssl-dev` or similar, or use the included sources).*

## 2. Run the Simulator
```bash
cd ../../tests
python3 hostile_channel_sim.py
```

## Expected Output
The simulator will:
1.  Create a Modem with DSP Hardening enabled.
2.  Generate 50 frames of voice.
3.  Simulate 20% Packet Loss and 50ms Jitter.
4.  Inject packets into the `SigaoDSP` Jitter Buffer.
5.  Report "Playback Recovered" or "Buffering" states.

Success is defined by the Jitter Buffer successfully re-ordering the shuffled packets and outputting audio frames despite the loss/delay.
