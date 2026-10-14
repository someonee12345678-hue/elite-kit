# ELITE KIT — ARCHITECTURE AND BUILD PLAN

Document Path: `docs/governance/03_ARCHITECTURE_AND_BUILD_PLAN.md`
Authoritative Baseline SHA: `dbd4153ca39ede69890ebaf47feaab7f393c3c5a`
Governing Authority: `MASTER_CONTROL.md`

---

## 1. SMALLEST VIABLE ARCHITECTURE

### 1.1 Local Storage Layer (SQLCipher for Android)
To eliminate plaintext leakage through Write-Ahead Logging (`.db-wal`) and Rollback Journal (`.db-journal`) side files, Elite Kit adopts **SQLCipher for Android** (`net.zetetic:android-database-sqlcipher`). All database pages, indexes, WAL frames, and journals are encrypted at rest via page-level AES-256-CBC + HMAC-SHA512.

### 1.2 Non-Circular Key Management & Replacement-Phone Recovery
```
[ Primary Unlock Flow (Device A) ]
Master Passphrase ──> PBKDF2WithHmacSHA256 ──> KEK_passphrase ──┐
                                                                 ├──> unwraps ──> DEK (256-bit AES)
Android KeyStore TEE ────────────────────────> KEK_keystore ──┘                │
                                                                                v
                                                                      SQLCipher DB & Files

[ Replacement-Phone Recovery Chain (Device B - Zero KeyStore Reliance) ]
24-Word BIP39 Recovery Seed ──> PBKDF2WithHmacSHA512 ──> KEK_recovery ──> unwraps ──> DEK
```

### 1.3 Streaming AEAD Backup & Crash-Safe Restore
Offline `.eltkbackup` container exports use Google Tink `AesGcmHkdfStreaming` (4KB chunks) with Associated Authenticated Data (AAD) header binding. Restores execute through a 7-state transactional state machine (`restore_state.json`), enforcing 2.5x storage space pre-checks and pre-restore snapshotting (`files/rollback_backup/`) with automatic startup rollback upon crash detection.

---

## 2. DEPENDENCY-ORDERED IMPLEMENTATION PLAN

| Increment | Target Component | Purpose & Files Affected | Acceptance Test |
| :--- | :--- | :--- | :--- |
| **Inc 1** | **Build & Test Harness** | Gradle 8.9 wrapper, JUnit, Robolectric, AndroidX Test Core in `app/build.gradle.kts`. | `./gradlew test` passes smoke test on clean checkout. (**COMPLETED**) |
| **Inc 2** | **Key Management System** | Implement `KeyManager.java` (PBKDF2 key derivation, KeyStore wrapping, 24-word seed recovery). | Test T1 passes (valid seed unwraps DEK; wrong seed fails). |
| **Inc 3** | **SQLCipher Storage Engine**| Implement `SqliteDatabase.java` with SQLCipher page-level encryption. | Test T2 & T5 pass (0% plaintext on disk; WAL frames encrypted). |
| **Inc 4** | **Streaming AEAD Backup** | Implement `BackupEngine.java` using Tink `AesGcmHkdfStreaming` + AAD header binding. | Test T3 & T4 pass (round trip produces identical SHA-256 hashes). |
| **Inc 5** | **Transactional Restore Engine**| Implement `RestoreStateMachine.java` with 2.5x pre-check & startup auto-rollback. | Test T6 & T8 pass (SIGKILL mid-restore auto-recovers snapshot). |
