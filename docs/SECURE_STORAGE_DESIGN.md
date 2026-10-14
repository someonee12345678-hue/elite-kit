STATUS: PROPOSED DESIGN
IMPLEMENTATION: NOT IMPLEMENTED
SECURITY VALIDATION: NOT COMPLETED

# ELITE KIT — SECURE STORAGE AND RECOVERY DESIGN SPECIFICATION

Document Path: docs/SECURE_STORAGE_DESIGN.md
Authoritative Baseline: dbd4153ca39ede69890ebaf47feaab7f393c3c5a
Governing Authority: MASTER_CONTROL.md

---

## 1. PURPOSE AND SCOPE

- **Purpose**: Define an exhaustive security architecture specification for local data encryption, non-circular key management, zero-keystore replacement-phone recovery, AEAD backup export, crash-resilient atomic restore, and fictional-data release gates.
- **Current Security Status**: **PROPOSED DESIGN — NOT IMPLEMENTED — NOT SECURITY VALIDATED**. At baseline `dbd4153ca39ede69890ebaf47feaab7f393c3c5a`, the codebase contains a minimal native Java activity shell (`MainActivity.java`). Encrypted database tables, key management mechanisms, backup generators, restore engines, and recovery seed modules are **NOT IMPLEMENTED**.
- **Scope**: Local-first Android native legal workspace (`com.elitekit.app`), Java 17, Android minSdk 26, targetSdk 35, compileSdk 35, AGP 8.7.3.
- **Financial Boundary**: $0 ongoing development cost; zero dependencies on paid APIs, cloud subscriptions, or online account infrastructures.
- **Fictional Test Data Policy**: Strictly 100% synthetic fictional test data during all design and development phases. Real confidential legal records remain **STRICTLY PROHIBITED** until all security capabilities, key management systems, backup, restore, and replacement-phone recovery pass automated CI verification.

---

## 2. THREAT MODEL AND SECURITY BOUNDARIES

### 2.1 Threat Vectors and Security Boundaries
1. **Device Theft or Physical Extraction**: Physical extraction of device flash storage.
   - *Boundary*: Payload encryption (`DEK`) via AES-256-GCM. Unwrapping `DEK` requires user Master Passphrase or TEE-backed biometric unlock (`KEK_keystore`).
2. **Unauthorized Local App or File Access**: Co-located untrusted app or shared device user inspecting private storage.
   - *Boundary*: Android OS app sandboxing (`context.getFilesDir()`) combined with AES-256 payload encryption.
3. **Application-Data Extraction / Backup Exposure**: Interception or disclosure of exported `.eltkbackup` archives.
   - *Boundary*: AEAD authenticated encryption (`K_backup`) derived from user credentials; zero plaintext payload exported.
4. **Lost Devices or Passphrase Guessing**: Attacker or user entering mismatched passphrases or seed words.
   - *Boundary*: Fails closed. Mismatched credentials trigger `GeneralSecurityException` without decrypting keys or altering wrapped key files.
5. **Corrupted, Truncated, Replaced, or Malicious Backups**: Altered ciphertext, truncated zip archives, or injected entries.
   - *Boundary*: Additional Authenticated Data (`AAD`) header binding, 128-bit AEAD tag verification, zip path sanitation (`../` blocking), and manifest SHA-256 verification before disk extraction.
6. **Interrupted Restores and Failed Migrations**: Power loss or crash during database write or restore swap.
   - *Boundary*: Atomic staging in `cache/restore_staging/`, pre-restore snapshotting in `files/rollback_backup/`, and `restore_state.json` startup rollback.
7. **Accidental Deletion and Event History Rollback**: Replaying an older valid database file to restore deleted entries or truncating database state.
   - *Boundary*: Monotonically increasing launch sequence counters in KeyStore preferences and Workspace Timeline Audit Journals inside the encrypted database.

### 2.2 Explicit Limitations
- The application **CANNOT** guarantee protection against an OS-level kernel compromise, active spyware, keyloggers, or a fully unlocked physical device with active root access.
- In a 100% offline single-device environment, detecting or preventing a file-system state replay attack cannot be guaranteed solely by cryptographic hash chains; launch sequence counters and audit journals serve as practical mitigations.

---

## 3. DATABASE AND IMPORTED FILE ENCRYPTION

1. **Structured Database Records (SQLite)**: Client details, matter metadata, case notes, and timeline events are stored in SQLite (`workspace.db`). Textual and binary payload fields are encrypted using field/column-level AES-256-GCM (`AES/GCM/NoPadding`).
2. **Imported Evidence Media (Files)**: Documents (PDFs), images (PNG/JPG), and audio recordings are stored in private internal storage (`context.getFilesDir()`). Stream-encrypted on write via `CipherOutputStream` (`AES/GCM/NoPadding`).
3. **Evidence Immutability**: Imported evidence files are written once to write-once encrypted containers. Modifications create versioned child records; original evidence files are never modified or overwritten.
4. **Sandboxing**: All database and media storage remains confined strictly within private app sandboxed storage (`context.getFilesDir()`).

---

## 4. PROPOSED KEY HIERARCHY AND ANDROID KEYSTORE ROLE

```
                                  +-----------------------------------+
                                  |     Master Passphrase (user)      |
                                  +-----------------------------------+
                                                    |
                                                    v  PBKDF2WithHmacSHA256 (salt_passphrase.bin)
                                          +-------------------+
                                          |  KEK_passphrase   |
                                          +-------------------+
                                                    |  unwraps
                                                    v
  +-----------------------+               +-------------------+               +-----------------------+
  |  BIP39 Recovery Seed  | --PBKDF2----> |   KEK_recovery    | ----unwraps-> | Workspace Data Key    |
  +-----------------------+               +-------------------+               |        (DEK)          |
                                                    |                         |   (256-bit AES-GCM)   |
  +-----------------------+               +-------------------+               +-----------------------+
  | Android KeyStore TEE  | ------------> |   KEK_keystore    | ----unwraps->             |
  +-----------------------+               +-------------------+                           | encrypts
                                                                                          v
                                                                              +-----------------------+
                                                                              | SQLite DB & Evidence  |
                                                                              |  (context.getFiles()) |
                                                                              +-----------------------+
```

### Key Separation & Lifecycle Matrix
1. **`DEK` (Data Encryption Key)**: 256-bit random AES key (`SecureRandom`) encrypting database records and media files directly. Never written to disk in unencrypted plaintext. Encrypted as `wrapped_dek_passphrase.bin`, `wrapped_dek_keystore.bin`, and `wrapped_dek_recovery.bin`. Overwritten in memory with zero bytes (`Arrays.fill(keyBytes, (byte)0)`) upon lock.
2. **`KEK_passphrase`**: Derived from Master Passphrase + 16-byte random salt via `PBKDF2WithHmacSHA256` ($\ge 100,000$ iterations floor). Transient in-memory `SecretKey`. Zeroed out immediately after use (`Arrays.fill(pwd, '\0')`). Never use raw passphrases directly as AES keys.
3. **`KEK_keystore`**: Non-exportable hardware TEE key inside `AndroidKeyStore` alias `"EliteKit_Keystore_KEK"`. Biometric unlock gate on local phone. **Cannot migrate to replacement phone.**
4. **`KEK_recovery`**: Derived from 12/24-word BIP39 Mnemonic Seed + salt via `PBKDF2`. Unwraps `DEK` on replacement devices.
5. **`K_backup`**: Derived fresh per export via HKDF-Expand from `KEK_passphrase` or `KEK_recovery` with info label `"EliteKit-BackupPayloadKey-v1"`.

---

## 5. PASSPHRASE HANDLING AND REPLACEMENT-PHONE RECOVERY

1. **User-Controlled Recovery Seed**: Workspace creation generates a 12/24-word BIP39 mnemonic seed.
2. **Replacement Phone Procedure**:
   - User installs clean app on Instance B (zero KeyStore keys or local state).
   - User enters Master Passphrase or 12/24-word recovery seed into Instance B.
   - Instance B derives `KEK_recovery` or `KEK_passphrase` in local memory.
   - Instance B unwraps `wrapped_dek_recovery.bin` or decrypts `.eltkbackup` archive to recover `DEK`.
   - Instance B generates a new local `KEK_keystore` inside Instance B's `AndroidKeyStore`.
   - Instance B re-wraps `DEK` locally as `wrapped_dek_keystore.bin`.
3. **Zero-KeyStore Dependence**: Recovery relies 0% on the old phone's TEE hardware.

---

## 6. RECOVERY MATERIAL AND ENCRYPTED BACKUP DESIGN

1. **User-Controlled SAF Export**: Export triggered explicitly by user via Storage Access Framework (SAF) document picker. Zero background cloud sync.
2. **AEAD Backup Envelope Format (`.eltkbackup`)**:
   ```
   [ 16 bytes: Magic Header "ELITEKIT_BACKUP1" ]
   [ 16 bytes: KDF Salt ]
   [ 12 bytes: AEAD Initialization Vector (IV) ]
   [ Variable: AEAD Encrypted Payload Stream (AES-256-GCM) ]
      ├── manifest.json (Schema version, creation timestamp, file inventory, SHA-256 digests)
      ├── database_export.db (Encrypted SQLite database table records)
      └── evidence_files/ (AES-256-GCM stream-encrypted media files)
   [ 16 bytes: AEAD GCM Authentication Tag ]
   ```

---

## 7. RESTORE VALIDATION, ATOMICITY, AND FAILURE HANDLING

```
                      +-------------------+
                      |   STATE_IDLE      |
                      +-------------------+
                                |
                                v (User approves restore)
                      +-------------------+
                      |   STATE_STAGING   | --> Unpack archive to cache/restore_staging/
                      +-------------------+
                                |
                                v (AEAD Tag & Manifest verified)
                      +-------------------+
                      |  STATE_SNAPSHOT   | --> Copy files/data/ to files/rollback_backup/
                      +-------------------+
                                |
                                v (fsync restore_state.json)
                      +-------------------+
                      | STATE_ACTIVATING  | --> Swap cache/restore_staging/ to files/data/
                      +-------------------+
                                |
                    +-----------+-----------+
                    | (Success)             | (Crash or Failure)
                    v                       v
          +-------------------+   +-------------------+
          |  STATE_COMPLETE   |   |  STATE_ROLLBACK   | --> Restore files/rollback_backup/
          +-------------------+   +-------------------+
                    |                       |
                    +-----------+-----------+
                                |
                                v
                      +-------------------+
                      |   STATE_CLEANUP   | --> Delete staging & snapshot dirs
                      +-------------------+
```

- **Validation**: AEAD authentication tag, zip path safety (`../` blocking), manifest SHA-256 digests, and schema version are verified in `STATE_STAGING` prior to touching active database or file directories.
- **Atomic Replacement**: Active workspace (`files/data/`) is snapshotted to `files/rollback_backup/` and tracked with `restore_state.json` synced via `FileOutputStream.getFD().sync()`.
- **Startup Crash Recovery Check**: `Application.onCreate()` checks `restore_state.json`. If incomplete, automatically restores `files/rollback_backup/` pre-restore snapshot intact.

---

## 8. PARTIAL WRITE PROTECTION AND DATA LOSS CONTROLS

1. **Transactional DB Writes**: All SQLite write operations execute inside explicit transactions (`beginTransaction()`, `setTransactionSuccessful()`).
2. **Atomic File Swaps**: Media file updates use temporary file write -> `fsync()` -> atomic file rename patterns.
3. **Data Preservation**: Existing workspace records are never silently deleted or replaced. Failed migrations or restores automatically revert to the pre-operation snapshot.
4. **Limits of Hashes**: SHA-256 digests provide bit-rot and file integrity detection only. They do not constitute proof against authorized rollback or record deletion.

---

## 9. FICTIONAL-DATA-ONLY TESTING REQUIREMENTS

Real legal records remain **STRICTLY PROHIBITED** until 100% of the following automated unit, integration, and instrumentation tests pass in CI using synthetic test fixtures:

| Test Case | Scope | Execution Command / Script | Acceptance Criteria |
| :--- | :--- | :--- | :--- |
| **Correct & Incorrect Passphrases** | Unit (CI) | `./gradlew test --tests "*PassphraseKeyTest*"` | Valid passphrase unwraps `DEK`. Invalid passphrase throws `GeneralSecurityException` without modifying wrapped key files. |
| **Ciphertext Tampering** | Unit (CI) | `./gradlew test --tests "*AeadIntegrityTest*"` | Single bit-flip in ciphertext or AAD header throws `AEADBadTagException` and halts read operation. |
| **Wrong Recovery Material** | Unit (CI) | `./gradlew test --tests "*RecoverySeedTest*"` | Mismatched recovery seed throws `GeneralSecurityException` without corrupting active keys. |
| **Nonce Uniqueness** | Unit (CI) | `./gradlew test --tests "*NonceUniquenessTest*"` | 100,000 encryption cycles produce zero duplicate 12-byte IVs. |
| **Truncated / Malformed Backups** | Integration (CI) | `./gradlew test --tests "*CorruptBackupTest*"` | Malformed archive rejected in `STATE_STAGING`; active database 100% unmodified. |
| **Interrupted Export / Restore** | Instrumentation (Device) | `./gradlew connectedAndroidTest` | Process killed mid-restore; restart automatically restores `files/rollback_backup/` pre-restore snapshot intact. |
| **Replacement-Phone Recovery** | Instrumentation (Device) | `./gradlew connectedAndroidTest` | Clean app instance without KeyStore key recovers complete workspace using 12/24-word seed. |
| **Storage Full Condition** | Integration (CI) | `./gradlew test --tests "*DiskFullTest*"` | Disk full error aborts write transaction without corrupting database headers. |

---

## 10. ACCEPTANCE CRITERIA BEFORE REAL LEGAL RECORDS ARE ALLOWED

Real legal records remain strictly prohibited until all release gates pass automated verification:

- **Gate A**: Architecture design approval by maintainer.
- **Gate B**: Key management and PBKDF2 derivation engine implemented and passing unit tests in CI.
- **Gate C**: Encrypted database and media file storage repository implemented and passing CI.
- **Gate D**: AEAD backup exporter and crash-resilient 9-state restore engine implemented and passing CI.
- **Gate E**: Replacement-phone recovery workflow passing on physical Android devices.
- **Gate F**: Failure and corruption injection test suite passing 100% in CI.
- **Gate G**: Independent security design review completed.
- **Gate H**: Explicit maintainer sign-off lifting prohibition for real legal records.

---

## 11. UNRESOLVED DECISIONS AND IMPLEMENTATION DEPENDENCIES [UNRESOLVED]

1. **Storage Engine Choice**: Standard Android SDK SQLite (`android.database.sqlite`) with payload AEAD encryption vs. SQLCipher for Android (`net.zetetic:android-database-sqlcipher`).
2. **Recovery Seed Derivation Standardization**: Standard BIP39 key derivation (`PBKDF2WithHmacSHA512`, 2048 iterations) vs. custom PBKDF2/HKDF key wrapping scheme.
3. **Password KDF Parameter Benchmarking**: Calibrating PBKDF2/Argon2 iteration parameters via setup benchmark loop on target mobile hardware.
4. **Recovery Mnemonic Entropy**: 12-word recovery seed (128-bit entropy) vs. 24-word recovery seed (256-bit entropy).
5. **Rollback Attack Defense Mechanism**: User-confirmed launch state timestamps vs. out-of-band sequence numbers.

---

## 12. STAGED IMPLEMENTATION AND VERIFICATION PLAN

1. **Increment 1**: Gradle 8.9 Wrapper Setup & Local Build Harness (`./gradlew test`).
2. **Increment 2**: Cryptographic Key Hierarchy & PBKDF2 Derivation Engine (`crypto/`).
3. **Increment 3**: Encrypted Database & Media Storage Repository (`storage/`).
4. **Increment 4**: AEAD Encrypted Backup Package Exporter (`backup/`).
5. **Increment 5**: Transactional Crash-Resilient Restore Engine (`restore/`).
6. **Increment 6**: BIP39 Replacement-Phone Recovery Workflow (`recovery/`).
7. **Increment 7**: Security CI Test Suite Integration (`ci/`).
