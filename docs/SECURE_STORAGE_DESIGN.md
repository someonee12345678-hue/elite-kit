# ELITE KIT — SECURE STORAGE AND RECOVERY DESIGN SPECIFICATION

Document Path: docs/SECURE_STORAGE_DESIGN.md
Status: PROPOSED DESIGN — NOT IMPLEMENTED — NOT SECURITY-VALIDATED
Authoritative Baseline: dbd4153ca39ede69890ebaf47feaab7f393c3c5a
Governing Authority: MASTER_CONTROL.md

---

## 1. Classification of Requirements, Proposed Architecture, and Open Decisions

### 1.1 Confirmed Baseline Requirements [VERIFIED FACT]
1. **Target Environment**: Local-first Android native application (`com.elitekit.app`), Java 17, Android minSdk 26, targetSdk 35, compileSdk 35, AGP 8.7.3.
2. **Tooling & Financial Boundary**: $0 tooling cost; zero dependencies on paid APIs, cloud subscriptions, or online account infrastructures.
3. **Fictional Data Policy**: 100% synthetic fictional test data during all design and development phases. Real legal records remain **STRICTLY PROHIBITED** until all security capabilities, recovery paths, and test suites pass automated CI verification.
4. **Preservation Boundaries**: `main` branch, `app/src/main/java/com/elitekit.app/MainActivity.java`, build configurations (`*.gradle.kts`), and both existing GitHub Actions workflows (`.github/workflows/main.yml` and `.github/workflows/android-smoke-test.yml`) remain 100% unchanged.
5. **Baseline Reality**: The repository at baseline `dbd4153ca39ede69890ebaf47feaab7f393c3c5a` contains a minimal Java activity shell (`MainActivity.java`). Encrypted database tables, key management mechanisms, backup generators, restore engines, and recovery seed modules are **NOT IMPLEMENTED**.

### 1.2 Proposed Target Architecture [PROPOSED — NOT IMPLEMENTED]
- Local-first encrypted workspace architecture utilizing non-circular key separation, authenticated AEAD payload encryption (`AES/GCM/NoPadding`), zero-keystore replacement-phone recovery, and a 9-state crash-resilient atomic restore engine.

### 1.3 Open Technical Decisions [UNRESOLVED]
- Storage technology choice (SQLite + Column AEAD vs. SQLCipher full-database page encryption).
- Recovery seed derivation standardization (BIP39 `PBKDF2WithHmacSHA512` vs custom PBKDF2/HKDF wrapping).
- Runtime KDF benchmark loop parameters and target latency ranges.
- Offline rollback protection mechanism.

---

## 2. Threat Model & Offline Rollback Limitations

### 2.1 Threat Vectors and Security Boundaries
1. **Lost or Stolen Device**: Physical extraction of device flash storage.
   - *Boundary*: Payload encryption (`DEK`) via AES-256-GCM. Unwrapping `DEK` requires user Master Passphrase or TEE-backed biometric unlock (`KEK_keystore`).
2. **Co-located Unprivileged App Access**: Local untrusted app inspecting shared storage.
   - *Boundary*: Android OS app sandboxing (`context.getFilesDir()`) combined with AES-256 payload encryption.
3. **Backup Theft or Disclosure**: Interception of exported `.eltkbackup` archives.
   - *Boundary*: AEAD authenticated encryption (`K_backup`) derived from user credentials; zero plaintext payload exported.
4. **Forgotten Passphrase / Lost TEE Key**: User forgets passphrase or phone is destroyed.
   - *Boundary*: Independent unwrapping path via BIP39 Recovery Seed (`KEK_recovery`). If user loses both Master Passphrase and seed, data is unrecoverable (zero backdoor).
5. **Replacement-Phone Recovery**: Recovering workspace on a clean replacement phone.
   - *Boundary*: Pure credential-derived unwrapping (`KEK_recovery` or `KEK_passphrase`) without dependence on the old device's KeyStore.
6. **Corrupt or Tampered Backup**: Altered ciphertext bytes or tampered backup headers.
   - *Boundary*: Additional Authenticated Data (`AAD`) header binding and 128-bit AEAD tag verification before disk extraction or parsing.

### 2.2 Offline Rollback Detection Limitations
When all application state is 100% offline and stored on a single local device, **detecting or preventing a rollback attack cannot be guaranteed solely by cryptographic hash chains or local database timestamps**.

- **Attack Vector**: An attacker with file-system access (or restoring an older valid `.eltkbackup` archive) replaces the current encrypted database file `workspace.db` (State $T_2$) with an older valid encrypted database file from State $T_1$. Because State $T_1$ is validly encrypted under `DEK` and authentic, the application will decrypt it successfully.
- **Fundamental Limit**: Without a remote trusted timestamping authority or a hardware-protected monotonic counter (e.g., Android KeyStore `setStrongBoxBacked(true)` monotonic counters, which are hardware-limited and unavailable on many Android 8.0+ devices), the local application cannot distinguish an intentionally restored past state from a malicious state rollback.
- **Proposed Mitigation**:
  1. Store a monotonically increasing launch sequence counter in hardware-backed KeyStore preferences (where supported).
  2. Maintain a mandatory, user-visible Workspace Timeline Audit Journal inside the encrypted database that records the creation timestamp, random UUID, and preceding state hash for every backup export and database checkpoint.
  3. Require explicit modal user approval displaying the backup creation timestamp and sequence ID whenever a backup import is performed.

---

## 3. Storage Technology Comparison: Encrypted SQLite Payloads vs. SQLCipher

| Evaluation Dimension | Approach A: Standard Android SQLite + AEAD Payload Encryption (`android.database.sqlite`) | Approach B: Full-Database Page Encryption (`SQLCipher for Android`) |
| :--- | :--- | :--- |
| **Dependency & Financial Cost** | **$0 added dependencies.** Built directly into Android SDK. Zero third-party library maintenance risk. | **$0 open-source library** (`net.zetetic:android-database-sqlcipher`). Introduces third-party dependency maintenance requirement. |
| **Binary Footprint** | **0 MB increase.** Zero native JNI binaries added to APK. | **~12 MB increase.** Requires bundling native JNI binaries (`libsqlcipher.so` for arm64-v8a, x86_64, armeabi-v7a). |
| **Metadata Exposure Risks** | **Exposes SQLite structural metadata.** Database headers, table names, column names, total row counts, and index structures remain visible in unencrypted SQLite pages. Payload contents inside columns are 100% encrypted via AES-256-GCM. | **Zero metadata exposure.** 100% of SQLite database pages (headers, tables, indexes, schemas, free lists) are encrypted on disk via AES-256-CBC / HMAC. |
| **Maintenance & OS Compatibility** | High stability. Managed directly by Android OS vendor updates across API 26+ (minSdk 26 to targetSdk 35). | Requires ongoing maintenance to update NDK JNI native binaries for new Android NDK releases, targetSdk 35 rules, and 64-bit alignment checks. |
| **Performance Overhead** | High performance. Encryption overhead is strictly confined to encrypted BLOB/String field reads and writes. | Moderate performance overhead. Every 4096-byte database page write triggers AES page re-encryption and HMAC calculation. |
| **Recovery Implications** | Simplified recovery. In the event of partial file corruption, uncorrupted records can be individually recovered from SQLite tables. | All-or-nothing recovery. Page corruption or header byte bit-flips can prevent the entire database file from opening. |
| **Recommendation & Open Decision** | **PROPOSED FOR INITIAL INCREMENT**: Standard Android SQLite + AEAD Payload Encryption provides zero dependency risk and 0MB footprint while fully protecting sensitive content payloads. Abstracted behind `EncryptedStorageRepository` interface to allow a future migration to SQLCipher if full page header obfuscation becomes mandatory. | **UNRESOLVED DECISION**: Pending explicit user selection. |

---

## 4. Complete Non-Circular Key Hierarchy

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

### Key Lifecycle Matrix

| Key Name | Type / Size | Creation Trigger | Storage Location | Key Rotation Path | Replacement-Phone Recovery Path | Destruction Protocol |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **`DEK`** (Data Encryption Key) | 256-bit random AES key (`SecureRandom`) | Workspace initialization | Never stored in plaintext. Stored encrypted as `wrapped_dek_passphrase.bin`, `wrapped_dek_keystore.bin`, and `wrapped_dek_recovery.bin`. | Remains constant during passphrase changes. Requires full DB re-encryption if explicitly rotated. | Unwrapped on replacement device using `KEK_recovery` or `KEK_passphrase`. | Overwritten in memory with zero bytes (`Arrays.fill(keyBytes, (byte)0)`). Encrypted files deleted on workspace wipe. |
| **`KEK_passphrase`** | 256-bit AES symmetric key | Derived on demand from Master Passphrase + `salt_passphrase.bin` | Transient in-memory `SecretKey`. Never written to disk. | Derived anew when passphrase changes. Unwraps `DEK`, then re-wraps `DEK` under new `KEK_passphrase`. | Derived directly on replacement device by user entering Master Passphrase. | Zeroed out in memory immediately after wrapping/unwrapping `DEK`. |
| **`KEK_keystore`** | Hardware-backed AES-256 key | Generated on local device during biometric setup | Hardware TEE inside `AndroidKeyStore` alias `"EliteKit_Keystore_KEK"`. Non-exportable. | Regenerated if biometric enrollment changes or local KeyStore invalidates key. | **Zero involvement in replacement-phone recovery.** New `KEK_keystore` generated locally on new phone. | Deleted via `keyStore.deleteEntry("EliteKit_Keystore_KEK")`. |
| **`KEK_recovery`** | 256-bit AES symmetric key | Derived on demand from BIP39 Mnemonic Seed + `salt_recovery.bin` | Transient in-memory `SecretKey`. Never written to disk. | Constant for a given recovery seed. | Derived on replacement device when user inputs 12/24-word recovery seed. | Zeroed out in memory immediately after unwrapping `DEK`. |
| **`K_backup`** | 256-bit AES-GCM key | Derived during backup export via HKDF-Expand from `KEK_passphrase` or `KEK_recovery` | Transient in-memory `SecretKey`. Never written to disk. | Regenerated fresh per backup export using a random salt. | Derived during backup import on replacement phone using user's passphrase or seed. | Zeroed out in memory immediately after backup zip stream encryption/decryption completes. |

---

## 5. Password-Based Key Derivation (PBKDF2) & Benchmarking Protocol

### 5.1 KDF Specification & Parameter Storage
- **Algorithm**: `PBKDF2WithHmacSHA256` (Standard Java JCE `SecretKeyFactory`).
- **Salt Generation**: 16 bytes (128 bits) generated fresh per workspace using `java.security.SecureRandom`.
- **Parameter Storage**: KDF parameters are stored in plaintext JSON header `kdf_params.json` inside private storage:
  ```json
  {
    "kdf_algorithm": "PBKDF2WithHmacSHA256",
    "kdf_version": 1,
    "salt_base64": "eXpNYTlsU3ByaW5nMTIzNA==",
    "target_iterations": 200000,
    "derived_key_length_bits": 256
  }
  ```

### 5.2 Runtime Benchmarking Protocol (Target Latency: 300ms–500ms)
To balance brute-force attack resistance with user unlock responsiveness across diverse Android hardware (from low-end budget chips to flagship SoCs), the application implements a setup benchmarking loop during workspace creation:

1. **Measurement Loop**:
   - Executes a test derivation of 10,000 iterations using `PBKDF2WithHmacSHA256` on a 16-byte dummy salt.
   - Measures elapsed wall-clock execution time via `System.nanoTime()`.
2. **Iteration Calculation**:
   - Calculates the iteration count required to achieve a target unlock execution latency of **350 milliseconds**:
     $$\text{iterations\_calibrated} = \left( \frac{350\text{ ms}}{\text{elapsed\_ms\_10k}} \right) \times 10,000$$
3. **Safety Bounds**:
   - Enforces a strict lower bound of **100,000 iterations** (OWASP mobile baseline minimum) and an upper bound of **1,000,000 iterations**.
4. **Zero Invented Benchmarks Rule**:
   - The application **MUST NOT** hardcode or claim fake benchmark numbers. Iteration counts are measured dynamically on the physical target device at runtime.

---

## 6. Backup Envelope Specification & Atomic Restore Engine

### 6.1 Encrypted Backup Envelope Format (`.eltkbackup`)
Backups are packaged as versioned AEAD-encrypted containers:
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

### 6.2 Atomic Crash-Resilient Restore State Machine (`restore_state.json`)

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

- **State Persistence**: Current state is recorded in `cache/restore_state.json` and flushed to disk via `FileOutputStream.getFD().sync()`.
- **Startup Recovery Check**: Upon app initialization (`Application.onCreate()`), the engine checks `restore_state.json`. If state is `STATE_ACTIVATING` or `STATE_ROLLBACK`, the app automatically restores `files/rollback_backup/` over `files/data/`, preserving the existing workspace 100% intact before UI launch.

---

## 7. Objective Acceptance Criteria Matrix

| Feature / Decision | Acceptance Test Method | Measurable Pass / Fail Criteria |
| :--- | :--- | :--- |
| **Passphrase Unwrapping** | Automated Unit Test (`PassphraseKeyTest.java`) | `PassphraseKeyEngine.unwrapDEK(validPassphrase)` returns valid 256-bit `SecretKey`. Incorrect passphrase throws `GeneralSecurityException`. |
| **AEAD Tampering Rejection** | Automated Unit Test (`AeadIntegrityTest.java`) | Modifying a single bit in ciphertext or AAD header throws `AEADBadTagException` and halts read operation without writing bytes to disk. |
| **Export/Import Round-Trip** | Automated Integration Test (`BackupRestoreTest.java`) | Export backup from Instance A, import into clean Instance B -> verify 100% record-for-record and byte-for-byte evidence file match. |
| **Corrupt Backup Preservation** | Automated Integration Test (`RestoreFailureTest.java`) | Attempt importing truncated backup archive -> verify import is rejected in `STATE_STAGING` and active database remains 100% unmodified. |
| **Mid-Restore Crash Rollback** | Automated Instrumentation Test (`CrashRollbackTest.java`) | Force kill app process during `STATE_ACTIVATING` state -> restart app -> verify startup recovery automatically restores `files/rollback_backup/` pre-restore data intact. |
| **Replacement-Phone Recovery** | Automated Integration Test (`BIP39RecoveryTest.java`) | On a clean device environment with no KeyStore key, input 12/24-word seed -> verify `DEK` is unwrapped and new local `KEK_keystore` generated. |
| **KDF Benchmarking Latency** | Runtime Unit Test (`KdfBenchmarkTest.java`) | Measured PBKDF2 execution time falls within 300ms–500ms window on target hardware and iteration count exceeds OWASP 100,000 minimum. |
| **Passphrase Zeroing Memory Check** | Automated Unit Test (`MemorySafetyTest.java`) | Verify `char[]` passphrase array contains exclusively `\0` bytes immediately following KDF execution. |
