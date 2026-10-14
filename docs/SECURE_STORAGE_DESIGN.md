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

## 1. THREAT MODEL AND BOUNDARIES

### 1.1 Addressed Threats
1. **Lost or Stolen Physical Phone**: Data on un-activated or locked devices is protected via AES-256-GCM encryption (`DEK`). Extraction of private flash files (`/data/data/com.elitekit.app/`) yields unreadable ciphertext.
2. **Co-located Unprivileged App Access**: Sandboxed private storage (`context.getFilesDir()`) combined with payload encryption prevents access by other installed Android apps.
3. **Forgotten Master Passphrase**: User can unwrap `DEK` and recover full workspace access using their offline 24-word BIP39 Recovery Mnemonic Seed.
4. **Device Replacement / KeyStore Loss**: Hardware KeyStore keys do not migrate across phones. Clean-device recovery unwraps `DEK` via the 24-word seed without dependence on the old phone's KeyStore.
5. **Corrupted or Malicious Backup**: Backups verify AEAD authentication tags in isolated staging before touching active storage. Tampered or truncated files fail verification and are purged without modifying active records.
6. **Interrupted Restoration**: Power loss or process kill during restore triggers automatic startup rollback from `files/rollback_backup/` snapshot.
7. **Accidental Deletion & Database Inconsistency**: Single-file exports (`.eltkbackup`) store database records and evidence files in a single atomic payload. Restores are transactional.

### 1.2 Explicit Security Limitations
- **OS Kernel & Root Compromise**: The application **CANNOT** guarantee data protection if the underlying Android OS is rooted, compromised by kernel-level malware, or subject to active runtime process injection/keylogging.
- **Offline Replay / Rollback**: A physical attacker can restore a valid, older untampered backup file. While cryptographic AEAD tags verify archive integrity, detecting valid older backups relies on local KeyStore launch counters and Workspace Audit Journals.

---

## 2. MINIMUM ARCHITECTURE AND BOUNDARIES

- **Storage Boundary**: Standard Android SDK SQLite (`android.database.sqlite`) in private app storage. Confidential fields (notes, text, metadata) stored as AES-256-GCM encrypted byte arrays. Evidence files stream-encrypted in `context.getFilesDir() + "/evidence/"`.
- **Key-Management Boundary**: Hardware TEE KeyStore alias `"EliteKit_Keystore_KEK"` handles local fast unlock. Key derivation (PBKDF2) and key unwrapping execute strictly in ephemeral app RAM.
- **Backup & Restore Boundary**: Single-file offline `.eltkbackup` container. Export/import uses Google Tink streaming AEAD. Staging occurs strictly in `cache/restore_staging/`.
- **User-Approval Boundary**: Explicit human approval dialog required before executing destructive actions (e.g., dataset restore or workspace wipe).

---

## 3. KEY HIERARCHY AND REPLACEMENT-PHONE RECOVERY

```
[ Primary Unlock Flow (Device A) ]
Master Passphrase ──> PBKDF2WithHmacSHA256 (salt_passphrase.bin) ──> KEK_passphrase ──┐
                                                                                     ├──> unwraps ──> DEK (256-bit AES)
Android KeyStore TEE ──────────────────────────────────────────────> KEK_keystore ──┘                │
                                                                                                     v
                                                                                           SQLite DB & Files

[ Replacement-Phone Recovery Chain (Device B - Zero KeyStore Reliance) ]
24-Word BIP39 Seed Material ──> PBKDF2WithHmacSHA512 (salt_recovery.bin) ──> KEK_recovery ──> unwraps ──> DEK
```

1. **Data Encryption Key (`DEK`)**: Randomly generated 256-bit AES key. Encrypts local database payloads and evidence files. Generated once on first setup and remains unchanged when passphrases rotate.
2. **Key-Encryption Key Passphrase (`KEK_passphrase`)**: Derived from Master Passphrase via `PBKDF2WithHmacSHA256` (100,000+ iterations). Unwraps `wrapped_dek_passphrase.bin`.
3. **Android KeyStore TEE Key (`KEK_keystore`)**: Non-exportable hardware key generated inside Android KeyStore TEE (`"EliteKit_Keystore_KEK"`). Unwraps `wrapped_dek_keystore.bin` for fast PIN/biometric unlock.
4. **Recovery Key-Encryption Key (`KEK_recovery`)**: Derived from 24-word BIP39 seed via `PBKDF2WithHmacSHA512`. Unwraps `wrapped_dek_recovery.bin`.
5. **Replacement-Phone Recovery**: On clean hardware (Device B), entering the 24-word seed derives `KEK_recovery`, which unwraps `wrapped_dek_recovery.bin` from the `.eltkbackup` archive, recovering `DEK` directly without access to Device A's KeyStore.
6. **Key Loss Consequences**: Fails closed. If both Master Passphrase and 24-word Recovery Seed are lost, data is mathematically unrecoverable.

---

## 4. ENCRYPTION STRATEGY AND SPECIFICATION

- **Cryptographic Implementation**: Uses standard Java Cryptography Extension (JCE) `javax.crypto.Cipher` (`AES/GCM/NoPadding`) and peer-reviewed Google Tink (`com.google.crypto.tink:tink-android`) for streaming AEAD. Zero custom cryptographic primitives.
- **Key Derivation Parameters**: `PBKDF2WithHmacSHA256` (passphrase, 100k+ iterations, 32-byte salt) and `PBKDF2WithHmacSHA512` (24-word seed, 2048 iterations, 32-byte salt).
- **Nonce / IV Handling**: 12-byte random IV generated per row/file encryption via `SecureRandom`. Backup streams derive IVs deterministically via HKDF per 4KB segment, ensuring 0% nonce reuse.
- **Authentication Failure Behavior**: Any modified bit or invalid tag throws `AEADBadTagException`, instantly halting read/import operations and purging unauthenticated staging files.

---

## 5. BACKUP AND RESTORATION FORMAT

- **Backup Format (`.eltkbackup`)**: Single offline archive containing:
  - `[ 16 bytes: Magic Header "ELITEKIT_BKUP_V1" ]`
  - `[ 2 bytes: uint16 Format Version 0x0001 ]`
  - `[ 2 bytes: uint16 Cipher Suite ID 0x0001 (AES-256-GCM-HKDF-4KB) ]`
  - `[ 32 bytes: KDF Salt ]`
  - `[ 80 bytes: wrapped_dek_recovery.bin ]`
  - `[ 8 bytes: uint64 Payload Byte Size ]`
  - `[ Stream: Chunked 4KB AES-256-GCM Ciphertext Segments + Tags ]`
- **Crash-Safe Restore Protocol**:
  1. Unpack archive stream into `cache/restore_staging/`. Verify format version and all chunk AEAD tags.
  2. Confirm available disk space >= 2.5x archive size (1.0x staging + 1.0x pre-restore snapshot + 0.5x safety buffer).
  3. Snapshot active workspace (`files/data/`) to `files/rollback_backup/` and sync `restore_state.json` via `fsync()`.
  4. Swap staged data into `files/data/`. If process death occurs during swap, `Application.onCreate()` checks `restore_state.json` on next startup and automatically restores `files/rollback_backup/` intact.

---

## 6. TESTING STRATEGY (PROPOSED MATRIX)

*(Note: All listed tests below are PROPOSED specifications marked **NOT RUN** at baseline SHA `dbd4153ca39ede69890ebaf47feaab7f393c3c5a`)*

| Test ID | Test Scope | Verification Command | Acceptance Criteria | Status |
| :--- | :--- | :--- | :--- | :--- |
| **T1** | **Passphrase Auth** | `./gradlew test --tests "*KeyManagerTest.testPassphrase*"` | Correct passphrase unwraps DEK; wrong passphrase fails gracefully. | **NOT RUN** |
| **T2** | **Ciphertext Confidentiality** | `./gradlew test --tests "*StorageTest.testCiphertext*"` | Stored payload contains zero plaintext; bit-flip throws `AEADBadTagException`. | **NOT RUN** |
| **T3** | **Backup Round Trip** | `./gradlew test --tests "*BackupTest.testRoundTrip*"` | Exported `.eltkbackup` restored to fresh DB produces identical record hashes. | **NOT RUN** |
| **T4** | **Corrupted Backup Rejection** | `./gradlew test --tests "*BackupTest.testCorrupt*"` | Malformed archive rejected in staging; existing database 100% untouched. | **NOT RUN** |
| **T5** | **Interrupted Restore Crash Recovery** | `./gradlew connectedAndroidTest --tests "*RestoreCrashTest*"` | Simulated SIGKILL mid-restore auto-recovers pre-restore snapshot on restart. | **NOT RUN** |
| **T6** | **Replacement Phone Recovery** | `./gradlew connectedAndroidTest --tests "*RecoveryTest*"` | Fresh instance with empty KeyStore recovers workspace using 24-word seed. | **NOT RUN** |
| **T7** | **Preservation After Failure** | `./gradlew test --tests "*PreservationTest*"` | Disk-full or invalid header during restore leaves active dataset 100% unmodified. | **NOT RUN** |
| **T8** | **Rollback Sequence Detection** | `./gradlew test --tests "*RollbackDetectionTest*"` | Restoring an older valid backup increments timeline journal and alerts user. | **NOT RUN** |

---

## 7. IMPLEMENTATION ORDER & TRIAGE

| Increment | Scope & Files Affected | Purpose | Triage | Acceptance Criteria | Rollback | Real-Record Blocker? |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Inc 1** | Build harness (`build.gradle.kts`, `app/build.gradle.kts`) | Setup testing harness. | P1 | `./gradlew test` passes on clean checkout. | Git revert | Yes |
| **Inc 2** | KMS (`app/.../crypto/KeyManager.java`, `Bip39Util.java`) | Key derivation & seed recovery. | P1 | Valid seed unwraps DEK; wrong seed fails. | Remove files | Yes |
| **Inc 3** | DB Engine (`app/.../storage/EncryptedDatabase.java`) | SDK SQLite AEAD storage. | P1 | Payload ciphertext stored; bit-flip throws exception. | Remove files | Yes |
| **Inc 4** | Backup (`app/.../backup/BackupEngine.java`) | Streaming AEAD export/import. | P1 | Export/import round trip yields identical hashes. | Remove files | Yes |
| **Inc 5** | Restore (`app/.../backup/RestoreStateMachine.java`) | 7-state transactional restore. | P1 | SIGKILL mid-restore auto-recovers snapshot on startup. | Remove files | Yes |

---

## 8. OPEN DECISIONS & EXPLICIT LIMITATIONS

### 8.1 Open Decisions Requiring Approval
1. **Storage Engine Choice**: Confirm approval of standard SDK SQLite payload encryption ($0 added dependencies, 0MB APK footprint) vs. SQLCipher (~12MB APK size increase for full-page encryption).
2. **Phase 3 Implementation Gate Authorization**: Explicit human approval required to advance from Phase 2 (Documentation) to Phase 3 (Minimum Code Implementation).

### 8.2 Explicit Security Limitations
- Physical root / kernel compromise is out of threat model scope.
- Device-local AI engine capabilities are unverified and excluded.
- Real legal records remain strictly prohibited until Increments 1–5 are implemented and all tests T1–T8 pass CI verification.
