STATUS: PROPOSED DESIGN — NOT IMPLEMENTED OR SECURITY-VALIDATED

# ELITE KIT — SECURE STORAGE AND RECOVERY DESIGN SPECIFICATION

Document Path: docs/SECURE_STORAGE_DESIGN.md
Authoritative Baseline: dbd4153ca39ede69890ebaf47feaab7f393c3c5a
Governing Authority: MASTER_CONTROL.md

---

## 1. STATUS AND SCOPE

- **Status**: **PROPOSED DESIGN — NOT IMPLEMENTED OR SECURITY-VALIDATED**. At baseline `dbd4153ca39ede69890ebaf47feaab7f393c3c5a`, the codebase contains a minimal native Java activity shell (`MainActivity.java`). Encrypted database tables, key management mechanisms, backup generators, restore engines, and recovery seed modules are **NOT IMPLEMENTED**.
- **Scope**: Local-first Android native legal workspace (`com.elitekit.app`), Java 17, Android minSdk 26, targetSdk 35, compileSdk 35, AGP 8.7.3.
- **Financial Boundary**: $0 ongoing development cost; zero dependencies on paid APIs, cloud subscriptions, or online account infrastructures.
- **Fictional Test Data Policy**: Strictly 100% synthetic fictional test data during all design and development phases. Real confidential legal records remain **STRICTLY PROHIBITED** until all security capabilities, key management systems, backup, restore, and replacement-phone recovery pass automated CI verification.
- **Preservation Boundaries**: `main` branch, application source code (`app/src/main/java/com/elitekit/app/MainActivity.java`), build configurations (`*.gradle.kts`), and both existing GitHub Actions workflows (`.github/workflows/main.yml` and `.github/workflows/android-smoke-test.yml`) remain 100% untouched.

---

## 2. THREAT MODEL AND EXPLICIT LIMITATIONS

### 2.1 Threat Vectors and Security Boundaries
1. **Device Theft or Physical Extraction**: Physical extraction of device flash storage.
   - *Boundary*: Storage payload (`DEK`) is encrypted using AES-256-GCM. Unwrapping `DEK` requires user Master Passphrase or TEE-backed biometric unlock (`KEK_keystore`).
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

## 3. STORAGE ARCHITECTURE EVALUATION

| Evaluation Dimension | Approach A: Standard Android SQLite + AEAD Payload Encryption (`android.database.sqlite`) | Approach B: SQLCipher for Android (`net.zetetic:android-database-sqlcipher`) | Approach C: Encrypted File Format |
| :--- | :--- | :--- | :--- |
| **Dependencies & Financial Cost** | **$0 added dependencies.** Built directly into Android SDK. Zero third-party library maintenance risk. | **$0 open-source library.** Introduces third-party dependency maintenance requirement. | **$0 added dependencies.** Custom file layout. |
| **Binary Footprint** | **0 MB increase.** Zero native JNI binaries added to APK. | **~12 MB increase.** Requires bundling native JNI binaries (`libsqlcipher.so`). | **0 MB increase.** Custom Java stream code. |
| **Metadata Exposure Risks** | Structural metadata visible (headers, table/column names, row counts). Field payloads 100% AES-256-GCM encrypted. | **Zero metadata exposure.** 100% of SQLite database pages encrypted on disk via AES-256-CBC / HMAC. | Custom metadata header layout required. High parsing risk. |
| **Transactions & Migration** | Native SQLite transactions (`beginTransaction()`) and schema migrations (`PRAGMA user_version`). | Full SQL transaction support; requires custom page migration handling. | Custom atomic file write -> `fsync()` -> rename required per record. No SQL queries. |
| **Recommendation & Trade-Off** | **PROPOSED BASELINE**: Standard Android SQLite + AEAD Payload Encryption provides zero dependency risk and 0MB footprint while fully protecting sensitive content payloads. Abstracted behind `EncryptedStorageRepository` interface. | **UNRESOLVED CHOICE**: Pending explicit user selection if full page header obfuscation becomes mandatory. | **REJECTED**: High custom parser complexity and lack of SQL transactional guarantees. |

---

## 4. KEY HIERARCHY

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
2. **`KEK_passphrase`**: Derived from Master Passphrase + 16-byte random salt via `PBKDF2WithHmacSHA256`. Transient in-memory `SecretKey`. Zeroed out immediately after use.
3. **`KEK_keystore`**: Non-exportable hardware TEE key inside `AndroidKeyStore` alias `"EliteKit_Keystore_KEK"`. Biometric unlock gate. **Zero involvement in replacement-phone recovery.**
4. **`KEK_recovery`**: Derived from 12/24-word BIP39 Mnemonic Seed + salt via `PBKDF2`. Unwraps `DEK` on replacement devices.
5. **`K_backup`**: Derived fresh per export via HKDF-Expand from `KEK_passphrase` or `KEK_recovery` with info label `"EliteKit-BackupPayloadKey-v1"`.

---

## 5. CRYPTOGRAPHY

1. **Primitives**: Standard Java JCE `javax.crypto.Cipher` implementing `AES/GCM/NoPadding`.
2. **Key Length**: 256-bit AES symmetric keys generated via `java.security.SecureRandom`.
3. **Authentication Tags**: 128-bit GCM authentication tags (`GCMParameterSpec(128, iv)`).
4. **Nonce / IV Uniqueness**: 12-byte (96-bit) cryptographically random IV generated fresh per write operation via `SecureRandom`. Never reused under the same key.
5. **Additional Authenticated Data (AAD)**: Container headers (magic string, schema version, creation timestamp) are bound to the GCM cipher via `Cipher.updateAAD()`, enforcing header integrity prior to decryption.
6. **Key Derivation (KDF)**: `PBKDF2WithHmacSHA256` with 16-byte random salt. Iterations dynamically calibrated during setup loop to target a **350 ms unlock latency** on target mobile hardware ($\ge 100,000$ iterations floor).

---

## 6. ENCRYPTED BACKUP FORMAT

1. **User-Initiated SAF Export**: Export triggered explicitly by user via Storage Access Framework (SAF) document picker. Zero background cloud sync.
2. **Encrypted Container Format (`.eltkbackup`)**:
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
3. **No Plaintext Export**: Backups contain zero unencrypted legal records or plaintext encryption keys.

---

## 7. RESTORE AND RECOVERY

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

- **Replacement-Phone Recovery**: On a clean device without `AndroidKeyStore` state, user inputs Master Passphrase or 12/24-word recovery seed -> derives `KEK_passphrase` / `KEK_recovery` -> unwraps `DEK` -> generates a new local KeyStore key.
- **Atomic Restore**: Unpacks to isolated `cache/restore_staging/`, validates AAD tag and manifest SHA-256 digests BEFORE replacing active data, snapshots `files/data/` to `files/rollback_backup/`, and syncs `restore_state.json` via `fsync()`.
- **Startup Crash Rollback**: `Application.onCreate()` checks `restore_state.json`. If incomplete, automatically restores `files/rollback_backup/` pre-restore snapshot intact.

---

## 8. FAILURE AND DATA-LOSS CONTROLS

1. **Incorrect Credentials**: Mismatched passphrases throw `GeneralSecurityException` without decrypting keys or altering wrapped key files.
2. **Corrupted / Malformed Backups**: Bit-flips or path traversal attempts (`../`) trigger `AEADBadTagException` or `ZipException` during `STATE_STAGING`; restore aborts without modifying active workspace files.
3. **Insufficient Storage**: Storage full condition during write aborts transaction and reverts to pre-write state without corrupting database headers.
4. **Interrupted Writes**: Database operations execute inside explicit SQLite transactions (`beginTransaction()`). File updates use temporary file write -> `fsync()` -> atomic rename patterns.
5. **Evidence Preservation**: Original imported evidence files remain immutable (`context.getFilesDir()`). Corrections or annotations are recorded as new versioned child events rather than overwriting historical records.

---

## 9. TEST PLAN (FICTIONAL TEST DATA ONLY)

Real legal records remain **STRICTLY PROHIBITED** until 100% of the following automated unit, integration, and instrumentation tests pass in CI using synthetic test fixtures:

| Test Case | Scope | Execution Command / Script | Acceptance Criteria |
| :--- | :--- | :--- | :--- |
| **Correct & Incorrect Passphrases** | Unit (CI) | `./gradlew test --tests "*PassphraseKeyTest*"` | Valid passphrase unwraps `DEK`. Invalid passphrase throws `GeneralSecurityException` without modifying wrapped key files. |
| **Ciphertext Tampering** | Unit (CI) | `./gradlew test --tests "*AeadIntegrityTest*"` | Single bit-flip in ciphertext or AAD header throws `AEADBadTagException` and halts read operation. |
| **Wrong Recovery Material** | Unit (CI) | `./gradlew test --tests "*RecoverySeedTest*"` | Mismatched recovery seed throws `GeneralSecurityException` without corrupting active keys. |
| **Nonce Uniqueness** | Unit (CI) | `./gradlew test --tests "*NonceUniquenessTest*"` | 100,000 encryption cycles produce zero duplicate 12-byte IVs. |
| **Truncated / Malformed Backups** | Integration (CI) | `./gradlew test --tests "*CorruptBackupTest*"` | Truncated backup archive is rejected in `STATE_STAGING` and active database remains 100% unmodified. |
| **Interrupted Export / Restore** | Instrumentation (Device) | `./gradlew connectedAndroidTest` | Process killed mid-restore; restart automatically triggers startup recovery and restores pre-restore snapshot intact. |
| **Replacement-Phone Recovery** | Instrumentation (Device) | `./gradlew connectedAndroidTest` | Clean app instance without KeyStore key recovers complete workspace using 12/24-word seed. |
| **Storage Full Condition** | Integration (CI) | `./gradlew test --tests "*DiskFullTest*"` | Disk full error during write aborts transaction and reverts to pre-write state without corrupting database headers. |

---

## 10. IMPLEMENTATION GATES

Real legal records remain strictly prohibited until all release gates pass automated verification:

- **Gate A**: Architecture design approval by maintainer.
- **Gate B**: Key management and PBKDF2 derivation engine implemented and passing unit tests in CI.
- **Gate C**: Encrypted database and media file storage repository implemented and passing CI.
- **Gate D**: AEAD backup package exporter implemented and passing CI.
- **Gate E**: Transactional crash-resilient restore engine implemented and passing CI.
- **Gate F**: Replacement-phone recovery workflow passing on physical Android devices.
- **Gate G**: Failure and corruption injection test suite passing 100% in CI.
- **Gate H**: Independent security design review completed.
- **Gate I**: Explicit maintainer sign-off lifting prohibition for real legal records.

---

## 11. OPEN TECHNICAL DECISIONS [UNRESOLVED]

1. **Storage Engine Choice**: Standard Android SDK SQLite (`android.database.sqlite`) with payload AEAD encryption vs. SQLCipher for Android (`net.zetetic:android-database-sqlcipher`).
2. **Recovery Seed Derivation Standardization**: Standard BIP39 key derivation (`PBKDF2WithHmacSHA512`, 2048 iterations) vs. custom PBKDF2/HKDF key wrapping scheme.
3. **Password KDF Parameter Benchmarking**: Calibrating PBKDF2/Argon2 iteration parameters via setup benchmark loop on target mobile hardware.
4. **Recovery Mnemonic Entropy**: 12-word recovery seed (128-bit entropy) vs. 24-word recovery seed (256-bit entropy).
5. **Rollback Attack Defense Mechanism**: User-confirmed launch state timestamps vs. out-of-band sequence numbers.

---

## 12. CHANGE RECORD

- **Authoritative Baseline**: Commit `dbd4153ca39ede69890ebaf47feaab7f393c3c5a` (`Restore app module configuration` on `main`).
- **Document Status**: Proposed design specification only. Zero lines of Java application code or encryption logic implemented.
