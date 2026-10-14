DOCUMENT TITLE: Elite Kit Secure-Storage Design
STATUS: PROPOSED — NOT IMPLEMENTED — NOT SECURITY-VERIFIED

# ELITE KIT — SECURE STORAGE AND RECOVERY DESIGN SPECIFICATION

Document Path: docs/SECURE_STORAGE_DESIGN.md
Authoritative Baseline: dbd4153ca39ede69890ebaf47feaab7f393c3c5a
Governing Authority: MASTER_CONTROL.md

---

## 1. PURPOSE AND SCOPE

- **Purpose**: Define a reviewable security architecture specification for Elite Kit (`com.elitekit.app`), establishing local-first data encryption, non-circular key management, zero-keystore replacement-phone recovery, AEAD backup export, crash-resilient atomic restore, and fictional-data test release gates.
- **Current Security Status**: **PROPOSED — NOT IMPLEMENTED — NOT SECURITY-VERIFIED**. At baseline `dbd4153ca39ede69890ebaf47feaab7f393c3c5a`, the codebase contains a minimal native Java activity shell (`MainActivity.java`). Encrypted database tables, key management mechanisms, backup generators, restore engines, and recovery seed modules are **NOT IMPLEMENTED**.
- **Scope**: Local-first Android native legal workspace (`com.elitekit.app`), Java 17, Android minSdk 26, targetSdk 35, compileSdk 35, AGP 8.7.3.
- **Financial Boundary**: $0 ongoing development cost; zero dependencies on paid APIs, cloud subscriptions, or online account infrastructures.
- **Fictional Test Data Policy**: Strictly 100% synthetic fictional test data during all design and development phases. Real confidential legal records remain **STRICTLY PROHIBITED** until all security capabilities, key management systems, backup, restore, and replacement-phone recovery pass automated CI verification.

---

## 2. CURRENT VERIFIED STATE

At baseline commit `dbd4153ca39ede69890ebaf47feaab7f393c3c5a`:
1. **Application Shell**: Minimal Android Activity shell (`app/src/main/java/com/elitekit/app/MainActivity.java`).
2. **Encrypted Storage**: Not implemented in source code.
3. **Key Management**: Not implemented in source code.
4. **Encrypted Backup & Restore**: Not implemented in source code.
5. **Replacement-Phone Recovery**: Not implemented in source code.
6. **Automated Security Tests**: Absent in source code.
7. **Build Workflows**: `.github/workflows/main.yml` and `.github/workflows/android-smoke-test.yml` exist, build test artifacts, and remain pristine.

---

## 3. THREAT MODEL AND EXPLICIT LIMITATIONS

### 3.1 Threat Vectors and Security Boundaries
1. **Lost or Stolen Locked Phone**: Physical extraction of device flash storage.
   - *Boundary*: Payload encryption (`DEK`) via AES-256-GCM. Unwrapping `DEK` requires user Master Passphrase or TEE-backed biometric unlock (`KEK_keystore`).
2. **Unauthorized Access to App Files or Exported Backups**: Co-located untrusted app or shared device user inspecting private storage or backup files.
   - *Boundary*: Android OS app sandboxing (`context.getFilesDir()`) combined with AEAD payload encryption (`K_backup`) on exported archives.
3. **Forgotten Passphrase / Lost TEE Key**: User forgets passphrase or phone is destroyed.
   - *Boundary*: Independent unwrapping path via BIP39 Recovery Seed (`KEK_recovery`). If user loses both Master Passphrase and seed, data is unrecoverable (zero backdoor).
4. **Loss or Replacement of the Phone**: Phone destroyed or user migrating to a clean new device.
   - *Boundary*: Pure credential-derived unwrapping (`KEK_recovery` or `KEK_passphrase`) without dependence on the old device's KeyStore.
5. **Corrupted, Truncated, Substituted, or Incompatible Backups**: Altered ciphertext bytes, malformed zip paths, or injected entries.
   - *Boundary*: Additional Authenticated Data (`AAD`) header binding, 128-bit AEAD tag verification, zip path sanitation (`../` blocking), and manifest SHA-256 verification before disk extraction.
6. **Interrupted Writes and Partial Restores**: Power loss or crash during database write or restore swap.
   - *Boundary*: Atomic staging in `cache/restore_staging/`, pre-restore snapshotting in `files/rollback_backup/`, and `restore_state.json` startup rollback.
7. **Malicious or Modified Application Updates**: Signed or unsigned app update attempting to corrupt database schema.
   - *Boundary*: Schema migrations versioned via `PRAGMA user_version` executing inside SQLite transactions with pre-migration snapshotting.
8. **Accidental Disclosure Through Logs, Clipboard, Exports, or Debugging**: Sensitive keys leaking into Android logcat or screenshots.
   - *Boundary*: Plaintext keys and passphrases prohibited from `Log` calls, screenshots (`FLAG_SECURE`), or unencrypted temporary files.

### 3.2 Explicit Limitations
- The application **CANNOT** guarantee protection against an OS-level kernel compromise, active spyware, keyloggers, or a fully unlocked physical device with active root access.

---

## 4. SECURITY OBJECTIVES AND LIMITATIONS

- **Confidentiality at Rest**: Payload data encrypted at rest using AES-256-GCM.
- **Integrity Verification & Authenticated Decryption**: AEAD tags (128-bit) and AAD header binding ensure modified ciphertext triggers `AEADBadTagException` prior to disk extraction.
- **Explicit Failure Reporting**: Decryption or integrity failures fail closed with explicit security exceptions, preventing empty records or silent data replacement.
- **Controlled Backup Export**: Backups are user-initiated via Storage Access Framework (SAF) with zero background cloud uploads.
- **Recoverability After Device Replacement**: Replacement phone recovery relies 0% on the old phone's KeyStore TEE key.
- **Hashes Boundary**: Hashes and digests provide bit-rot detection only. They do not constitute proof against authorized state rollback, truncation, or record deletion. The system is NOT described as tamper-proof.

---

## 5. PROPOSED KEY HIERARCHY

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

### Key Roles & Separation Rules
1. **`DEK` (Data Encryption Key)**: 256-bit random AES key (`SecureRandom`) encrypting database records and media files directly. Never written to disk in unencrypted plaintext. Encrypted as `wrapped_dek_passphrase.bin`, `wrapped_dek_keystore.bin`, and `wrapped_dek_recovery.bin`.
2. **`KEK_passphrase`**: Derived from Master Passphrase + 16-byte random salt via `PBKDF2WithHmacSHA256` ($\ge 100,000$ iterations floor). Transient in-memory `SecretKey`. Zeroed out immediately after use (`Arrays.fill(pwd, '\0')`). Never use raw passphrases directly as AES keys.
3. **`KEK_keystore`**: Non-exportable hardware TEE key inside `AndroidKeyStore` alias `"EliteKit_Keystore_KEK"`. Biometric unlock gate on local phone. **Cannot migrate to replacement phone.**
4. **`KEK_recovery`**: Derived from 12/24-word BIP39 Mnemonic Seed + salt via `PBKDF2`. Unwraps `DEK` on replacement devices.
5. **`K_backup`**: Derived fresh per export via HKDF-Expand from `KEK_passphrase` or `KEK_recovery` with info label `"EliteKit-BackupPayloadKey-v1"`.

---

## 6. ENCRYPTED STORAGE DECISION

| Evaluation Dimension | Approach A: Standard Android SQLite + AEAD Payload Encryption (`android.database.sqlite`) | Approach B: SQLCipher for Android (`net.zetetic:android-database-sqlcipher`) |
| :--- | :--- | :--- |
| **Dependencies & Financial Cost** | **$0 added dependencies.** Built directly into Android SDK. Zero third-party library maintenance risk. | **$0 open-source library.** Introduces third-party dependency maintenance requirement. |
| **Binary Footprint** | **0 MB increase.** Zero native JNI binaries added to APK. | **~12 MB increase.** Requires bundling native JNI binaries (`libsqlcipher.so`). |
| **Metadata Exposure Risks** | Structural metadata visible (headers, table/column names, row counts). Field payloads 100% AES-256-GCM encrypted. | **Zero metadata exposure.** 100% of SQLite database pages encrypted on disk via AES-256-CBC / HMAC. |
| **Maintenance & Compatibility** | High stability across minSdk 26 to targetSdk 35 managed by Android OS vendor updates. | Requires ongoing NDK JNI binary updates for targetSdk 35 rules and 64-bit alignment checks. |
| **Recommendation & Trade-Off** | **PROPOSED BASELINE**: Standard Android SQLite + AEAD Payload Encryption provides zero dependency risk and 0MB footprint while fully protecting sensitive content payloads. Abstracted behind `EncryptedStorageRepository` interface. | **UNRESOLVED CHOICE**: Pending explicit user selection if full page header obfuscation becomes mandatory. |

---

## 7. BACKUP FORMAT AND RECOVERY

1. **User-Controlled Export**: Export triggered explicitly by user via Storage Access Framework (SAF). Zero automatic background cloud sync.
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
3. **Staged Restore State Machine (`restore_state.json`)**:
   - Staging: Unpacks archive to isolated `cache/restore_staging/`.
   - Validation: Verifies AAD header tag, zip path safety (`../` blocking), manifest SHA-256 digests, and schema version BEFORE replacing active data.
   - Snapshot: Copies active `files/data/` to `files/rollback_backup/` and syncs `restore_state.json` via `FileOutputStream.getFD().sync()`.
   - Activation: Swaps staged files to `files/data/`.
   - Startup Recovery Check: `Application.onCreate()` checks `restore_state.json`. If incomplete, automatically restores `files/rollback_backup/` pre-restore snapshot intact.

---

## 8. NONCE, KEY, AND FAILURE HANDLING

1. **Nonce Uniqueness**: 12-byte (96-bit) cryptographically random IV generated fresh per write operation via `SecureRandom`. Never reused under the same key.
2. **Key Rotation**: Passphrase change unwraps `DEK` with old passphrase, derives new `KEK_passphrase`, and re-wraps `DEK` into `wrapped_dek_passphrase.bin`.
3. **Authentication Failures**: Mismatched passphrases or tampered archives throw `AEADBadTagException` or `GeneralSecurityException`. **Never silently replace decryption failures with empty data.**
4. **Interrupted Writes**: Database write operations execute inside explicit SQLite transactions (`beginTransaction()`). File updates use temporary file write -> `fsync()` -> atomic rename.

---

## 9. TESTING REQUIREMENTS (FICTIONAL TEST DATA ONLY)

Real legal records remain **STRICTLY PROHIBITED** until 100% of the following automated unit, integration, and instrumentation tests pass in CI using synthetic test fixtures:

| Test Case | Scope | Execution Command / Script | Acceptance Criteria |
| :--- | :--- | :--- | :--- |
| **Correct & Incorrect Passphrases** | Unit (CI) | `./gradlew test --tests "*PassphraseKeyTest*"` | Valid passphrase unwraps `DEK`. Invalid passphrase throws `GeneralSecurityException` without modifying wrapped key files. |
| **Ciphertext Tampering** | Unit (CI) | `./gradlew test --tests "*AeadIntegrityTest*"` | Single bit-flip in ciphertext or AAD header throws `AEADBadTagException` and halts read. |
| **Wrong Recovery Material** | Unit (CI) | `./gradlew test --tests "*RecoverySeedTest*"` | Mismatched recovery seed throws `GeneralSecurityException` without corrupting active keys. |
| **Nonce Uniqueness** | Unit (CI) | `./gradlew test --tests "*NonceUniquenessTest*"` | 100,000 encryption cycles produce zero duplicate 12-byte IVs. |
| **Truncated / Malformed Backups** | Integration (CI) | `./gradlew test --tests "*CorruptBackupTest*"` | Malformed archive rejected in `STATE_STAGING`; active database 100% unmodified. |
| **Interrupted Export / Restore** | Instrumentation (Device) | `./gradlew connectedAndroidTest` | Process killed mid-restore; restart automatically restores `files/rollback_backup/` pre-restore snapshot intact. |
| **Replacement-Phone Recovery** | Instrumentation (Device) | `./gradlew connectedAndroidTest` | Clean app instance without KeyStore key recovers complete workspace using 12/24-word seed. |
| **Storage Full Condition** | Integration (CI) | `./gradlew test --tests "*DiskFullTest*"` | Disk full error aborts write transaction without corrupting database headers. |

---

## 10. REAL-RECORD RELEASE GATE

Real legal records remain strictly prohibited until all nine release gates pass automated verification:
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

## 11. PROPOSED IMPLEMENTATION SEQUENCE

1. **Increment 1**: Gradle 8.9 Wrapper Setup & Local Build Harness (`./gradlew test`).
2. **Increment 2**: Cryptographic Key Hierarchy & PBKDF2 Derivation Engine (`crypto/`).
3. **Increment 3**: Encrypted Database & Media Storage Repository (`storage/`).
4. **Increment 4**: AEAD Encrypted Backup Package Exporter (`backup/`).
5. **Increment 5**: Transactional Crash-Resilient Restore Engine (`restore/`).
6. **Increment 6**: BIP39 Replacement-Phone Recovery Workflow (`recovery/`).
7. **Increment 7**: Security CI Test Suite Integration (`ci/`).

---

## 12. OPEN DECISIONS [UNRESOLVED]

1. **Storage Engine Choice**: Standard Android SDK SQLite (`android.database.sqlite`) with payload AEAD encryption vs. SQLCipher for Android (`net.zetetic:android-database-sqlcipher`).
2. **Recovery Seed Derivation Standardization**: Standard BIP39 key derivation (`PBKDF2WithHmacSHA512`, 2048 iterations) vs. custom PBKDF2/HKDF key wrapping scheme.
3. **Password KDF Parameter Benchmarking**: Calibrating PBKDF2/Argon2 iteration parameters via setup benchmark loop on target mobile hardware.
4. **Recovery Mnemonic Entropy**: 12-word recovery seed (128-bit entropy) vs. 24-word recovery seed (256-bit entropy).
5. **Rollback Attack Defense Mechanism**: User-confirmed launch state timestamps vs. out-of-band sequence numbers.

---

## 13. CHANGE RECORD

- **Authoritative Baseline**: Commit `dbd4153ca39ede69890ebaf47feaab7f393c3c5a` (`Restore app module configuration` on `main`).
- **Document Status**: Initial proposed design specification only. Zero lines of Java application code or encryption logic implemented.
