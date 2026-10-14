Title: Elite Kit — Secure Storage and Recovery Design
Status: PROPOSED — NOT IMPLEMENTED — NOT SECURITY-VALIDATED
Version: 0.1
Scope: Android-native, local-first Elite Kit (`com.elitekit.app`)
Cost Limit: $0
Test Data: Fictional only
Authoritative Baseline: dbd4153ca39ede69890ebaf47feaab7f393c3c5a
Governing Authority: MASTER_CONTROL.md

---

## A. PURPOSE AND SCOPE

1. **Purpose**: Establish a reviewable security architecture specification for Elite Kit (`com.elitekit.app`), defining local data encryption, key hierarchy, zero-keystore replacement-phone recovery, AEAD backup export, crash-resilient atomic restore, and fictional-data test release gates.
2. **Current Security Status**: **PROPOSED — NOT IMPLEMENTED — NOT SECURITY-VALIDATED**. At baseline `dbd4153ca39ede69890ebaf47feaab7f393c3c5a`, the codebase contains a minimal native Java activity shell (`MainActivity.java`). Encrypted database tables, key management mechanisms, backup generators, restore engines, and recovery seed modules are **NOT IMPLEMENTED**.
3. **Target Environment**: Local-first Android native application, Java 17, minSdk 26, targetSdk 35, compileSdk 35, AGP 8.7.3.
4. **Financial Boundary**: $0 ongoing development cost; zero dependencies on paid APIs, cloud subscriptions, or online account infrastructures.
5. **Fictional Data Policy**: Strictly 100% synthetic fictional test data during all design and development phases. Real confidential legal records remain **STRICTLY PROHIBITED** until all security capabilities, recovery paths, and test suites pass automated CI verification.

---

## B. THREAT MODEL AND EXPLICIT LIMITATIONS

### B.1 Threat Vectors and Security Boundaries
1. **Lost or Stolen Phone**: Physical extraction of device flash storage.
   - *Boundary*: Payload encryption (`DEK`) via AES-256-GCM. Unwrapping `DEK` requires user Master Passphrase or TEE-backed biometric unlock (`KEK_keystore`).
2. **Unauthorized App or File Access**: Local untrusted app or shared device user inspecting private app storage.
   - *Boundary*: Android OS app sandboxing (`context.getFilesDir()`) combined with AES-256 payload encryption.
3. **Wrong or Forgotten Passphrase**: Mismatched user input during unlock.
   - *Boundary*: Fails closed. Mismatched credentials trigger `GeneralSecurityException` without decrypting keys or altering wrapped key files.
4. **Lost Recovery Material**: User loses both Master Passphrase and BIP39 recovery seed.
   - *Boundary*: Data is permanently unrecoverable (zero backdoor).
5. **Corrupt, Modified, or Replaced Backup**: Altered ciphertext, truncated archives, or injected entries.
   - *Boundary*: Additional Authenticated Data (`AAD`) header binding and 128-bit AEAD tag verification before disk extraction.
6. **Failed Restore and Partial Writes**: Power loss or crash during database write or restore swap.
   - *Boundary*: Atomic staging in `cache/restore_staging/`, pre-restore snapshotting in `files/rollback_backup/`, and `restore_state.json` startup rollback.
7. **Replacement Phone & App Reinstall**: Migrating workspace to a clean new device.
   - *Boundary*: Pure credential-derived unwrapping (`KEK_recovery` or `KEK_passphrase`) without dependence on the old device's KeyStore TEE.
8. **Accidental Deletion & History Truncation**: Replaying an older valid database snapshot or truncating logs.
   - *Boundary*: Monotonically increasing launch sequence counters in KeyStore preferences and Workspace Timeline Audit Journals inside the encrypted database.
9. **Database/File Inconsistency**: App crash mid-write.
   - *Boundary*: Transactional SQLite writes (`beginTransaction()`) and temporary file write -> `fsync()` -> atomic rename patterns.

### B.2 Explicit Limitations
- The application **CANNOT** guarantee protection against an OS-level kernel compromise, active spyware, keyloggers, or a fully unlocked physical device with active root access.

---

## C. KEY ARCHITECTURE

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

## D. CRYPTOGRAPHY

1. **Primitives**: Standard Java JCE `javax.crypto.Cipher` implementing `AES/GCM/NoPadding`.
2. **Key Length**: 256-bit AES symmetric keys generated via `java.security.SecureRandom`.
3. **Authentication Tags**: 128-bit GCM authentication tags (`GCMParameterSpec(128, iv)`).
4. **Nonce / IV Uniqueness**: 12-byte (96-bit) cryptographically random IV generated fresh per write operation via `SecureRandom`. Never reused under the same key.
5. **Additional Authenticated Data (AAD)**: Container headers (magic string, schema version, creation timestamp) are bound to the GCM cipher via `Cipher.updateAAD()`, enforcing header integrity prior to decryption.

---

## E. STORAGE ARCHITECTURE

| Evaluation Dimension | Approach A: Standard Android SQLite + AEAD Payload Encryption (`android.database.sqlite`) | Approach B: SQLCipher for Android (`net.zetetic:android-database-sqlcipher`) |
| :--- | :--- | :--- |
| **Dependency & Financial Cost** | **$0 added dependencies.** Built directly into Android SDK. Zero third-party library maintenance risk. | **$0 open-source library.** Introduces third-party dependency maintenance requirement. |
| **Binary Footprint** | **0 MB increase.** Zero native JNI binaries added to APK. | **~12 MB increase.** Requires bundling native JNI binaries (`libsqlcipher.so`). |
| **Metadata Exposure Risks** | Structural metadata visible (headers, table/column names, row counts). Payload contents inside columns 100% AES-256-GCM encrypted. | **Zero metadata exposure.** 100% of SQLite database pages encrypted on disk via AES-256-CBC / HMAC. |
| **Maintenance & Compatibility** | High stability across minSdk 26 to targetSdk 35 managed by Android OS vendor updates. | Requires ongoing NDK JNI binary updates for targetSdk 35 rules and 64-bit alignment checks. |
| **Recommendation & Trade-Off** | **PROPOSED BASELINE**: Standard Android SQLite + AEAD Payload Encryption provides zero dependency risk and 0MB footprint while fully protecting sensitive content payloads. Abstracted behind `EncryptedStorageRepository` interface. | **UNRESOLVED CHOICE**: Pending explicit user selection if full page header obfuscation becomes mandatory. |

---

## F. BACKUP AND RESTORE

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
3. **Atomic Restore State Machine (`restore_state.json`)**:
   - Staging: Unpacks archive to isolated `cache/restore_staging/`.
   - Validation: Verifies AAD header tag, zip path safety (`../` blocking), manifest SHA-256 digests, and schema version BEFORE replacing active data.
   - Snapshot: Copies active `files/data/` to `files/rollback_backup/` and syncs `restore_state.json` via `FileOutputStream.getFD().sync()`.
   - Activation: Swaps staged files to `files/data/`.
   - Startup Recovery Check: `Application.onCreate()` checks `restore_state.json`. If incomplete, automatically restores `files/rollback_backup/` pre-restore snapshot intact.

---

## G. RECOVERY TEST SCENARIO (PROPOSED TEST PROCEDURE)

The following sequence describes the proposed verification test procedure using synthetic fictional data:
1. Create synthetic test workspace on Instance A (generate `DEK`, populate test matters and evidence files).
2. Export encrypted `.eltkbackup` archive to external storage via SAF.
3. Simulate complete device loss by instantiating clean Instance B with zero local KeyStore keys or state.
4. Input 12/24-word BIP39 recovery seed into Instance B.
5. Instance B derives `KEK_recovery`, unwraps `DEK`, decrypts `.eltkbackup`, and generates new local `KEK_keystore`.
6. Compare restored records and evidence files byte-for-byte against Instance A originals to verify parity.
7. Verify wrong credentials and corrupted backup archives fail safely without altering existing workspace files.
8. Verify force-killing process mid-restore automatically triggers startup rollback and preserves pre-restore data 100% intact.

---

## H. APPROVAL AND DATA-LOSS CONTROLS

Explicit modal human user approval is **MANDATORY** for:
1. Destructive deletion of legal matters or evidence purging.
2. Key rotation or recovery seed regeneration.
3. Backup restore operations replacing existing workspace files.
4. Irreversible database schema migrations.
5. Backup archive replacements.

*Evidence Preservation*: Original imported evidence files remain immutable (`context.getFilesDir()`). Corrections or annotations are recorded as new versioned child events rather than overwriting historical records.

---

## I. IMPLEMENTATION AND TEST GATES

Real legal records remain **STRICTLY PROHIBITED** until all nine release gates pass automated verification:
1. **Gate 1**: Architecture design approval by maintainer.
2. **Gate 2**: Key lifecycle and PBKDF2 derivation engine implemented and passing unit tests in CI.
3. **Gate 3**: Encrypted database and media file storage repository implemented and passing CI.
4. **Gate 4**: AEAD backup package exporter implemented and passing CI.
5. **Gate 5**: Transactional crash-resilient restore engine implemented and passing CI.
6. **Gate 6**: Replacement-phone recovery workflow passing on physical Android devices.
7. **Gate 7**: Failure and corruption injection test suite passing 100% in CI.
8. **Gate 8**: Independent security design review completed.
9. **Gate 9**: Explicit maintainer sign-off lifting prohibition for real legal records.

---

## J. OPEN DECISIONS [UNRESOLVED]

1. **Storage Engine Choice**: Standard Android SDK SQLite (`android.database.sqlite`) with payload AEAD encryption vs. SQLCipher for Android (`net.zetetic:android-database-sqlcipher`).
2. **Recovery Seed Derivation Standardization**: Standard BIP39 key derivation (`PBKDF2WithHmacSHA512`, 2048 iterations) vs. custom PBKDF2/HKDF key wrapping scheme.
3. **Password KDF Parameter Benchmarking**: Calibrating PBKDF2/Argon2 iteration parameters via setup benchmark loop on target mobile hardware.
4. **Recovery Mnemonic Entropy**: 12-word recovery seed (128-bit entropy) vs. 24-word recovery seed (256-bit entropy).
5. **Rollback Attack Defense Mechanism**: User-confirmed launch state timestamps vs. out-of-band sequence numbers.
