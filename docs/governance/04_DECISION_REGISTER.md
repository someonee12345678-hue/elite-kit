# ELITE KIT — DECISION REGISTER

Document Path: `docs/governance/04_DECISION_REGISTER.md`
Authoritative Baseline SHA: `dbd4153ca39ede69890ebaf47feaab7f393c3c5a`
Governing Authority: `MASTER_CONTROL.md`

---

## 1. APPROVED AND PROPOSED ARCHITECTURAL DECISIONS

| Decision ID | Topic / Scope | Decision & Rationale | Status | Evidence / Approval |
| :--- | :--- | :--- | :--- | :--- |
| **DEC-001** | **Database Storage Engine** | Adopt SQLCipher for Android (`net.zetetic:android-database-sqlcipher`) to guarantee 100% page-level and `.db-wal` side-file encryption. | **PROPOSED BASELINE** | Detailed in `docs/SECURE_STORAGE_DESIGN.md`. Pending final binary footprint approval (~12MB APK footprint). |
| **DEC-002** | **Key Derivation Standards** | PBKDF2WithHmacSHA256 (100k+ iterations) for passphrases; PBKDF2WithHmacSHA512 (2048 iterations) for 24-word BIP39 seeds. | **APPROVED** | Non-circular key flow specified in architecture plan. |
| **DEC-003** | **Backup Container & Streaming** | Google Tink `AesGcmHkdfStreaming` (4KB payload segments) with 136-byte header bound via Associated Authenticated Data (AAD). | **APPROVED** | Eliminates OOM errors on large attachments; prevents unauthenticated header tampering. |
| **DEC-004** | **Restore Crash Recovery** | 7-state transactional restore state machine with 2.5x storage space pre-checks and `Application.onCreate()` startup auto-rollback. | **APPROVED** | Guarantees zero data loss or database wiping upon power loss or process kill during restore. |
| **DEC-005** | **Test Harness Infrastructure** | Adopt JUnit 4, Robolectric 4.11.1, and AndroidX Test Core for local JVM testing under Gradle 8.9. | **VERIFIED & EXECUTED** | `./gradlew test` passes 48 tasks successfully in Increment 1. |
