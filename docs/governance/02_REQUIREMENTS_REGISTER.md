# ELITE KIT — REQUIREMENTS REGISTER

Document Path: `docs/governance/02_REQUIREMENTS_REGISTER.md`
Authoritative Baseline SHA: `dbd4153ca39ede69890ebaf47feaab7f393c3c5a`
Governing Authority: `MASTER_CONTROL.md`

---

## 1. CAPTURED GOVERNANCE & TECHNICAL REQUIREMENTS

| Req ID | Source Section | Requirement Description | Priority | Status | Acceptance Criteria |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **REQ-001** | MC Section 3.1 | No real confidential legal records in prototype/test builds. | P0 | **ENFORCED** | 100% synthetic test data used across all test suites. |
| **REQ-002** | MC Section 3.2 | No security/production claims without automated test evidence. | P0 | **ENFORCED** | Security docs labeled `PROPOSED — NOT IMPLEMENTED`. |
| **REQ-003** | MC Section 3.5 | Preserve last-known-good release and provide tested rollback. | P0 | **VERIFIED** | `main` baseline `dbd4153ca39ede69890ebaf47feaab7f393c3c5a` preserved untouched. |
| **REQ-004** | MC Section 3.7 | Human approval required for publishing or destructive actions. | P0 | **ENFORCED** | Destructive restore operations require explicit user UI approval. |
| **REQ-005** | MC Section 3.8 | Keep credentials and keys out of source control and logs. | P0 | **ENFORCED** | Keys derived in ephemeral RAM; zero hardcoded secrets. |
| **REQ-006** | MC Section 1 | Phone-first Android native legal workspace ($0 budget). | P1 | **IMPLEMENTED** | Native Java 17, minSdk 26 app building with Gradle 8.9. |
| **REQ-007** | MC Section 2 | Page-level database encryption and evidence file AEAD encryption. | P1 | **MISSING** | SQLCipher database storage and AES-GCM evidence encryption. |
| **REQ-008** | MC Section 7 | Non-circular key management & zero-KeyStore 24-word seed recovery. | P1 | **MISSING** | Replacement phone recovers `DEK` via 24-word BIP39 seed + PBKDF2. |
| **REQ-009** | MC Section 7 | Offline `.eltkbackup` export with Tink streaming AEAD and AAD header binding. | P1 | **MISSING** | Tink `AesGcmHkdfStreaming` container export/import verified. |
| **REQ-010** | MC Section 7 | Transactional 7-state restore state machine with startup auto-rollback. | P1 | **MISSING** | Simulated crash during `STATE_ACTIVATING` auto-restores pre-restore snapshot. |
