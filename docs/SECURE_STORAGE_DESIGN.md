# ELITE KIT — SECURE STORAGE & RECOVERY DESIGN SPECIFICATION

Document Path: docs/SECURE_STORAGE_DESIGN.md
Version: 1.1-proposed
Authoritative Baseline: dbd4153ca39ede69890ebaf47feaab7f393c3c5a
Governing Authority: MASTER_CONTROL.md

---

## 1. Status and Scope

- **Implementation Status**: [PROPOSED — NOT IMPLEMENTED] Architecture Specification. All cryptographic storage capabilities, key wrapping mechanisms, backup/restore engines, and recovery seed workflows described herein are NOT IMPLEMENTED in application source code.
- **Scope**: Local-first Android native application (`com.elitekit.app`), targeting Java 17, Android API 26+ (minSdk 26, targetSdk 35, compileSdk 35), $0 tooling cost, preserving the `main` branch and existing GitHub Actions workflows (`.github/workflows/main.yml` and `.github/workflows/android-smoke-test.yml`).
- **Data Policy**: 100% synthetic fictional test data during all development phases. Real confidential legal records remain **STRICTLY PROHIBITED** until every security capability, key derivation layer, encrypted backup mechanism, safe restore pipeline, replacement-phone recovery workflow, and required automated test suite has been implemented and verified passing in CI.
- **Repository Baseline Reality**: The current codebase contains a minimal native Java app shell (`MainActivity.java`). Encrypted database tables, key wrapping mechanisms, backup generators, restore engines, and recovery seed modules are **NOT IMPLEMENTED**.

---

## 2. Threat Model

1. **Lost or Stolen Phone**: Attacker acquires physical device in locked or unlocked state.
   - *Boundary*: Android Keystore hardware boundary + user Master Passphrase required to unwrap `DEK`.
2. **Unauthorized App Access / Shared Device**: Unprivileged app or user on local device attempts to inspect app storage.
   - *Boundary*: Biometric / PIN unlock gate protecting local Keystore wrapping key (`KEK_keystore`).
3. **Backup Theft or Disclosure**: Interception or extraction of backup files exported to local SD card or cloud storage.
   - *Boundary*: AEAD authenticated encryption (`K_backup`) derived from user credentials; no plaintext payload exported.
4. **Lost Device Keys or Recovery Material**: Device destroyed or recovery seed forgotten.
   - *Boundary*: Independent wrapping paths via `KEK_passphrase` and `KEK_recovery` (BIP39 seed). If user loses both passphrase and seed, data is unrecoverable (zero backdoor).
5. **Corrupted, Incomplete, or Malicious Backup**: Tampered headers, bit rot, or injected entries in backup archive.
   - *Boundary*: Additional Authenticated Data (AAD) header binding and 128-bit AEAD tag verification before disk extraction or parsing.
6. **Replacement-Phone Recovery**: Transferring encrypted workspace to a clean replacement device.
   - *Boundary*: Pure credential-derived unwrapping (`KEK_recovery` or `KEK_passphrase`) with zero reliance on the old device's Keystore key.
7. **Rollback, Truncation, and Deletion Risks**: Replaying an older valid database file to restore deleted entries or truncating database state.
   - *Boundary*: Monotonically increasing sequence counters / user-confirmed state timestamps on launch.

---

## 3. Data Architecture

1. **Separate Protection Requirements**:
   - *Structured Database Data*: Client details, case notes, timeline events stored in SQLite. Protected via field-level or page-level AES-256 encryption.
   - *Imported Evidence Files*: Documents (PDF), images (PNG/JPG), audio evidence streams stored in private file storage (`context.getFilesDir()`). Stream-encrypted on write via AES-256-GCM (`CipherOutputStream`).
2. **Preserve Original Evidence**:
   - Imported evidence files are written to immutable encrypted file containers. Modifications create versioned child records; original evidence files are never overwritten.
3. **Transactional Storage Operations**:
   - Database writes use explicit SQLite transactions (`beginTransaction()`, `setTransactionSuccessful()`, `endTransaction()`).
   - File updates use temporary file write -> disk sync (`fsync`) -> file swap patterns.
4. **Schema Versioning & Migrations**:
   - Database maintains explicit `PRAGMA user_version`. Every schema migration is versioned, deterministic, and tested against clean rollback snapshots.

---

## 4. Key Hierarchy

### 4.1 Non-Circular Key Separation & Roles [PROPOSED]
1. **Workspace Data Encryption Key (`DEK`)**:
   - Cryptographically random 256-bit AES symmetric key (`SecureRandom`) generated on workspace creation. Encrypts database record payloads and evidence files directly. Never written to disk in unencrypted plaintext. NOT IMPLEMENTED.
2. **Passphrase Key Encryption Key (`KEK_passphrase`)**:
   - 256-bit symmetric key derived from Master Passphrase + `salt_passphrase.bin` via `PBKDF2WithHmacSHA256` (200,000 provisional iterations). Wraps `DEK` as `wrapped_dek_passphrase.bin`. NOT IMPLEMENTED.
3. **Local Keystore Key Encryption Key (`KEK_keystore`)**:
   - Hardware-backed / TEE key generated inside `AndroidKeyStore`. Wraps `DEK` as `wrapped_dek_keystore.bin` for biometric/PIN unlock on local device. *Device-bound hardware key; zero involvement in backup decryption or phone replacement.* NOT IMPLEMENTED.
4. **Recovery Key Encryption Key (`KEK_recovery`)**:
   - 256-bit key derived from NFKD-normalized 12/24-word BIP39 Recovery Seed + `salt_recovery.bin` via `PBKDF2WithHmacSHA256` (200,000 provisional iterations) or standard BIP39 derivation. Wraps `DEK` as `wrapped_dek_recovery.bin`. NOT IMPLEMENTED.
5. **Backup Envelope Encryption Key (`K_backup`)**:
   - Derived directly from `KEK_passphrase` OR `KEK_recovery` via HKDF-Expand with info label `"EliteKit-BackupPayloadKey-v1"`. Used to encrypt and authenticate versioned backup envelopes. NOT IMPLEMENTED.

### 4.2 Storage & Passphrase Rules
- Plaintext keys or passphrases are **NEVER** stored in source code, logs, preferences, screenshots, or backups.
- Passphrase character arrays (`char[]`) in memory are zeroed out (`Arrays.fill(pwd, '\0')`) immediately after derivation.

### 4.3 Key Loss & Passphrase Rotation Behavior
- **Passphrase Change**: `DEK` remains constant. User inputs old passphrase, unwraps `DEK`, derives new `KEK_passphrase` from new passphrase, and re-wraps `DEK` as `wrapped_dek_passphrase.bin`. No database re-encryption required.
- **Key Loss**: If user loses Master Passphrase AND BIP39 recovery seed, data is unrecoverable. No backdoor recovery exists.
- **Recovery Disclaimer**: Recovery capabilities **CANNOT** be claimed operational until replacement-phone recovery tests pass in clean test environments.

---

## 5. Cryptographic Requirements

1. **Maintained Implementations First**:
   - Standard Java 17 / Android JCE (`javax.crypto.Cipher`, `java.security.SecureRandom`, `javax.crypto.SecretKeyFactory`).
   - SQLite: Evaluation comparing standard Android SDK `android.database.sqlite` vs open-source maintained `SQLCipher for Android` (`net.zetetic:android-database-sqlcipher`).
2. **Authenticated Encryption (AEAD)**:
   - All payload encryption uses AES-256-GCM (`AES/GCM/NoPadding`) with a 128-bit authentication tag.
3. **Nonce / IV Uniqueness**:
   - 12-byte (96-bit) cryptographically random IV generated fresh for every encryption operation via `SecureRandom`. Never reuse IV across encryptions under the same key.
4. **Key Derivation (KDF)**:
   - Password-based KDF (`PBKDF2WithHmacSHA256` or `Argon2id`). Low-entropy passwords MUST NOT use HKDF directly. HKDF is used strictly for key expansion from derived KEKs. KDF iteration counts are provisional pending hardware benchmarking.
5. **Authentication Failure Handling**:
   - Mismatched passphrases, altered ciphertext, or corrupted headers trigger immediate `AEADBadTagException` or `GeneralSecurityException`, halting extraction prior to database or file mutation.
6. **Limits of Hashes**:
   - SHA-256 digests are treated as integrity checksums (bit rot detection), **NOT** proof of authenticity or origin. AEAD authentication tags enforce authenticity. Rollback protection requires sequence/timestamp counters.

---

## 6. Backup and Restore

1. **User-Controlled Encrypted Export**:
   - Backups are created strictly upon explicit user request.
   - No automatic background cloud upload. Exports produce versioned `.eltkbackup` archives.
2. **Validation Before Restore**:
   - The backup envelope header is authenticated via AAD, `K_backup` decrypts payload, and `manifest.json` SHA-256 digests & schema versions are validated **BEFORE** unpacking entries to disk.
3. **Staged Restore & Data Preservation**:
   - Unpacking occurs into isolated staging (`cache/restore_staging/`).
   - Active workspace (`files/data/`) is snapshotted to `files/rollback_backup/` and tracked with `restore_state.json` synced via `fsync()`.
   - If validation or restore fails or process crashes, startup recovery restores active files from `files/rollback_backup/`. Existing workspace is never destroyed on restore failure.
4. **Replacement-Phone Recovery Procedure**:
   - User inputs BIP39 seed or passphrase on clean device -> derives `KEK_recovery` or `KEK_passphrase` -> expands `K_backup` -> decrypts envelope -> unwraps `wrapped_dek_recovery.bin` -> recovers `DEK` -> generates local `KEK_keystore` -> re-wraps `DEK` locally as `wrapped_dek_keystore.bin`.

---

## 7. Approval Gates

- Explicit modal user approval is **MANDATORY** for:
  1. Destructive actions (deleting legal matters, purging evidence).
  2. Backup restoration (replacing active workspace files).
  3. Key rotation or recovery seed regeneration.
  4. Schema migrations and release publishing.
- The system will **NEVER** silently delete original evidence files, active databases, or backup archives.

---

## 8. Test Plan (Fictional Data Only)

Real legal records remain **STRICTLY PROHIBITED** until 100% of the following automated unit, integration, and security tests pass in CI using synthetic test fixtures:

1. **Correct & Incorrect Passphrase Test**: Verify valid passphrase unwraps `DEK`; verify invalid passphrase triggers `GeneralSecurityException` without corrupting keys. [NOT IMPLEMENTED]
2. **Ciphertext Confidentiality & AEAD Failure Test**: Verify bit-flip in ciphertext/AAD tag throws `AEADBadTagException` and blocks read. [NOT IMPLEMENTED]
3. **Backup Export/Import Round-Trip Test**: Export backup, restore into clean environment, confirm byte-for-byte record and media parity. [NOT IMPLEMENTED]
4. **Corrupt, Truncated, & Modified Backup Rejection Test**: Attempt importing tampered archive; verify clean rejection before disk unpack. [NOT IMPLEMENTED]
5. **Clean Installation / Second Device Recovery Test**: Perform clean install on Instance B, recover using BIP39 seed alone, verify complete workspace access. [NOT IMPLEMENTED]
6. **Restore Failure & Workspace Preservation Test**: Force crash mid-restore (`ACTIVATING_STAGED_DATA`); verify startup recovery restores pre-restore workspace 100% intact. [NOT IMPLEMENTED]
7. **Key Rotation & Schema Migration Test**: Rotate passphrase, run schema migration script; verify data remains accessible and schema updates successfully. [NOT IMPLEMENTED]

---

## 9. Implementation Stages

1. **Stage 1**: Resolve architectural design decisions (Storage Engine choice, KDF choice).
2. **Stage 2**: Small, reviewable implementation increments (Increments 1 through 7).
3. **Stage 3**: Automated CI test suite and acceptance criteria verification for each increment.
4. **Stage 4**: Preserve existing workflows (`main.yml` and `android-smoke-test.yml`) throughout.

---

## 10. Unresolved Decisions [PROPOSED]

1. **Database/Storage Technology Choice**: Standard SQLite + application-level AES-256-GCM payload encryption ($0 added dependencies, zero native binaries) vs SQLCipher for Android (full-database page encryption, ~12MB native binary footprint). [PROPOSED]
2. **Key Hierarchy and Recovery Format**: Standard BIP39 derivation (`PBKDF2WithHmacSHA512`, 2048 iterations) vs custom PBKDF2/HKDF wrapping scheme. [PROPOSED]
3. **Backup Envelope File Format**: Versioned AEAD ZIP container vs custom binary stream layout. [PROPOSED]
4. **Test Execution Environment**: Local terminal `./gradlew test` vs GitHub Actions CI test runner configuration. [PROPOSED]
5. **Library Maintenance & MinSdk 26 Compatibility**: Verifying Android API 26+ compatibility and maintenance status for all third-party dependencies before inclusion. [PROPOSED]
