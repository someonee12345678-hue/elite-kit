STATUS: PROPOSED DESIGN — NOT IMPLEMENTED OR SECURITY-VALIDATED
BASELINE: dbd4153ca39ede69890ebaf47feaab7f393c3c5a
SCOPE: Android-native, local-first Elite Kit (`com.elitekit.app`)
COST LIMIT: $0
TEST DATA: Fictional only

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

---

## 2. PROPOSED KEY HIERARCHY AND REPLACEMENT-PHONE RECOVERY

### 2.1 Non-Circular Key Flow Architecture

```
[ Primary Unlock Flow ]
Master Passphrase (user) ──> PBKDF2WithHmacSHA256 (salt_passphrase.bin) ──> KEK_passphrase ──┐
                                                                                           │
Android KeyStore TEE ────────────────────────────────────────────────────────> KEK_keystore ──┼──> unwraps ──> DEK (256-bit AES)
                                                                                           │                │
24-Word BIP39 Seed ────────> PBKDF2WithHmacSHA512 (salt_recovery.bin) ───> KEK_recovery ──┘                │ encrypts
                                                                                                            v
                                                                                                  SQLite DB & Evidence
```

### 2.2 End-to-End Replacement-Phone Recovery Path (Zero KeyStore Reliance)

```
[ Instance B: Clean Replacement Phone ]
1. User inputs 24-Word BIP39 Seed Material into clean Instance B (zero KeyStore state).
2. Instance B derives KEK_recovery via PBKDF2WithHmacSHA512 (salt_recovery.bin).
3. Instance B unwraps `wrapped_dek_recovery.bin` from backup envelope to recover DEK directly.
4. Instance B initializes local AndroidKeyStore TEE alias "EliteKit_Keystore_KEK" to generate new local KEK_keystore.
5. Instance B re-wraps DEK locally as `wrapped_dek_keystore.bin`, restoring local biometric unlock.
```

- **Key Wrapping Mechanics**:
  - `DEK`: 256-bit AES symmetric key (`SecureRandom`) generated on workspace setup. Encrypts database payload fields and evidence files directly.
  - `DEK` is wrapped into three separate, independent ciphertexts:
    - `wrapped_dek_passphrase.bin` = $E_{\text{KEK\_passphrase}}(\text{DEK})$
    - `wrapped_dek_keystore.bin` = $E_{\text{KEK\_keystore}}(\text{DEK})$
    - `wrapped_dek_recovery.bin` = $E_{\text{KEK\_recovery}}(\text{DEK})$
- **Passphrase Change & Older Backup Recoverability**:
  - When the user changes their Master Passphrase, the app unwraps `DEK` using old `KEK_passphrase`, derives new `KEK_passphrase`, and writes a new `wrapped_dek_passphrase.bin`. `DEK` itself **remains unchanged**.
  - Older backups created prior to the passphrase change remain 100% recoverable because they contain the payload stream encrypted under `DEK` (or wrapped under `KEK_recovery`), which is independent of passphrase rotations.

---

## 3. CRASH-SAFE TRANSACTIONAL RESTORE ENGINE

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

### 3.1 Persistent Restore Protocol & Failure Handling
1. **Isolated Staging**: Unpacks exported `.eltkbackup` container into isolated sandbox staging (`cache/restore_staging/`).
2. **Validation Gate**: Verifies AEAD tag, zip path safety (blocking `../` path traversal attacks), manifest SHA-256 digests, and schema version **BEFORE** modifying active workspace files.
3. **Pre-Restore Snapshot**: Active workspace (`files/data/`) is copied to `files/rollback_backup/`.
4. **`fsync()` State Commit**: Restore state `STATE_ACTIVATING` is written to `cache/restore_state.json` and flushed to disk via `FileOutputStream.getFD().sync()`.
5. **Disk-Full & Crash Handling**:
   - If disk space is insufficient during staging or snapshotting, the operation aborts immediately in `STATE_STAGING`, purging staging files without touching active workspace files.
   - If power loss or process kill occurs during `STATE_ACTIVATING`, `Application.onCreate()` checks `restore_state.json` upon next app startup. Detecting `STATE_ACTIVATING` or `STATE_ROLLBACK`, it automatically restores `files/rollback_backup/` over `files/data/`, preserving existing records 100% intact before UI launch.

---

## 4. BACKUP FORMAT SPECIFICATION & STREAMING AEAD

### 4.1 Container Layout (`.eltkbackup`)
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

### 4.2 Streaming AEAD Processing for Large Archives
- **Problem**: Processing multi-gigabyte evidence backups by loading entire archives into RAM causes `OutOfMemoryError` on Android mobile devices.
- **Solution**: Backups use chunked stream encryption via Java JCE `CipherInputStream` and `CipherOutputStream` (`AES/GCM/NoPadding`).
- **Unauthenticated Plaintext Prevention**:
  - During restore, data is unpacked through `CipherInputStream` into isolated staging (`cache/restore_staging/`).
  - Staged files are **NOT** committed to active storage or parsed by database engines until `CipherInputStream.read()` reaches EOF and successfully verifies the trailing 128-bit GCM authentication tag. If tag verification fails, the staging directory is purged instantly.

---

## 5. KEY AND CREDENTIAL LIFECYCLE

- **Device Key Invalidation**: If Android OS invalidates KeyStore biometrics (e.g., new fingerprint enrolled), `KEK_keystore` becomes unuseable. The app prompts for Master Passphrase or Recovery Seed to unwrap `DEK` and re-generate `KEK_keystore`.
- **Passphrase Rotation**: Old passphrase unwraps `DEK` -> new `KEK_passphrase` derived -> `DEK` re-wrapped into `wrapped_dek_passphrase.bin`. Database payloads remain untouched.
- **Lost Recovery Credentials**: Fails closed. If user loses both Master Passphrase and BIP39 seed, workspace data is permanently unrecoverable (zero backdoor).

---

## 6. STORAGE TECHNOLOGY EVALUATION

| Evaluation Dimension | Approach A: Standard Android SQLite + AEAD Payload Encryption (`android.database.sqlite`) | Approach B: SQLCipher for Android (`net.zetetic:android-database-sqlcipher`) |
| :--- | :--- | :--- |
| **Dependencies & Financial Cost** | **$0 added dependencies.** Built directly into Android SDK. Zero third-party library maintenance risk. | **$0 open-source library.** Introduces third-party dependency maintenance requirement. |
| **Binary Footprint** | **0 MB increase.** Zero native JNI binaries added to APK. | **~12 MB increase.** Requires bundling native JNI binaries (`libsqlcipher.so`). |
| **Exposed Metadata Risks** | Structural metadata visible (headers, table/column names, row counts). Field payloads 100% AES-256-GCM encrypted. | **Zero metadata exposure.** 100% of SQLite database pages encrypted on disk via AES-256-CBC / HMAC. |
| **Maintenance & Compatibility** | High stability across minSdk 26 to targetSdk 35 managed by Android OS vendor updates. | Requires ongoing NDK JNI binary updates for targetSdk 35 rules and 64-bit alignment checks. |
| **Recommendation & Trade-Off** | **PROPOSED BASELINE**: Standard Android SQLite + AEAD Payload Encryption provides zero dependency risk and 0MB footprint while fully protecting sensitive content payloads. Abstracted behind `EncryptedStorageRepository` interface. | **UNRESOLVED CHOICE**: Pending explicit user selection if full page header obfuscation becomes mandatory. |

---

## 7. THREAT MODEL AND KNOWN LIMITATIONS

1. **Physical Theft / Flash Extraction**: Protected via AES-256-GCM (`DEK`). Unwrapping requires Master Passphrase, Recovery Seed, or KeyStore biometric unlock.
2. **Co-located Unprivileged App Access**: Protected via Android OS app sandboxing (`context.getFilesDir()`) and payload encryption.
3. **Backup Interception**: Protected via `K_backup` AEAD encryption; zero plaintext payload exported.
4. **OS Kernel & Root Compromise Limitation**: The application **CANNOT** guarantee protection against an OS-level kernel compromise, active spyware, keyloggers, or a fully unlocked physical device with active root access.
5. **Offline Single-Device Rollback Limitation**: Single-device offline replay attacks cannot be prevented solely by cryptographic hash chains. Launch sequence counters in KeyStore preferences and Workspace Timeline Audit Journals serve as mitigations.

---

## 8. TEST MATRIX WITH MEASURABLE ACCEPTANCE CRITERIA

Real legal records remain **STRICTLY PROHIBITED** until 100% of the following automated unit, integration, and instrumentation tests pass in CI using synthetic test fixtures:

| Test Case | Scope | Execution Command / Script | Acceptance Criteria |
| :--- | :--- | :--- | :--- |
| **Correct & Incorrect Passphrases** | Unit (CI) | `./gradlew test --tests "*PassphraseKeyTest*"` | Valid passphrase unwraps `DEK`. Invalid passphrase throws `GeneralSecurityException` without modifying wrapped key files. |
| **Ciphertext Tampering** | Unit (CI) | `./gradlew test --tests "*AeadIntegrityTest*"` | Single bit-flip in ciphertext or AAD header throws `AEADBadTagException` and halts read operation. |
| **Wrong Recovery Material** | Unit (CI) | `./gradlew test --tests "*RecoverySeedTest*"` | Mismatched recovery seed throws `GeneralSecurityException` without corrupting active keys. |
| **Nonce Uniqueness** | Unit (CI) | `./gradlew test --tests "*NonceUniquenessTest*"` | 100,000 encryption cycles produce zero duplicate 12-byte IVs. |
| **Truncated / Malformed Backups** | Integration (CI) | `./gradlew test --tests "*CorruptBackupTest*"` | Malformed archive rejected in `STATE_STAGING`; active database 100% unmodified. |
| **Interrupted Export / Restore** | Instrumentation (Device) | `./gradlew connectedAndroidTest` | Process killed mid-restore; restart automatically restores `files/rollback_backup/` pre-restore snapshot intact. |
| **Replacement-Phone Recovery** | Instrumentation (Device) | `./gradlew connectedAndroidTest` | Clean app instance without KeyStore key recovers complete workspace using 24-word seed; record & content-hash equality verified. |
| **Storage Full Condition** | Integration (CI) | `./gradlew test --tests "*DiskFullTest*"` | Disk full error aborts write transaction without corrupting database headers. |

---

## 9. DECISIONS REQUIRING OWNER APPROVAL [UNRESOLVED]

1. **Storage Engine Choice**: Standard Android SDK SQLite (`android.database.sqlite`) with payload AEAD encryption vs. SQLCipher for Android (`net.zetetic:android-database-sqlcipher`).
2. **Recovery Seed Derivation Standardization**: Standard 24-word BIP39 key derivation (`PBKDF2WithHmacSHA512`, 2048 iterations) vs. custom PBKDF2/HKDF key wrapping scheme.
3. **Password KDF Parameter Benchmarking**: Calibrating PBKDF2/Argon2 iteration parameters via setup benchmark loop on target mobile hardware.
4. **Rollback Attack Defense Mechanism**: User-confirmed launch state timestamps vs. out-of-band sequence numbers.

---

## 10. AUDIT RESPONSE MATRIX

| Reviewer Finding / Blocker | Design Resolution / Section Pointer | Technical Explanation & Mitigation |
| :--- | :--- | :--- |
| **1. Replacement-Phone Recovery** | **Section 2.2** | KeyStore TEE explicitly decoupled. Recovery on clean Instance B uses 24-word BIP39 seed + `PBKDF2WithHmacSHA512` to derive `KEK_recovery` and unwrap `DEK`, generating new local KeyStore keys on phone B. |
| **2. Crash-Safe Restore** | **Section 3.1** | 9-state restore engine isolates unpacking in `cache/restore_staging/`, snapshots active data to `files/rollback_backup/`, and syncs `restore_state.json` via `fsync()`. `Application.onCreate()` automatically restores snapshot upon startup crash detection. |
| **3. Streaming AEAD Backup Format** | **Section 4.2** | Uses `CipherInputStream`/`CipherOutputStream` for streaming archive processing. Unauthenticated plaintext is held in isolated staging until GCM tag reaches EOF, preventing OOM and unauthenticated data leakage. |
| **4. Credential Lifecycle & Older Backups** | **Section 2.2 & Section 5** | Changing passphrase re-wraps `DEK` into `wrapped_dek_passphrase.bin` without altering `DEK` itself, ensuring older backups encrypted under `DEK` or `KEK_recovery` remain 100% recoverable. |
| **5. Storage Engine Trade-Offs** | **Section 6** | Evaluates SDK SQLite payload encryption ($0 cost, 0MB footprint) vs SQLCipher (~12MB JNI footprint). Notes that SDK SQLite exposes structural metadata (table names/row counts) while SQLCipher encrypts 100% of pages. |
| **6. Measurable Test Matrix** | **Section 8** | Establishes test cases across CI unit, integration, and device instrumentation scopes with exact acceptance criteria (content-hash equality, wrong seed rejection, disk-full abort). |
| **7. Platform Rules & Safety** | **Section 1 & Section 7** | Enforces $0 ongoing budget, minSdk 26, Java 17, `FLAG_SECURE` window protection, private `context.getFilesDir()` storage, and 100% fictional test data policy. |
