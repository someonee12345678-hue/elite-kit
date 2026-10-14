STATUS: PROPOSED MINIMUM ARCHITECTURE SPECIFICATION — NOT IMPLEMENTED
BASELINE: dbd4153ca39ede69890ebaf47feaab7f393c3c5a
PR REFERENCE: Pull Request #1 (`feature/secure-storage-foundation`)
SCOPE: Local-First Minimum Viable Safe Storage for Elite Kit (`com.elitekit.app`)
FINANCIAL CONSTRAINT: $0 Ongoing Cost (Zero Paid APIs, Subscriptions, or Cloud Services)
DATA POLICY: 100% Synthetic Fictional Test Data Only

# ELITE KIT — MINIMUM SAFE ARCHITECTURE & SECURE STORAGE SPECIFICATION

Document Path: `docs/SECURE_STORAGE_DESIGN.md`
Authoritative Baseline SHA: `dbd4153ca39ede69890ebaf47feaab7f393c3c5a`
Governing Authority: `MASTER_CONTROL.md`

---

## 1. FINDINGS ON CURRENT DESIGN AND DEFERRED MECHANISMS

Following a minimum-viable-product review of the initial storage design proposals, the architecture has been streamlined to eliminate unnecessary complexity, unsupported assumptions, and maintenance overhead:

### 1.1 Identified Complexities & Risks Removed
1. **Third-Party Native DB Binaries (SQLCipher)**: Adding SQLCipher introduces ~12MB of native C/NDK JNI binaries (`libsqlcipher.so`), requiring ongoing NDK maintenance, targetSdk 35 16KB memory page alignment compliance, and third-party dependency risks.
   *Resolution*: Deferred. Replaced with standard Android SDK SQLite (`android.database.sqlite`) paired with AES-256-GCM field/column payload encryption.
2. **Circular Key Wrapping & Redundant Key Wraps**: Prior drafts specified multiple nested key recovery wraps inside backup headers.
   *Resolution*: Streamlined to a clean 3-key hierarchy (`DEK`, `KEK_passphrase`, `KEK_recovery`).
3. **Speculative Nonessential Capabilities**:
   *Resolution*: Explicitly deferred: local AI engines, dynamic executable updates, cloud sync adapters, automated case management rules, and multi-user access controls.

---

## 2. MINIMUM RECOMMENDED ARCHITECTURE

### 2.1 Storage & Payload Encryption
- **Structured Workspace Database**: Standard Android SDK SQLite (`android.database.sqlite`) in private storage (`context.getDatabasePath()`). Confidential legal fields (notes, text, metadata) are stored as encrypted byte arrays via Java JCE `Cipher` (`AES/GCM/NoPadding`, 256-bit `DEK`, 12-byte unique random IV per row).
- **Imported Media & Evidence Files**: Saved in application-private storage (`context.getFilesDir() + "/evidence/"`) and encrypted using stream-level AES-256-GCM (`CipherOutputStream`).
- **Zero Paid Dependencies**: Built 100% on standard Android AOSP APIs (`minSdk 26`, Java 17).

### 2.2 Key Management System & Replacement-Phone Recovery

```
[ Primary Unlock Flow (Device A) ]
Master Passphrase ──> PBKDF2WithHmacSHA256 (salt_passphrase.bin) ──> KEK_passphrase ──┐
                                                                                     ├──> unwraps ──> DEK (256-bit AES)
Android KeyStore TEE ──────────────────────────────────────────────> KEK_keystore ──┘                │
                                                                                                     v
                                                                                           Encrypted DB & Files

[ Replacement-Phone Recovery Chain (Device B - Zero KeyStore Reliance) ]
24-Word BIP39 Recovery Seed ──> PBKDF2WithHmacSHA512 (salt_recovery.bin) ──> KEK_recovery ──> unwraps ──> DEK
```

1. **Non-Circular Key Separation**:
   - `DEK` (Data Encryption Key): Random 256-bit AES key generated on first setup. Encrypts local database records and documents. `DEK` never changes when passphrases change.
   - `KEK_passphrase`: Derived from user Master Passphrase via `PBKDF2WithHmacSHA256`.
   - `KEK_keystore`: Hardware-backed KeyStore alias `"EliteKit_Keystore_KEK"` for PIN/biometric fast unlock on Device A.
   - `KEK_recovery`: Derived from 24-word BIP39 seed via `PBKDF2WithHmacSHA512`.
2. **Replacement-Phone Recovery**: On clean hardware (Device B), entering the 24-word seed derives `KEK_recovery`, which unwraps `wrapped_dek_recovery.bin` from the user's `.eltkbackup` archive. Device B then generates a new local KeyStore alias `KEK_keystore` and re-wraps `DEK` locally for biometric unlock on Device B.
3. **Forgotten Credentials**: Fails closed. If both Master Passphrase and 24-word Recovery Seed are lost, stored ciphertext is mathematically unrecoverable.

### 2.3 User-Controlled Backup and Crash-Safe Transactional Restore
- **Backup Container (`.eltkbackup`)**: Single offline file containing magic header (`ELITEKIT_BKUP_V1`), uint16 format version (`0x0001`), 32-byte salt, and chunked 4KB streaming AES-256-GCM encrypted payload (Google Tink `AesGcmHkdfStreaming`).
- **Crash-Resilient Restore Engine**:
  1. Stream archive to `cache/restore_staging/`. Verify all chunk AEAD authentication tags before touching active data.
  2. Confirm available storage >= 2.5x archive size (1.0x staging + 1.0x pre-restore snapshot + 0.5x buffer).
  3. Copy active data (`files/data/`) to `files/rollback_backup/` and sync `restore_state.json` via `fsync()`.
  4. Swap staged data into `files/data/`. If process death or crash occurs during swap, `Application.onCreate()` checks `restore_state.json` on next startup and automatically restores `files/rollback_backup/` intact.

---

## 3. SMALLEST IMPLEMENTATION SEQUENCE

```
+-------------------------------------------------------------------------+
| Increment 1: Build Harness & Test Foundations                           |
| - Configure JUnit/Robolectric test harness in gradle scripts.           |
+-------------------------------------------------------------------------+
                                    │
                                    v
+-------------------------------------------------------------------------+
| Increment 2: Key Management System & 24-Word Seed Recovery              |
| - Implement PBKDF2 derivation, KeyStore wrapping, & BIP39 seed recovery.|
+-------------------------------------------------------------------------+
                                    │
                                    v
+-------------------------------------------------------------------------+
| Increment 3: SDK SQLite AEAD Encrypted Storage Engine                   |
| - Implement AES-256-GCM payload encryption for database & evidence files.|
+-------------------------------------------------------------------------+
                                    │
                                    v
+-------------------------------------------------------------------------+
| Increment 4: Streaming AEAD Backup Exporter & Importer                  |
| - Implement Tink AesGcmHkdfStreaming container exporter/importer.      |
+-------------------------------------------------------------------------+
                                    │
                                    v
+-------------------------------------------------------------------------+
| Increment 5: Transactional Crash-Safe Restore State Machine             |
| - Implement 7-state restore engine with auto-rollback on startup.       |
+-------------------------------------------------------------------------+
```

| Increment | Scope & Files Affected | Acceptance Test | Dependency |
| :--- | :--- | :--- | :--- |
| **Inc 1** | Build harness (`build.gradle.kts`, `app/build.gradle.kts`) | `./gradlew test` passes smoke test on clean workspace. | Baseline SHA |
| **Inc 2** | KMS (`app/.../crypto/KeyManager.java`, `Bip39Util.java`) | Valid passphrase/seed unwraps DEK; invalid credential fails gracefully. | Inc 1 |
| **Inc 3** | DB Engine (`app/.../storage/EncryptedDatabase.java`) | Payload ciphertext stored on disk; bit-flip throws `AEADBadTagException`. | Inc 2 |
| **Inc 4** | Backup (`app/.../backup/BackupEngine.java`) | Backup export & import round trip produces identical record hashes. | Inc 3 |
| **Inc 5** | Restore (`app/.../backup/RestoreStateMachine.java`) | SIGKILL mid-restore auto-recovers `files/rollback_backup/` snapshot on restart. | Inc 4 |

---

## 4. MINIMUM AUTOMATED TEST MATRIX

*(Note: All tests below are PROPOSED specifications marked **NOT RUN** at baseline SHA `dbd4153ca39ede69890ebaf47feaab7f393c3c5a`)*

| Test ID | Scope | Verification Execution Command | Minimum Acceptance Criteria | Status |
| :--- | :--- | :--- | :--- | :--- |
| **T1** | Unit (CI) | `./gradlew test --tests "*KeyManagerTest.testPassphrase*"` | Correct passphrase unwraps DEK; wrong passphrase fails gracefully. | **NOT RUN** |
| **T2** | Unit (CI) | `./gradlew test --tests "*StorageTest.testCiphertext*"` | Stored payload on disk contains zero plaintext substrings; tamper bit-flip fails tag check. | **NOT RUN** |
| **T3** | Unit (CI) | `./gradlew test --tests "*BackupTest.testRoundTrip*"` | Exported `.eltkbackup` restored to fresh DB produces identical record hashes. | **NOT RUN** |
| **T4** | Instrumentation | `./gradlew connectedAndroidTest --tests "*RecoveryTest*"` | Fresh instance with empty KeyStore recovers workspace using 24-word seed. | **NOT RUN** |
| **T5** | Unit (CI) | `./gradlew test --tests "*BackupTest.testCorrupt*"` | Malformed archive rejected in staging; existing database 100% untouched. | **NOT RUN** |
| **T6** | Instrumentation | `./gradlew connectedAndroidTest --tests "*RestoreCrashTest*"` | Simulated SIGKILL mid-restore auto-recovers pre-restore snapshot on application restart. | **NOT RUN** |

---

## 5. REAL-RECORD RELEASE GATE & GENUINE BLOCKERS

### 5.1 Real-Record Gate
No real legal records may be entered or processed in Elite Kit until **100%** of Increments 1–5 are implemented and all tests T1–T6 pass automated CI verification.

### 5.2 Genuine Blockers Requiring Human Authorization
1. **Storage Engine Approval**: Confirm approval of standard SDK SQLite payload-level encryption (recommended baseline, 0MB overhead) vs. full-page SQLCipher (~12MB native binary footprint).
2. **Implementation Gate Authorization**: Explicit human authorization required to advance from Phase 2 (Documentation Specification) to Phase 3 (Minimum Code Implementation).
