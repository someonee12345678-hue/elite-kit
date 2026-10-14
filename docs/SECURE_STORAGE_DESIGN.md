STATUS: PROPOSED MINIMUM ARCHITECTURE SPECIFICATION — NOT IMPLEMENTED
BASELINE: dbd4153ca39ede69890ebaf47feaab7f393c3c5a
SCOPE: Minimum Viable Secure Storage for Elite Kit (`com.elitekit.app`)
FINANCIAL CONSTRAINT: $0 Ongoing Cost
DATA POLICY: Synthetic Fictional Test Data Only

# ELITE KIT — MINIMUM SECURE-STORAGE DESIGN SPECIFICATION

Document Path: `docs/SECURE_STORAGE_DESIGN.md`
Authoritative Baseline SHA: `dbd4153ca39ede69890ebaf47feaab7f393c3c5a`
Governing Authority: `MASTER_CONTROL.md`

---

## 1. IMPLEMENTATION BOUNDARY & STATUS

- **Status Classifications**:
  - **Design Status**: `PROPOSED` (Architecture specification for minimum safe foundation).
  - **Application Implementation**: `NOT IMPLEMENTED` (Baseline `dbd4153ca39ede69890ebaf47feaab7f393c3c5a` contains minimal activity shell `MainActivity.java` only).
  - **Security Claims**: `NOT VERIFIED` (No claims of security until code is implemented and verified by automated tests).
  - **Test Execution Status**: `NOT RUN` (0 automated tests executed at baseline).
- **Environment & Language**: Native Java 17, Android API level 26+ (`minSdk 26`, `targetSdk 35`, `compileSdk 35`), AGP 8.7.3.
- **Financial & Scope Boundaries**: $0 development cost. Strictly zero paid subscriptions, cloud APIs, or online account dependencies.
- **Data Policy**: Strictly 100% synthetic fictional test data. Real legal records remain **STRICTLY PROHIBITED** until encryption, key handling, backup, restore, and recovery are fully implemented and verified.
- **Deferred Nonessentials**: Defer AI integration, dynamic plugin execution, cloud sync engines, and advanced automation. Focus exclusively on the minimum viable local workspace foundation.

---

## 2. STORAGE AND ENCRYPTION (MINIMUM RECOMMENDED SOLUTION)

### 2.1 Recommended Architecture Default
- **Primary Database Storage**: Standard Android SDK SQLite (`android.database.sqlite`) paired with AES-256-GCM payload-level column/field encryption.
  - *Evaluation*: Uses standard Android OS APIs ($0 cost, 0MB added APK binary footprint, Apache 2.0 / AOSP licensed, 100% compatible with minSdk 26+ and Java 17).
  - *Trade-off*: Table structure and row counts are visible to root, but all sensitive legal content, notes, contacts, and metadata are AES-256-GCM encrypted payload blobs.
- **Imported Documents & Files**: Stored as individual AES-256-GCM encrypted files inside application-private storage (`context.getFilesDir() + "/evidence/"`).
- **Encryption Engine**: Java Cryptography Extension (JCE) standard `javax.crypto.Cipher` with `AES/GCM/NoPadding` (256-bit keys, 12-byte random IVs per encryption operation).

---

## 3. KEY MANAGEMENT AND REPLACEMENT-PHONE RECOVERY

### 3.1 Key Flow Architecture

```
[ Master Passphrase ] ──> PBKDF2WithHmacSHA256 (salt_passphrase.bin) ──> KEK_passphrase ──┐
                                                                                         ├──> unwraps ──> DEK (256-bit AES)
[ Android KeyStore TEE ] ──────────────────────────────────────────────> KEK_keystore ──┘                │
                                                                                                         v
[ 24-Word BIP39 Seed ] ──> PBKDF2WithHmacSHA512 (salt_recovery.bin) ────> KEK_recovery ───────────> Encrypted DB & Files
```

- **Data Encryption Key (DEK)**: Random 256-bit AES key generated locally on first setup. Encrypts local database records and documents.
- **Passphrase Wrapping (`KEK_passphrase`)**: Derived from user's Master Passphrase via `PBKDF2WithHmacSHA256` (100,000+ iterations). Unwraps `wrapped_dek_passphrase.bin`.
- **KeyStore Fast-Unlock (`KEK_keystore`)**: Generated inside Android KeyStore TEE (`"EliteKit_Keystore_KEK"`). Unwraps `wrapped_dek_keystore.bin` for biometric/PIN fast unlock.
- **Recovery Seed (`KEK_recovery`)**: Derived from 24-word BIP39 mnemonic seed via `PBKDF2WithHmacSHA512`. Unwraps `wrapped_dek_recovery.bin`.

### 3.2 Replacement-Phone Recovery Path (Zero KeyStore Reliance)
1. On clean replacement device (Instance B), the user enters their 24-word Recovery Seed.
2. Device B derives `KEK_recovery` using `PBKDF2WithHmacSHA512`.
3. Device B unwraps `wrapped_dek_recovery.bin` from the user's `.eltkbackup` export, recovering `DEK` directly.
4. Device B generates a new local KeyStore key `KEK_keystore` and re-wraps `DEK` for local fast unlock on Device B.
5. **Key Store Invalidation / Forgotten Credentials**:
   - If Master Passphrase is changed, `DEK` is re-wrapped into `wrapped_dek_passphrase.bin`. `DEK` itself is unchanged; historical backups remain valid.
   - If both Master Passphrase and 24-word Recovery Seed are lost, data is mathematically unrecoverable.

---

## 4. BACKUP AND RESTORE SPECIFICATION

### 4.1 Encrypted Backup Container (`.eltkbackup`)
- **Format**: User-controlled offline single-file backup container.
- **Header Structure**:
  - `[ 16 bytes: Magic Header "ELITEKIT_BKUP_V1" ]`
  - `[ 2 bytes: uint16 Format Version 0x0001 ]`
  - `[ 2 bytes: uint16 Cipher Suite ID (0x0001 = AES-256-GCM-HKDF-4KB) ]`
  - `[ 32 bytes: KDF Salt ]`
  - `[ 8 bytes: uint64 Payload Size ]`
  - `[ Stream: Chunked 4KB AES-256-GCM Ciphertext Segments + Tags ]`
- **Zero Paid Services**: Operates 100% offline via local storage or Android System Storage Access Framework (SAF). Zero automatic cloud upload dependencies.

### 4.2 Crash-Safe Transactional Restore Engine
1. **Verification**: Streams backup into `cache/restore_staging/`. Validates format version, header salt, and all chunk AEAD tags before touching active data.
2. **Disk Space Pre-check**: Confirms available storage is at least **2.5x** archive size (1.0x staging + 1.0x rollback snapshot + 0.5x safety buffer). If insufficient, restore aborts without modifying active data.
3. **Pre-Restore Snapshot**: Active database and files are copied to `files/rollback_backup/`. State `STATE_SNAPSHOT` is written to `restore_state.json` and flushed via `fsync()`.
4. **Activation & Recovery**: Staged data replaces active data (`files/data/`). If a crash or power loss occurs during activation, `Application.onCreate()` detects `STATE_ACTIVATING` upon restart and automatically restores `files/rollback_backup/` pre-restore snapshot intact. Damaged archives or failed restores never result in a silently wiped database.

---

## 5. MINIMUM SECURITY TEST MATRIX

*(Note: All tests below are PROPOSED specifications and marked **NOT RUN** at baseline SHA `dbd4153ca39ede69890ebaf47feaab7f393c3c5a`)*

| Test ID | Test Case Scope | Verification Execution Command | Minimum Acceptance Criteria | Status |
| :--- | :--- | :--- | :--- | :--- |
| **T1** | **Passphrase Authentication** | `./gradlew test --tests "*KeyManagerTest.testPassphrase*"` | Correct passphrase unwraps DEK; incorrect passphrase throws `GeneralSecurityException`. | **NOT RUN** |
| **T2** | **Ciphertext Confidentiality** | `./gradlew test --tests "*StorageTest.testCiphertext*"` | Stored payload on disk contains zero plaintext substrings; tamper bit-flip throws `AEADBadTagException`. | **NOT RUN** |
| **T3** | **Backup & Restore Round Trip** | `./gradlew test --tests "*BackupTest.testRoundTrip*"` | Exported `.eltkbackup` restored to fresh DB produces identical record SHA-256 hashes. | **NOT RUN** |
| **T4** | **Replacement-Phone Recovery** | `./gradlew connectedAndroidTest --tests "*RecoveryTest*"` | Fresh instance with empty KeyStore recovers workspace using 24-word seed. | **NOT RUN** |
| **T5** | **Corrupted Backup Safety** | `./gradlew test --tests "*BackupTest.testCorrupt*"` | Truncated backup rejected in staging; existing database remains 100% untouched. | **NOT RUN** |
| **T6** | **Interrupted Restore Crash Recovery** | `./gradlew connectedAndroidTest --tests "*RestoreCrashTest*"` | Simulated SIGKILL mid-restore auto-recovers pre-restore snapshot on application restart. | **NOT RUN** |

---

## 6. DECISION DISCIPLINE & BLOCKING QUESTIONS

### 6.1 Recommended Defaults (Established)
1. **Database Layer**: Standard SDK SQLite + AES-256-GCM payload encryption ($0 added cost, 0MB APK overhead).
2. **Streaming Backup**: Google Tink `AesGcmHkdfStreaming` (4KB chunks, proven peer-reviewed AOSP library).
3. **Recovery Seed**: 24-word BIP39 mnemonic with `PBKDF2WithHmacSHA512` key derivation.

### 6.2 Genuine Implementation Blockers (Requires Human Approval)
1. **Storage Engine Approval**: Confirm whether SDK SQLite payload encryption (recommended baseline) is approved or if SQLCipher (~12MB APK size increase for full-page encryption) is strictly required.
2. **Implementation Gate Authorization**: Explicit human authorization required to advance from Phase 2 (Documentation Checkpoint) to Phase 3 (Minimum Code Implementation).
