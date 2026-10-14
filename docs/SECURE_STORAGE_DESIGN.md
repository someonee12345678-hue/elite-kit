STATUS: PROPOSED DESIGN — NOT IMPLEMENTED
VERSION: 0.1
SCOPE: Android-native, local-first Elite Kit
COST LIMIT: $0
TEST DATA: Fictional only

# ELITE KIT — SECURE STORAGE AND RECOVERY DESIGN SPECIFICATION

Document Path: docs/SECURE_STORAGE_DESIGN.md
Authoritative Baseline: dbd4153ca39ede69890ebaf47feaab7f393c3c5a
Governing Authority: MASTER_CONTROL.md

---

## 1. Purpose and Current Security Status

- **Purpose**: Establish an exhaustive, reviewable security architecture specification for Elite Kit (`com.elitekit.app`), defining local data encryption, non-circular key management, zero-keystore replacement-phone recovery, AEAD backup export, crash-resilient atomic restore, and fictional-data automated test release gates.
- **Current Security Status**: **PROPOSED DESIGN — NOT IMPLEMENTED**. At baseline `dbd4153ca39ede69890ebaf47feaab7f393c3c5a`, the codebase contains a minimal native Java activity shell (`MainActivity.java`). Encrypted database tables, key management mechanisms, backup generators, restore engines, and recovery seed modules are **NOT IMPLEMENTED**.
- **Data Policy**: Strictly 100% synthetic fictional test data during all design and development phases. Real confidential legal records remain **STRICTLY PROHIBITED** until all security capabilities and release gates pass automated verification in CI.

---

## 2. Threat Model and Explicit Limitations

### 2.1 Threat Vectors and Security Boundaries
1. **Loss or Theft of the Phone**: Physical extraction of device flash storage.
   - *Boundary*: Payload encryption (`DEK`) via AES-256-GCM. Unwrapping `DEK` requires user Master Passphrase or TEE-backed biometric unlock (`KEK_keystore`).
2. **Unauthorized Access to Application Files**: Local untrusted app or shared user inspecting private app storage.
   - *Boundary*: Android OS app sandboxing (`context.getFilesDir()`) combined with AES-256 payload encryption.
3. **Backup-File Exposure**: Interception or disclosure of exported `.eltkbackup` archives.
   - *Boundary*: AEAD authenticated encryption (`K_backup`) derived from user credentials; zero plaintext payload exported.
4. **Incorrect Passphrase or Recovery Material**: Attacker or user entering mismatched passphrases or seed words.
   - *Boundary*: Fails closed. Mismatched credentials trigger `GeneralSecurityException` without decrypting keys or altering wrapped key files.
5. **Lost Device or Replacement Phone**: Phone destroyed or user migrating to a clean new device.
   - *Boundary*: Pure credential-derived unwrapping (`KEK_recovery` or `KEK_passphrase`) without dependence on the old device's KeyStore TEE.
6. **Interrupted Backup or Restore**: Power loss or crash during database write or restore swap.
   - *Boundary*: Atomic staging in `cache/restore_staging/`, pre-restore snapshotting in `files/rollback_backup/`, and `restore_state.json` startup rollback.
7. **Corruption, Rollback, or Partial Deletion**: Altered ciphertext bytes, truncated archives, or replaying older valid database files.
   - *Boundary*: Additional Authenticated Data (`AAD`) header binding and 128-bit AEAD tag verification before disk extraction.
8. **Malicious or Malformed Imported Files**: Crafted backup files designed to exploit path traversal or parser vulnerabilities.
   - *Boundary*: Strict zip path sanitation (blocking `../` entries), schema version validation, and manifest SHA-256 integrity verification before disk extraction.
9. **Accidental Entry of Real Legal Records Before Security Is Verified**: Operator attempting to import or record confidential client files on an unverified build.
   - *Boundary*: Explicit release gates blocking real legal records until automated security test suites pass 100% in CI.

### 2.2 Explicit Limitations
- The application **CANNOT** resist an OS-level kernel compromise, active spyware, keyloggers, or an unlocked physical device with active root access.
- In a 100% offline single-device environment, preventing file-system replay/rollback attacks cannot be guaranteed solely by cryptographic hash chains; launch sequence counters and audit journals serve as practical mitigations.

---

## 3. Data Classification and Storage Boundaries

1. **Structured Records (SQLite)**: Client details, matter metadata, case notes, and timeline events are stored in SQLite (`workspace.db`). Textual and binary fields are encrypted using field/column-level AES-256-GCM.
2. **Imported Evidence Media (Files)**: Documents (PDFs), images (PNG/JPG), and audio recordings are stored in private internal storage (`context.getFilesDir()`). Stream-encrypted on write via `CipherOutputStream` (`AES/GCM/NoPadding`).
3. **Evidence Immutability**: Imported evidence files are written once to write-once encrypted containers. Modifications create versioned child records; original evidence files are never modified or overwritten.
4. **Sandboxing**: All database and media storage remains confined strictly within private app sandboxed storage (`context.getFilesDir()`).

---

## 4. Proposed Encrypted-Storage Architecture

- **Local-First Focus**: Zero reliance on remote servers, paid AI APIs, cloud subscriptions, or online account infrastructures ($0 ongoing cost).
- **Architecture**:
  - `EncryptedStorageRepository`: Java interface abstracting data persistence and payload encryption/decryption operations.
  - `KeyManagementEngine`: Java engine managing `DEK` generation, key wrapping (`KEK_passphrase`, `KEK_keystore`, `KEK_recovery`), and passphrase derivation.
  - `BackupRestoreEngine`: Java engine executing AEAD container packaging, SAF export, atomic staging, and crash-resilient rollback recovery.

---

## 5. Encryption Algorithm and Authenticated-Encryption Requirements

1. **Primitive Choice**: Standard Java JCE `javax.crypto.Cipher` implementing `AES/GCM/NoPadding`.
2. **Key Length**: 256-bit AES symmetric keys generated via `java.security.SecureRandom`.
3. **Authentication Tags**: 128-bit GCM authentication tags (`GCMParameterSpec(128, iv)`).
4. **Nonce / IV Uniqueness**: 12-byte (96-bit) cryptographically random IV generated fresh per encryption cycle via `SecureRandom`. IVs are prepended to ciphertext streams and never reused under the same key.
5. **Additional Authenticated Data (AAD)**: Header metadata (magic string, schema version, timestamp) is bound to the GCM cipher via `Cipher.updateAAD()`, enforcing header integrity prior to decryption.

---

## 6. Data-Encryption Key (DEK) and Key-Encryption-Key (KEK) Separation

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

### Key Lifecycle & Separation Rules
- **`DEK` (Data Encryption Key)**: 256-bit random AES key encrypting database payloads and media files directly. Never stored in plaintext. Encrypted as `wrapped_dek_passphrase.bin`, `wrapped_dek_keystore.bin`, and `wrapped_dek_recovery.bin`.
- **`KEK_passphrase`**: Derived from Master Passphrase + 16-byte random salt via `PBKDF2WithHmacSHA256`. Transient in-memory `SecretKey`.
- **`KEK_keystore`**: Non-exportable TEE key inside `AndroidKeyStore`. Biometric unlock gate.
- **`KEK_recovery`**: Derived from BIP39 Mnemonic Seed + salt via `PBKDF2`. Unwraps `DEK` on replacement devices.
- **`K_backup`**: Derived fresh per export via HKDF-Expand from `KEK_passphrase` or `KEK_recovery`.

---

## 7. Passphrase Handling and Password-Based Key Derivation

1. **Algorithm**: `PBKDF2WithHmacSHA256` (Standard Java JCE `SecretKeyFactory`).
2. **Salt**: 16 bytes (128 bits) fresh random salt (`SecureRandom`) saved in `kdf_params.json`.
3. **Dynamic Setup Benchmarking**: To balance brute-force attack resistance with user unlock responsiveness, setup executes a test derivation of 10,000 iterations, measures wall-clock time (`System.nanoTime()`), and calculates iterations required to achieve a **350 ms unlock latency** target.
4. **OWASP Bounds**: Enforces a strict minimum of **100,000 iterations** (OWASP mobile baseline floor) and maximum of **1,000,000 iterations**.

---

## 8. Android Keystore's Role and Limitations

- **Role**: `KEK_keystore` provides fast hardware-backed biometric unlock on the local device without requiring the user to re-enter their Master Passphrase for every session.
- **Explicit Limitations**: Android KeyStore keys are hardware-bound and **NON-EXPORTABLE**. They do not transfer or migrate to replacement devices during device migration or cloud restores.
- **Cross-Device Guarantee**: Replacement-phone recovery relies **0%** on `KEK_keystore` and 100% on credential-derived keys (`KEK_recovery` or `KEK_passphrase`).

---

## 9. Replacement-Phone Recovery Design

1. User installs clean app on replacement device (Instance B).
2. User inputs Master Passphrase OR 12/24-word BIP39 Recovery Seed.
3. App derives `KEK_passphrase` or `KEK_recovery` on Instance B.
4. App unwraps `wrapped_dek_recovery.bin` or decrypts `.eltkbackup` container to recover `DEK`.
5. App generates a new local `KEK_keystore` inside Instance B's `AndroidKeyStore`.
6. App re-wraps `DEK` locally as `wrapped_dek_keystore.bin` on Instance B.

---

## 10. Encrypted Backup Format and User-Controlled Export

1. **Export Protocol**: Explicit user-triggered export via Storage Access Framework (SAF).
2. **Container Layout (`.eltkbackup`)**:
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

## 11. Restore Validation, Staging, Atomic Replacement, and Failure Handling

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

- **Validation**: AEAD authentication tag, zip entry path safety (`../` blocking), and manifest SHA-256 digests are verified in `STATE_STAGING` prior to touching active database or file directories.
- **Atomic Replacement**: Active workspace (`files/data/`) is snapshotted to `files/rollback_backup/` and tracked with `restore_state.json` synced via `fsync()`.
- **Startup Crash Recovery**: `Application.onCreate()` checks `restore_state.json`. If incomplete, it automatically restores `files/rollback_backup/` over `files/data/` before launching UI.

---

## 12. Database/File Consistency and Schema Versioning

1. **Transactional DB Writes**: All SQLite write operations execute inside explicit transactions (`beginTransaction()`, `setTransactionSuccessful()`).
2. **Atomic File Swaps**: Media file updates use temporary file write -> `fsync()` -> atomic file rename patterns.
3. **Schema Versioning**: SQLite database maintains explicit `PRAGMA user_version`. Migrations execute deterministically inside transactions with pre-migration snapshotting.

---

## 13. Key Rotation, Passphrase Changes, and Recovery-Material Replacement

1. **Passphrase Rotation**: Old passphrase unwraps `DEK` -> new `KEK_passphrase` derived -> `DEK` re-wrapped as `wrapped_dek_passphrase.bin`. Database payloads remain untouched.
2. **Recovery Seed Regeneration**: User generates new seed -> unwraps `DEK` -> derives new `KEK_recovery` -> re-wraps `DEK` as `wrapped_dek_recovery.bin`.
3. **Biometric Key Invalidation**: If Android OS invalidates KeyStore biometrics, user re-enters Master Passphrase to regenerate `KEK_keystore` and re-wrap `wrapped_dek_keystore.bin`.

---

## 14. Locking, Memory Handling, and Sensitive-Data Exposure

1. **Passphrase Memory Zeroing**: Passphrases in memory strictly as `char[]` and zeroed out (`Arrays.fill(pwd, '\0')`) immediately following KDF derivation.
2. **Key Memory Zeroing**: Symmetric key bytes in memory are overwritten with zero bytes (`Arrays.fill(keyBytes, (byte)0)`) upon workspace lock or closure.
3. **Leakage Prevention**: Plaintext keys, passphrases, and legal payloads are strictly prohibited from log output (`android.util.Log`), screenshots (`FLAG_SECURE`), preferences, or temporary unencrypted files.

---

## 15. Threats Involving Backup Theft, Wrong Passwords, Corrupted Files, Interrupted Writes, Rollback, and Lost Recovery Material

- **Backup Theft**: Archive encrypted under `K_backup` (AES-256-GCM); attacker without passphrase/seed cannot extract payload.
- **Wrong Password**: Mismatched KDF output fails AEAD tag decryption (`AEADBadTagException`); fails closed without corrupting stored wrapped keys.
- **Corrupted Files**: Bit-flips trigger `AEADBadTagException` during `STATE_STAGING`; restore aborts without modifying active workspace.
- **Interrupted Writes**: DB transaction aborts; file writes use temp-file `fsync` swap; active files remain pristine.
- **Rollback Attacks**: Sequence counters and Workspace Timeline Audit Journals record state lineage; modal approval alerts operator of timestamp discrepancies.
- **Lost Recovery Material**: Fails closed. If passphrase and recovery seed are lost, data is unrecoverable (zero backdoor).

---

## 16. Required Unit, Integration, and Failure-Injection Tests

| Test Case | Test Scope | Execution Script / Command | Expected Behavior |
| :--- | :--- | :--- | :--- |
| **Passphrase Unwrapping** | Unit (CI) | `./gradlew test --tests "*PassphraseKeyTest*"` | Valid passphrase unwraps `DEK`. Invalid passphrase throws `GeneralSecurityException`. |
| **Ciphertext Tampering** | Unit (CI) | `./gradlew test --tests "*AeadIntegrityTest*"` | Single bit-flip in ciphertext/AAD header throws `AEADBadTagException` and halts read. |
| **Wrong Recovery Material** | Unit (CI) | `./gradlew test --tests "*RecoverySeedTest*"` | Mismatched recovery seed throws `GeneralSecurityException` without corrupting active keys. |
| **Nonce Uniqueness** | Unit (CI) | `./gradlew test --tests "*NonceUniquenessTest*"` | 100,000 encryption cycles produce zero duplicate 12-byte IVs. |
| **Truncated / Corrupt Backups** | Integration (CI) | `./gradlew test --tests "*CorruptBackupTest*"` | Malformed archive rejected in `STATE_STAGING`; active database 100% unmodified. |
| **Mid-Restore Crash Rollback** | Instrumentation (Device) | `./gradlew connectedAndroidTest` | Process killed mid-restore; restart automatically restores `files/rollback_backup/` pre-restore snapshot intact. |
| **Replacement-Phone Recovery** | Instrumentation (Device) | `./gradlew connectedAndroidTest` | Clean app instance without KeyStore key recovers complete workspace using 12/24-word seed. |
| **Storage Full Failure Injection** | Integration (CI) | `./gradlew test --tests "*DiskFullTest*"` | Disk full error aborts write transaction without corrupting database headers. |

---

## 17. Acceptance Criteria Before Real Legal Records May Be Permitted

Real legal records remain **STRICTLY PROHIBITED** until all four release gates pass automated verification:
- **Gate A**: Key management and PBKDF2 derivation engine implemented and verified passing in CI.
- **Gate B**: Encrypted database and media file storage repository implemented and verified passing in CI.
- **Gate C**: AEAD backup exporter and crash-resilient 9-state restore engine implemented and verified passing in CI.
- **Gate D**: Complete end-to-end recovery test suite passing on physical Android test devices with 100% fictional test data.

---

## 18. Implementation Sequence and Rollback Considerations

1. **Increment 1**: Gradle 8.9 Wrapper Setup & Local Build Harness (`./gradlew test`).
2. **Increment 2**: Cryptographic Key Hierarchy & PBKDF2 Derivation Engine (`crypto/`).
3. **Increment 3**: Encrypted Database & Media Storage Repository (`storage/`).
4. **Increment 4**: AEAD Encrypted Backup Package Exporter (`backup/`).
5. **Increment 5**: Transactional Crash-Resilient Restore Engine (`restore/`).
6. **Increment 6**: BIP39 Replacement-Phone Recovery Workflow (`recovery/`).
7. **Increment 7**: Security CI Test Suite Integration (`ci/`).

---

## 19. Unresolved Decisions Requiring Explicit Approval [UNRESOLVED]

1. **Storage Engine Choice**: Standard Android SDK SQLite (`android.database.sqlite`) with payload AEAD encryption vs. SQLCipher for Android (`net.zetetic:android-database-sqlcipher`).
2. **Recovery Seed Derivation Standardization**: Standard BIP39 key derivation (`PBKDF2WithHmacSHA512`, 2048 iterations) vs. custom PBKDF2/HKDF key wrapping scheme.
3. **Password KDF Parameter Benchmarking**: Calibrating PBKDF2/Argon2 iteration parameters via setup benchmark loop on target mobile hardware.
4. **Recovery Mnemonic Entropy**: 12-word recovery seed (128-bit entropy) vs. 24-word recovery seed (256-bit entropy).
5. **Rollback Attack Defense Mechanism**: User-confirmed launch state timestamps vs. out-of-band sequence numbers.

---

## 20. Security Claims That Must Not Be Made Without Evidence

- **"Encrypted Storage Operational"**: Must NOT be claimed until `EncryptedStorageRepository` unit and integration tests pass in CI.
- **"Replacement-Phone Recovery Operational"**: Must NOT be claimed until clean-instance recovery tests pass on physical Android devices.
- **"Crash-Safe Restore Verified"**: Must NOT be claimed until mid-restore process termination failure-injection tests pass in instrumentation.
- **"Production-Ready Legal Workspace"**: Must NOT be claimed until Release Gates A through D pass 100% and independent security review is completed.
