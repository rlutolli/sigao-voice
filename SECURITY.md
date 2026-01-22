# Security Policy

## Supported Versions

As this is a research Proof-of-Concept, only the latest commit on the `main` branch is supported.

| Version | Supported |
| ------- | ------------------ |
| Main Branch | :white_check_mark: |
| Old Commits | :x: |

## Reporting a Vulnerability

We take the security of this research seriously. If you discover a vulnerability, especially one that could compromise the "Uninterceptable" or "Resilient" properties discussed in our research, please follow these steps:

1.  **Do NOT open a public GitHub Issue.** Public disclosure of security vulnerabilities can put users at risk and violates responsible disclosure practices.
2.  **Report Privately on GitHub:** Go to the [Security tab](https://github.com/rlutolli/sigao-voice/security/advisories/new) of this repository and click on **"Report a vulnerability"** to open a private advisory. This ensures the details remain confidential between you and the maintainers.
3.  **Include Details:** Please include a description of the vulnerability, steps to reproduce it, and any proof-of-concept code.
4.  **Response:** We will acknowledge receipt of your report within 48 hours and provide an estimated timeline for analysis.

## Scope of Security

This project is an **academic prototype**. While we strive for security, the following are **OUT OF SCOPE** for security reports unless they demonstrate a fundamental flaw in the cryptographic design:

*   Physical device seizure (protection against this is best-effort).
*   OS-level exploits (e.g., rooted Android devices, malware with root access).
*   Denial of Service (jamming the acoustic channel).

## Disclaimer

This policy acts as an invitation for valid security research. We will not pursue legal action against researchers who discover and report security vulnerabilities in good faith and in accordance with this policy.
