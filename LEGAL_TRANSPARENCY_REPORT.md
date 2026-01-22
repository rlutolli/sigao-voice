# Sigao Voice: Legal Transparency Report & Global Regulatory Analysis

**Date:** January 2026
**Status:** Public
**Classification:** Legal Research / User Advisory

---

## 1. Executive Summary: The Digital Curtain and Jurisdictional Fragmentation

As of 2026, the global legal landscape for secure communications is characterized by a "Digital Curtain." While End-to-End Encryption (E2EE) remains legal in most democratic jurisdictions, the supporting infrastructure and implementation mechanisms face increasing regulatory scrutiny.

This report identifies three distinct geopolitical blocs affecting Sigao Voice users:

1.  **The "Lawful Access" Bloc (EU, Five Eyes):** Developing mandates for Client-Side Scanning (CSS) and Technical Capability Notices. The regulatory focus is on the provider's refusal to build "backdoors" or access mechanisms.
2.  **The "State Control" Bloc (China, Russia, Iran):** Explicitly criminalizing unauthorized encryption. Statutes such as Russia's *Yarovaya Law* and China's *Cryptography Law* mandate state access to cryptographic keys.
3.  **The "Grey Zone" (Balkans):** A high-risk environment where outdated legislation conflicts with aggressive enforcement against "SIM Box" fraud. While E2EE is legal, the transmission methods (vocoder bypass, data-over-voice) are frequently prosecuted under fraud and network integrity statutes.

---

## 2. Technical-Legal Matrix

Sigao Voice operates at the intersection of three technologies, each carrying unique legal liabilities.

| Technology | Implementation | Legal Status (Global Consensus) | Primary Legal Risk |
| :--- | :--- | :--- | :--- |
| **End-to-End Encryption (E2EE)** | `libsodium` / `StrongBox` | **Legal** (95% of nations) | **Compelled Access:** Jurisdiction-specific laws (e.g., UK *RIPA Part III*, Australia *TOLA Act*) can force users to utilize biometric or password credentials to surrender keys or face imprisonment. |
| **Data-Over-Voice (DoV)** | Acoustic Modulation (PSK/QAM) | **Grey Area / Contractual Breach** | **Theft of Service:** Carrier Terms of Service (ToS) strictly prohibit utilizing voice channels for non-dialogue data to bypass billing or data metering. |
| **Vocoder Bypass** | Forcing "Transparent Mode" | **High Risk** | **Interconnect Fraud:** Often legally conflated with "SIM Boxing." Manipulating network signaling to bypass compression is a primary indicator for telecom fraud detection systems. |

---

## 3. Regional Risk Analysis

### 3.1 The Balkans (Serbia, Albania, Kosovo, Montenegro)
*   **Risk Level:** HIGH
*   **Primary Threat:** Network Integrity & Fraud Prosecution.
*   **Analysis:** The region acts as a global hub for "SIM Boxing" (interconnect bypass fraud). Law enforcement, often in coordination with Europol, aggressively targets devices attempting to bypass vocoders or exhibiting machine-like calling patterns.
*   **Warning:** Utilization of Sigao Voice to bypass billing or termination fees may be prosecuted as criminal fraud rather than treated as a civil contract violation. In Serbia, "Smart Surveillance" systems render physical anonymity difficult.

### 3.2 European Union (EU-27)
*   **Risk Level:** MEDIUM / EVOLVING
*   **Primary Threat:** "Chat Control" (CSAM Regulation).
*   **Analysis:** The proposed CSAM regulation seeks to mandate Client-Side Scanning for messaging applications. While E2EE remains protected, potential "detection orders" could functionally prohibit applications that refuse to scan content prior to encryption.
*   **Precedent:** The *EncroChat* and *Sky ECC* investigations established that evidence obtained through state-sponsored hacking of servers is admissible in court across the EU.

### 3.3 The Americas (USA, Canada, Brazil)
*   **Risk Level:** LOW to MEDIUM
*   **Primary Threat:** Carrier Contract Termination.
*   **USA:** Protected by First Amendment precedents regarding code as speech. *CALEA* exempts "information services" from requirements to build wiretap backdoors. However, major carriers (AT&T, Verizon) strictly prohibit Data-Over-Voice in standard consumer contracts.
*   **Brazil:** Judicial blocking of messaging services sets a precedent for banning platforms that cannot comply with decryption orders.
*   **Canada:** Bill C-26 may introduce new lawful access powers comparable to Australia's *TOLA Act*.

---

## 4. Criminal Precedents & Liability

Recent case law demonstrates a shift from prosecuting individual criminal actors to prosecuting the architects of privacy infrastructure.

*   **Phantom Secure (USA, 2018):**
    *   *Precedent:* Marketing a device specifically for "criminal evasion" (e.g., advertising it as "uncrackable" or removing hardware features like GPS/Microphones) is evidence of conspiracy. Sigao Voice is a general-purpose research tool; it is not designed or intended for criminal concealment.
*   **EncroChat / Sky ECC (Europe, 2020-2021):**
    *   *Precedent:* Private servers provide no immunity. State agencies have demonstrated the capability to deploy malware implants to harvest data at rest. Sigao Voice's offline-first, peer-to-peer architecture mitigates server-side risks, but device seizure remains a viable threat vector.

---

## 5. Conclusion

Sigao Voice is designed as a **research tool** for exploring resilient communication protocols.

1.  **Strict Prohibition on Fraud:** Users must not utilize this software to bypass billing metering or commit telecommunications fraud.
2.  **Jurisdictional Compliance:** Users must not rely on this tool in jurisdictions where encryption is explicitly banned or where key disclosure is mandatory (e.g., China, Russia, Iran).
3.  **No Immunity:** "Uninterceptable" describes a mathematical property of encryption, not a legal shield. Users should be aware that refusal to comply with court orders to unlock a device may constitute a separate criminal offense in jurisdictions such as the UK and Australia.
