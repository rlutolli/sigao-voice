#!/usr/bin/env python3
"""
Generate a scripted ping-pong dialogue as two COMPLEMENTARY 8 kHz mono tracks.

Each speaker's track is full-length, with their speech only during their own
turns and silence during the other's. Mixing the two tracks reproduces the
full alternating conversation with no overlap. This lets two devices stream
simultaneously over the secure link and have the received audio reconstruct a
clean back-and-forth call.

Outputs (into the given dir):
  alice_track.wav, bob_track.wav, full_dialogue.wav
"""

import os
import sys
import struct
import subprocess
import tempfile
import wave

# (speaker, voice, text) — alternating turns.
TURNS = [
    ("alice", "Samantha", "Bob, can you hear me on the secure line?"),
    ("bob",   "Daniel",   "Loud and clear, Alice. Is this call really encrypted?"),
    ("alice", "Samantha", "Yes, end to end. The tower only sees noise."),
    ("bob",   "Daniel",   "Perfect. Let's confirm our keys match."),
    ("alice", "Samantha", "Keys verified. Nobody can tap this call."),
    ("bob",   "Daniel",   "Excellent. Sigao secure voice works."),
]

SR = 8000


def synth(voice, text, tmp):
    aiff = os.path.join(tmp, "u.aiff")
    wav = os.path.join(tmp, "u.wav")
    subprocess.run(["say", "-v", voice, "-o", aiff, text], check=True)
    subprocess.run(["afconvert", "-f", "WAVE", "-d", "LEI16@8000", "-c", "1", aiff, wav], check=True)
    w = wave.open(wav, "rb")
    data = w.readframes(w.getnframes())
    w.close()
    return list(struct.unpack("<%dh" % (len(data) // 2), data))


def write_wav(path, samples):
    w = wave.open(path, "wb")
    w.setnchannels(1)
    w.setsampwidth(2)
    w.setframerate(SR)
    w.writeframes(struct.pack("<%dh" % len(samples), *samples))
    w.close()


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else "demo_output"
    os.makedirs(out, exist_ok=True)
    tmp = tempfile.mkdtemp()

    # Synthesize each turn and remember its length.
    utterances = []
    for speaker, voice, text in TURNS:
        s = synth(voice, text, tmp)
        # small 250 ms trailing pause so turns breathe
        s += [0] * (SR // 4)
        utterances.append((speaker, s))
        print(f"  {speaker:5s}: {len(s)/SR:5.2f}s  \"{text}\"")

    alice, bob = [], []
    for speaker, s in utterances:
        if speaker == "alice":
            alice += s
            bob += [0] * len(s)
        else:
            bob += s
            alice += [0] * len(s)

    full = [max(-32768, min(32767, a + b)) for a, b in zip(alice, bob)]

    write_wav(os.path.join(out, "alice_track.wav"), alice)
    write_wav(os.path.join(out, "bob_track.wav"), bob)
    write_wav(os.path.join(out, "full_dialogue.wav"), full)
    print(f"Total dialogue: {len(full)/SR:.2f}s  -> {out}/alice_track.wav, bob_track.wav, full_dialogue.wav")


if __name__ == "__main__":
    main()
