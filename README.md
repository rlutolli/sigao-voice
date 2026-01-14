# Sigao Voice

**Data-over-Voice (DoV) Secure Scrambler**

A cross-platform mobile application designed to establish End-to-End Encrypted (E2EE) communication over standard cellular voice channels.

## Architecture
-   **Modem**: FBMC/OQAM (Filter Bank Multicarrier / Offset QAM) designed to mimicking human speech harmonics.
-   **Security**: AES-256-GCM + ECDH Key Exchange.
-   **Platform**: Flutter UI + C++ Core (FFI).

## Directory Structure
-   `sigao_voice/`: Flutter Mobile App.
-   `sigao_core/`: C++ Core Library (Modem & Crypto).
-   `simulation/`: Python verification scripts (`pysigao_sim.py`).

## Getting Started
See [walkthrough.md](../brain/...,/walkthrough.md) for build instructions.

## Disclaimer
This software is for **Educational Research Only**. 
See `DISCLAIMER.md`.
