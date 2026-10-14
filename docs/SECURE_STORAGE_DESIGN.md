STATUS: PROPOSED — NOT IMPLEMENTED
BASELINE: dbd4153ca39ede69890ebaf47feaab7f393c3c5a
PR REFERENCE: Pull Request #1 (`feature/secure-storage-foundation`)
SCOPE: Local-First Secure Storage Architecture for Elite Kit (`com.elitekit.app`)
FINANCIAL CONSTRAINT: $0 Ongoing Cost (Zero Paid APIs, Subscriptions, or Cloud Services)
DATA POLICY: 100% Synthetic Fictional Test Data Only

# ELITE KIT — SECURE STORAGE ARCHITECTURE SPECIFICATION

Document Path: `docs/SECURE_STORAGE_DESIGN.md`
Authoritative Baseline SHA: `dbd4153ca39ede69890ebaf47feaab7f393c3c5a`
Governing Authority: `MASTER_CONTROL.md`

---

## 1. THREAT MODEL AND SECURITY OBJECTIVES

### 1.1 Addressed Threat Vectors
1. **Physical Theft or Unattended Device Loss**: Private data on un-activated or locked devices is protected via AES-256-GCM encryption (`DEK`). Extraction of private flash files (`/data/data/com.elitekit.app/`) yields unreadable ciphertext.
2. **Co-located Unprivileged Application Access**: Sandboxed private storage (`context.getFilesDir()`) combined with SQLCipher page-level encryption prevents access by other installed Android apps.
3. **Forgotten Master Passphrase**: Users unwrap `DEK` and recover full workspace access using their offline 24-word BIP39 Recovery Mnemonic Seed.
4. **Device Replacement / KeyStore TEE Loss**: Hardware KeyStore keys do not migrate across phones. Clean-device recovery unwraps `DEK` via the 24-word seed without dependence on the old phone's KeyStore.
5. **Corrupted or Malicious Backup**: Backups verify AEAD authentication tags in isolated staging before touching active storage. Tampered or truncated files fail verification and are purged without modifying active records.
6. **Interrupted Restoration**: Power loss or process kill during restore triggers automatic startup rollback from `files/rollback_backup/` snapshot.
7. **Accidental Deletion & Database Inconsistency**: Single-file exports (`.eltkbackup`) store database records and evidence files in a single atomic payload. Restores are transactional.

### 1.2 Explicit Security Limitations & Out-of-Scope Risks
- **OS Kernel & Root Compromise**: The application **CANNOT** guarantee data protection if the underlying Android OS is rooted, compromised by kernel-level malware, or subject to active runtime process injection/keylogging.
- **Offline Single-Device Replay / Rollback**: A physical attacker can restore a valid, older untampered backup file. While cryptographic AEAD tags verify archive integrity, detecting valid older backups relies on local KeyStore launch counters and Workspace Audit Journals.

---

## 2. ENCRYPTED STORAGE ARCHITECTURE

### 2.1 Storage Layer Selection (SQLCipher for Android)
To prevent sensitive plaintext from leaking through Write-Ahead Log (`.db-wal`), Rollback Journal (`.db-journal`), temporary index files, and structural metadata (table/column names), Elite Kit uses **SQLCipher for Android** (`net.zetetic:android-database-sqlcipher`):
- **100% Page-Level Encryption**: Every database page (data, indexes, headers, WAL frames, journals) is encrypted at the page level via AES-256-CBC with HMAC-SHA512 integrity checks.
- **Side-File Leakage Prevention**: `.db-wal` and `.db-journal` frames are encrypted with the same page key before touching disk storage.
- **$0 Cost & License**: Open source (BSD-style license), 100% Java 17 and Android API 26+ compatible. Adds ~12MB APK footprint.

### 2.2 Evidence Files Engine
Imported documents, media, and attachments are saved in application-private storage (`context.getFilesDir() + "/evidence/"`) and encrypted using stream-level AES-256-GCM (`CipherOutputStream`) using `DEK`.

---

## 3. ENCRYPTION-KEY HIERARCHY AND KEYSTORE LIMITATIONS

```
[ Primary Unlock Flow (Device A) ]
Master Passphrase ──> PBKDF2WithHmacSHA256 (salt_passphrase.bin) ──> KEK_passphrase ──┐
                                                                                     ├──> unwraps ──> DEK (256-bit AES)
Android KeyStore TEE ──────────────────────────────────────────────> KEK_keystore ──┘                │
                                                                                                     v
                                                                                           SQLCipher DB & Files

[ Replacement-Phone Recovery Chain (Device B - Zero KeyStore Reliance) ]
24-Word BIP39 Seed Material ──> PBKDF2WithHmacSHA512 (salt_recovery.bin) ──> KEK_recovery ──> unwraps ──> DEK
```

1. **Data Encryption Key (`DEK`)**: Randomly generated 256-bit AES key. Encrypts local SQLCipher database pages and evidence files. Generated once on first setup and remains unchanged when passphrases rotate.
2. **Key-Encryption Key Passphrase (`KEK_passphrase`)**: Derived from Master Passphrase via `PBKDF2WithHmacSHA256` (100,000+ iterations). Unwraps `wrapped_dek_passphrase.bin`.
3. **Android KeyStore TEE Key (`KEK_keystore`)**: Non-exportable hardware key generated inside Android KeyStore TEE (`"EliteKit_Keystore_KEK"`). Unwraps `wrapped_dek_keystore.bin` for fast PIN/biometric unlock.
4. **Recovery Key-Encryption Key (`KEK_recovery`)**: Derived from 24-word BIP39 seed via `PBKDF2WithHmacSHA512`. Unwraps `wrapped_dek_recovery.bin`.
5. **KeyStore Limitations**: Android KeyStore keys are hardware-bound to the local device TEE and **do not transfer** to replacement phones. Recovery relies 0% on KeyStore.

---

## 4. PASSPHRASE AND REPLACEMENT-PHONE RECOVERY DESIGN

1. **Clean Hardware Setup (Device B)**: User installs clean app on Device B (empty KeyStore) and inputs their 24-word BIP39 Recovery Seed.
2. **Seed Key Derivation**: Device B derives `KEK_recovery = PBKDF2WithHmacSHA512(seed, salt_recovery)`.
3. **DEK Unwrapping**: Device B unwraps `wrapped_dek_recovery.bin` from the imported `.eltkbackup` archive header, recovering `DEK` in ephemeral RAM.
4. **Local KeyStore Binding**: Device B generates a new local KeyStore key `KEK_keystore` on Device B and re-wraps `DEK` as `wrapped_dek_keystore.bin` for local biometric unlock.
5. **Fails Closed**: If both Master Passphrase and 24-word Recovery Seed are lost, `DEK` unwrap is impossible. Data is permanently unrecoverable (zero backdoor).

---

## 5. ENCRYPTED BACKUP AND RESTORE ARCHITECTURE

### 5.1 Versioned Backup Container Layout (`.eltkbackup`)
```
+-----------------------------------------------------------------------------------+
| 1. Magic Header: "ELITEKIT_BKUP_V1" (16 bytes ASCII)                             |
+-----------------------------------------------------------------------------------+
| 2. Format Version: 0x0001 (2 bytes, uint16)                                       |
+-----------------------------------------------------------------------------------+
| 3. Cipher Suite ID: 0x0001 = AES-256-GCM-HKDF-4KB (2 bytes, uint16)                |
+-----------------------------------------------------------------------------------+
| 4. Header Length: uint32 (4 bytes) length of fields 5 + 6                         |
+-----------------------------------------------------------------------------------+
| 5. KDF Salt Material: 32 bytes random salt                                        |
+-----------------------------------------------------------------------------------+
| 6. Wrapped DEK Recovery Blob: wrapped_dek_recovery.bin (80 bytes)                 |
+-----------------------------------------------------------------------------------+
| 7. Streaming AEAD Payload (Google Tink Stream)                                   |
|    - Bound to Header Fields 1-6 via Associated Authenticated Data (AAD)          |
|    - Payload chunks: 4KB encrypted segments + 16-byte GCM tags                    |
+-----------------------------------------------------------------------------------+
```

### 5.2 Cryptographic Header Binding (AAD)
The entire 136-byte header payload (fields 1–6) is passed as **Associated Authenticated Data (AAD)** into Google Tink's `AesGcmHkdfStreaming.newEncryptingChannel(outputStream, headerAADBytes)`. Any modification to header bytes or payload chunks invalidates the GCM authentication tag, throwing `AEADBadTagException`.

---

## 6. PROTECTION AGAINST PARTIAL RESTORES AND DATA LOSS

```
 [ STATE_IDLE ] ──(Select Backup)──> [ STATE_VERIFYING ]
                                            │
                                            v (AEAD Tag & Manifest OK)
                                     [ STATE_PROPOSING ]
                                            │
                                            v (User Explicitly Approves)
                                     [ STATE_SNAPSHOT ] ──> Copy files/data/ to files/rollback_backup/
                                            │
                                            v
                                     [ STATE_ACTIVATING ] ──> Swap staging -> files/data/
                                            │
                                  +---------+---------+
                                  |                   |
                            (Success)             (Failure / Process Death)
                                  v                   v
                         [ STATE_COMPLETE ]   [ STATE_ROLLBACK ] ──> Restore files/rollback_backup/
                                  │                   │
                                  +---------+---------+
                                            │
                                            v
                                   [ STATE_CLEANUP ] ──> Delete staging & snapshot dirs
```

1. **Storage Pre-Check**: Before restore begins, available disk space must be at least **2.5x** archive size (1.0x staging + 1.0x pre-restore snapshot + 0.5x safety buffer). If storage is insufficient, restore halts in `STATE_VERIFYING` with zero state changes.
2. **Pre-Restore Snapshot**: Active database and evidence files (`files/data/`) are copied to `files/rollback_backup/`. State `STATE_SNAPSHOT` is written to `restore_state.json` and flushed via `FileOutputStream.getFD().sync()`.
3. **Startup Auto-Rollback**: Staged data replaces active data (`files/data/`). If process death or power loss occurs during `STATE_ACTIVATING`, `Application.onCreate()` checks `restore_state.json` upon next restart, detects incomplete activation, and automatically restores `files/rollback_backup/` pre-restore snapshot intact. Damaged archives or interrupted restores never result in data loss or a wiped workspace.

---

## 7. FILE/DATABASE CONSISTENCY AND SCHEMA VERSIONING

- **Atomic Container**: The `.eltkbackup` export bundles the SQLite database dump and all evidence attachments into a single atomic Tink streaming payload.
- **Schema Migration Protocol**: Backup manifest records database schema version (e.g., `schemaVersion: 1`). Upon import, `DatabaseHelper` runs SQLite migration scripts sequentially before committing the restored dataset.

---

## 8. FAILURE HANDLING, LOCKING, AND USER APPROVAL

- **Destructive Operations Approval**: Dataset restoration, workspace wiping, and passphrase changes require explicit user authorization via confirmation UI dialogs.
- **Database Locks**: Active write transactions acquire exclusive SQLite database locks (`BEGIN EXCLUSIVE`). Staging restores occur in isolated directory `cache/restore_staging/` to prevent concurrency conflicts.

---

## 9. REQUIRED UNIT, INTEGRATION, AND RECOVERY TESTS

*(Note: Test dependencies must be added to `app/build.gradle.kts` in Increment 1. All tests below are PROPOSED specifications marked **NOT RUN** at baseline SHA `dbd4153ca39ede69890ebaf47feaab7f393c3c5a`)*

| Test ID | Test Scope | Execution Command | Acceptance Criteria | Status |
| :--- | :--- | :--- | :--- | :--- |
| **T1** | **Passphrase & Seed KDF** | `./gradlew test --tests "*KeyManagerTest*"` | Correct passphrase/seed unwraps DEK; wrong credential throws `GeneralSecurityException`. | **NOT RUN** |
| **T2** | **Ciphertext Confidentiality** | `./gradlew test --tests "*CiphertextTest*"` | Stored payload contains 0% plaintext; bit-flip throws `AEADBadTagException`. | **NOT RUN** |
| **T3** | **Backup Round Trip** | `./gradlew test --tests "*BackupRoundTripTest*"` | Exported `.eltkbackup` restored to fresh DB produces 100% identical record SHA-256 digests. | **NOT RUN** |
| **T4** | **Corrupt Backup Rejection** | `./gradlew test --tests "*CorruptBackupTest*"` | Malformed archive rejected in staging; existing database 100% untouched. | **NOT RUN** |
| **T5** | **SQLCipher Side-File Check** | `./gradlew test --tests "*DatabaseSideFileTest*"` | Confirms `.db-wal` and `.db-journal` on disk contain zero plaintext substrings. | **NOT RUN** |
| **T6** | **Interrupted Restore Crash** | `./gradlew connectedAndroidTest --tests "*RestoreCrashTest*"` | SIGKILL mid-restore auto-recovers pre-restore snapshot on application restart. | **NOT RUN** |
| **T7** | **Replacement Phone Recovery** | `./gradlew connectedAndroidTest --tests "*RecoveryTest*"` | Fresh instance with empty KeyStore recovers workspace using 24-word seed. | **NOT RUN** |
| **T8** | **Preservation After Failure** | `./gradlew test --tests "*PreservationTest*"` | Disk-full or invalid header during restore leaves active dataset 100% unmodified. | **NOT RUN** |

---

## 10. IMPLEMENTATION PHASES AND ACCEPTANCE CRITERIA

| Increment | Scope & Files Affected | Purpose | Acceptance Criteria | Rollback Method |
| :--- | :--- | :--- | :--- | :--- |
| **Inc 1** | Build harness (`app/build.gradle.kts`) | Add JUnit, Robolectric, & test dependencies. | `./gradlew test` passes on clean checkout. | Git revert |
| **Inc 2** | KMS (`app/.../crypto/KeyManager.java`) | Key derivation & 24-word seed recovery. | Valid seed unwraps DEK; wrong seed fails. | Remove files |
| **Inc 3** | DB Engine (`app/.../storage/SqliteDatabase.java`)| SQLCipher page-level encrypted DB storage. | Payload ciphertext stored; WAL frames encrypted. | Remove files |
| **Inc 4** | Backup (`app/.../backup/BackupEngine.java`) | Tink streaming AEAD export/import. | Export/import round trip yields identical hashes. | Remove files |
| **Inc 5** | Restore (`app/.../backup/RestoreStateMachine.java`)| 7-state transactional restore engine. | SIGKILL mid-restore auto-recovers snapshot on startup.| Remove files |

---

## 11. UNRESOLVED DESIGN DECISIONS AND RISKS

1. **Storage Engine Binary Overhead**: SQLCipher adds ~12MB native JNI binary footprint (`libsqlcipher.so`). Confirm explicit approval of SQLCipher over standard SDK SQLite with `PRAGMA journal_mode = MEMORY;`.
2. **Phase 3 Implementation Gate Authorization**: Explicit human approval required before advancing from Phase 2 (Documentation) to Phase 3 (Minimum Code Implementation).

---

## 12. EXPLICIT STATEMENTS OF UNIMPLEMENTED / UNVERIFIED STATE

- **STATUS**: All security capabilities, encryption engines, backup generators, restore state machines, and key management systems described in this document are **PROPOSED ARCHITECTURE SPECIFICATIONS** and remain **100% UNIMPLEMENTED AND UNVERIFIED** at baseline `dbd4153ca39ede69890ebaf47feaab7f393c3c5a`.
- **TEST EXECUTION STATUS**: **0 automated tests have been executed**. All tests T1–T8 are marked **NOT RUN**.
- **DATA POLICY**: Real confidential legal records remain **STRICTLY PROHIBITED** until Increments 1–5 are implemented and all tests T1–T8 pass CI verification.
