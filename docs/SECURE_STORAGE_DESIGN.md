# ELITE KIT — SECURE STORAGE & RECOVERY DESIGN SPECIFICATION

Document Path: docs/SECURE_STORAGE_DESIGN.md
Version: 1.0-proposed
Authoritative Baseline: dbd4153ca39ede69890ebaf47feaab7f393c3c5a
Governing Authority: MASTER_CONTROL.md

---

## 1. Status and Scope

- **Implementation Status**: [PROPOSED] Architecture Specification. All cryptographic storage capabilities, key wrapping mechanisms, backup/restore engines, and recovery seed workflows described herein are NOT IMPLEMENTED in application source code.
- **Scope**: Local-first Android native application (`com.elitekit.app`), targeting Java 17, Android API 26+ (minSdk 26, targetSdk 35, compileSdk 35), $0 tooling cost, preserving the `main` branch and existing GitHub Actions workflows (`.github/workflows/main.yml` and `.github/workflows/android-smoke-test.yml`).
- **Data Policy**: 100% synthetic fictional test data during all development phases. Real confidential legal records remain **STRICTLY PROHIBITED** until every security capability, key derivation layer, encrypted backup mechanism, safe restore pipeline, replacement-phone recovery workflow, and required automated test suite has been implemented and verified passing in CI.
- **Repository Baseline Reality**: The current codebase contains a minimal native Java app shell (`MainActivity.java`). Encrypted database tables, key wrapping mechanisms, backup generators, restore engines, and recovery seed modules are **NOT IMPLEMENTED**.

---

## 2. Threat Model and Security Boundaries

### 2.1 Asset Inventory & Protection Targets
- **Structured Workspace Data**: Client records, legal case notes, matter metadata, chronological events stored in SQLite.
- **Evidence Media & Files**: Imported documents (PDF, DOCX), images (PNG, JPEG), and audio recordings.
- **Master Encryption Keys**: Symmetric data encryption keys (`DEK`) and key encryption keys (`KEK`).

### 2.2 Threat Vectors & Boundary Definitions
1. **Physical Device Theft / Unlocks**: Attacker gains physical possession of unlocked phone.
   - *Boundary*: Android Keystore hardware protection + Master Passphrase requirement for key unwrapping.
2. **Untrusted Shared Storage / ADB Extractions**: Malware or ADB backup attempts to read raw app database or media files from device storage.
   - *Boundary*: AES-256-GCM application-level payload encryption ensures raw database files and media streams contain zero readable plaintext.
3. **Transit & Cloud Storage Interception**: Interception or unauthorized extraction of backup archives stored on local SD cards or external cloud storage.
   - *Boundary*: Versioned AEAD encrypted backup envelopes authenticated via `K_backup` derived from user credentials.
4. **Malicious Backup Modification / Injection**: Attacker alters header fields or injects malicious entries into backup archives.
   - *Boundary*: Additional Authenticated Data (AAD) binding and AEAD authentication tag verification before unpacking.
5. **Rollback / Replay Attacks**: Attacker replaces current active database with a valid older database file to restore previously deleted records.
   - *Boundary*: Monotonically increasing sequence counters / user-confirmed startup timestamps.

---

## 3. Storage Options & Encryption Architecture

### 3.1 Maintained Component Evaluation
- **Option A: Full-Database Page Encryption (SQLCipher for Android)** [PROPOSED ALTERNATIVE]:
  - *Status*: Maintained open-source library (`net.zetetic:android-database-sqlcipher`).
  - *Mechanism*: Encrypts every SQLite database page (headers, B-tree indexes, table schemas, row IDs, write-ahead logs) via AES-256.
  - *Trade-offs*: Provides complete metadata obfuscation. However, it introduces C/C++ JNI native binaries (`.so` files), increasing APK size by ~12MB and adding native toolchain maintenance overhead.
- **Option B: Standard SQLite + Application-Level AES-256-GCM Payload Encryption** [PROPOSED BASELINE]:
  - *Status*: Built-in Android SDK SQLite (`android.database.sqlite`) + Java 17 standard cryptography (`javax.crypto`).
  - *Mechanism*: Encrypts all record fields, notes, client metadata, and text payloads using AES-256-GCM (`AES/GCM/NoPadding`) prior to database insertion.
  - *Trade-offs*: $0 added dependencies, zero native binary overhead, lightweight, hardware-accelerated.
  - *Metadata Visibility Notice*: Field encryption **DOES NOT** hide SQLite database headers (`SQLite format 3`), table names, column definitions, index structures, total row counts, primary key sequences, or write-ahead log (WAL) page allocations.

### 3.2 Evidence Media File Encryption
- **Stream Encryption**: All imported evidence files are encrypted on write using AES-256-GCM streaming (`CipherOutputStream` / `CipherInputStream`).
- **File Container Structure**: Encrypted evidence files store an unencrypted header containing magic bytes (`0x454C544B` = `ELTK`), spec version (`0x01`), 16-byte random salt, 12-byte random initialization vector (IV), followed by AES-256-GCM ciphertext and 128-bit AEAD authentication tag.

---

## 4. Key Hierarchy and Android Keystore Role

### 4.1 Non-Circular Key Hierarchy & Wrapping Architecture
To prevent circular dependencies, key roles, derivation paths, and wrapping mechanisms are strictly ordered:

1. **Workspace Data Encryption Key (`DEK`)**:
   - Cryptographically random 256-bit AES symmetric key (`SecureRandom`) generated once on workspace creation. Encrypts database record payloads and evidence files directly. `DEK` is never stored unencrypted. NOT IMPLEMENTED.
2. **Passphrase Key Encryption Key (`KEK_passphrase`)**:
   - Derived from normalized Master Passphrase + 16-byte random salt (`salt_passphrase.bin`) via `PBKDF2WithHmacSHA256` (200,000 provisional iterations). Wraps `DEK` as `wrapped_dek_passphrase.bin`. NOT IMPLEMENTED.
3. **Recovery Key Encryption Key (`KEK_recovery`)**:
   - Derived from NFKD-normalized 12/24-word BIP39 Recovery Seed + 16-byte random salt (`salt_recovery.bin`) via `PBKDF2WithHmacSHA256` (200,000 provisional iterations). Wraps `DEK` as `wrapped_dek_recovery.bin`. NOT IMPLEMENTED.
4. **Local Keystore Key Encryption Key (`KEK_keystore`)**:
   - Hardware-backed / TEE key generated inside `AndroidKeyStore` on the local phone. Wraps `DEK` as `wrapped_dek_keystore.bin` for biometric/PIN unlock. *Non-transferable hardware boundary; zero involvement in backup decryption or phone replacement.* NOT IMPLEMENTED.
5. **Backup Envelope Encryption Key (`K_backup`)**:
   - Derived directly from `KEK_passphrase` OR `KEK_recovery` via HKDF-Expand with info string `"EliteKit-BackupPayloadKey-v1"`. Used to encrypt and authenticate the versioned backup archive envelope. NOT IMPLEMENTED.

### 4.2 Android Keystore Limitations & Non-Portability
- `AndroidKeyStore` provides hardware-backed key protection (TEE/StrongBox) preventing key extraction even if the OS is rooted.
- **Hardware Boundary Limitation**: Keys inside `AndroidKeyStore` are bound to the local physical hardware. They **CANNOT** be backed up, exported, or transferred to another phone.
- **Design Rule**: `KEK_keystore` is used exclusively for local biometric quick-unlock (`wrapped_dek_keystore.bin`). **Decryption of backups and replacement-phone recovery MUST NOT rely on `AndroidKeyStore`.**

---

## 5. Passphrase Handling and Replacement-Phone Recovery

### 5.1 Credential Derivation & Unwrapping Flow

| Task | Required Credential | Derivation & Unwrapping Flow | Keystore Dependency |
| :--- | :--- | :--- | :--- |
| **Decrypt Backup Envelope** | Master Passphrase OR BIP39 Recovery Seed | Derive `KEK` via PBKDF2 -> Expand `K_backup` via HKDF -> Decrypt AEAD Backup Envelope | **None** |
| **Unwrap DEK (Replacement Phone)** | Master Passphrase OR BIP39 Recovery Seed | Derive `KEK_passphrase` or `KEK_recovery` -> Decrypt `wrapped_dek_passphrase.bin` or `wrapped_dek_recovery.bin` -> Recover `DEK` | **None** |
| **Quick Daily Unlock (Local Device)** | Device PIN / Biometric | Read `wrapped_dek_keystore.bin` -> Unwrap via `KEK_keystore` in local `AndroidKeyStore` -> Recover `DEK` | Local Device Only |

### 5.2 Replacement-Phone Recovery Procedure (Zero Keystore Reliance)
1. On a clean replacement phone, the user inputs either their Master Passphrase or 12/24-word BIP39 Recovery Seed.
2. The app reads `salt_recovery.bin` (or `salt_passphrase.bin`) from the unencrypted backup header.
3. The app derives `KEK_recovery` (or `KEK_passphrase`), expands `K_backup`, and authenticates/decrypts the backup envelope.
4. The app unwraps `wrapped_dek_recovery.bin` (or `wrapped_dek_passphrase.bin`), recovering the identical 256-bit `DEK`.
5. The app generates a new local `KEK_keystore` inside the replacement phone's `AndroidKeyStore` and re-wraps `DEK` as `wrapped_dek_keystore.bin` to restore biometric quick-unlock on the new device. Zero reliance exists on the old phone's hardware key. NOT IMPLEMENTED.

---

## 6. Encrypted Backup Envelope and Safe Restore Protocol

### 6.1 Versioned Backup Envelope Format (Version `0x01`)
- **Unencrypted Header** (Bytes 0–37):
  - Magic Bytes: `ELITEKIT_BKP` (12 bytes)
  - Spec Version: `0x01` (2 bytes)
  - KDF Algorithm ID: `PBKDF2_SHA256` (2 bytes)
  - KDF Salt: 16 bytes random salt
  - KDF Iterations: 4 bytes (200,000)
  - AEAD IV / Nonce: 12 bytes random IV
- **Authenticated Additional Data (AAD)**: Unencrypted header fields passed to `Cipher.updateAAD()`. Modifying any header parameter invalidates AEAD tag.
- **AEAD Encrypted Payload Body**: AES-256-GCM ciphertext encrypted using `K_backup`, containing `wrapped_dek_recovery.bin`, `database_snapshot.sqlite`, `evidence/` directory, and `manifest.json`.
- **Manifest Protection**: `manifest.json` is encrypted inside the AEAD payload body. Decryption and AEAD tag verification occur **BEFORE** any entry in `manifest.json` is uncompressed, trusted, or unpacked to disk. NOT IMPLEMENTED.

### 6.2 Safe Durable Restore Protocol & Idempotent Crash Recovery
To prevent data loss during restore operations, the system enforces durable state markers written to `restore_state.json` (flushed to disk via `FileOutputStream.getFD().sync()`):

1. **Durable Lifecycle States**:
   - `STATE_0_IDLE`: Normal state.
   - `STATE_1_STAGING`: Unpacking archive to isolated `cache/restore_staging/`.
   - `STATE_2_AUTHENTICATED`: AEAD tag verified, `DEK` unwrapped, manifest digests & schema version validated.
   - `STATE_3_SNAPSHOT_INIT`: Preparing `files/rollback_backup/`.
   - `STATE_4_SNAPSHOT_COMPLETE`: Active workspace copied to `files/rollback_backup/`, verified against `rollback_manifest.json`, and synced (`fsync`).
   - `STATE_5_SWAP_MARKER_WRITTEN`: `restore_state.json` written with durable swap marker and synced (`fsync`). Swap is imminent.
   - `STATE_6_ACTIVATING_STAGED_DATA`: Staged files replacing active files in `files/data/`. (*File-system atomicity like `renameTo()` is NOT assumed*).
   - `STATE_7_SWAP_COMPLETED`: Active workspace update verified. Synced (`fsync`).
   - `STATE_8_CLEANUP`: Purging `files/rollback_backup/` and `cache/restore_staging/`.
   - `STATE_0_IDLE`: Reset to idle.

2. **Crash Recovery & Interruption Handling**:
   - **Crash during States 1–3 (`STAGING`, `AUTHENTICATED`, `SNAPSHOT_INIT`)**: Startup recovery sees `STATE_3` or earlier without complete snapshot marker. Purges `cache/restore_staging/` and incomplete `files/rollback_backup/`. Active workspace was untouched.
   - **Crash during State 5 or 6 (`SWAP_MARKER_WRITTEN` / `ACTIVATING_STAGED_DATA`)**: Startup recovery detects `STATE_5` or `STATE_6`. It reads complete `rollback_manifest.json` in `files/rollback_backup/`, copies original files back to `files/data/`, verifies checksums, flushes disk (`fsync`), purges staging, clears state markers, and resets to `STATE_0_IDLE`. **Existing workspace is 100% restored.**
   - **Crashes during Rollback itself**: Idempotent rollback handler. Every file copy operation checks `rollback_manifest.json`. Retrying rollback after repeated crashes executes identical, stateless restore operations.
   - **Crash during Cleanup (State 8)**: Swap was completed (`STATE_7_SWAP_COMPLETED`). Startup recovery finishes purging `rollback_backup/` and resets to `STATE_0_IDLE`. NOT IMPLEMENTED.

3. **Handling Total Key Loss**:
   - If user loses Master Passphrase AND BIP39 recovery seed, data is mathematically unrecoverable. Zero backdoor recovery paths exist.

---

## 7. Fictional-Data Test Requirements & Release Gates

Real legal records remain **STRICTLY PROHIBITED** until 100% of the following automated unit, integration, and security tests pass in CI using synthetic test fixtures:

1. **Replacement-Phone Recovery Test**: Export backup from Instance A; perform clean install on Instance B; recover using ONLY backup archive and BIP39 recovery seed; verify 100% byte-for-byte matching of database records and evidence files. [NOT IMPLEMENTED]
2. **Wrong Credentials Test**: Attempt unwrapping `DEK` or decrypting backup with invalid passphrase or seed; verify `GeneralSecurityException` is thrown without corrupting active workspace. [NOT IMPLEMENTED]
3. **Modified Header & AEAD Tampering Test**: Modify single bit in header AAD or ciphertext payload; verify immediate `AEADBadTagException` prior to parsing or unpacking entries. [NOT IMPLEMENTED]
4. **Modified Ciphertext & Manifest Test**: Inject tampered file or alter `manifest.json` inside ciphertext; verify AEAD authentication failure blocks restore before disk unpack. [NOT IMPLEMENTED]
5. **Unsupported Backup Version Test**: Attempt opening backup envelope with unsupported spec version flag (`0xFF`); verify clean compatibility error response. [NOT IMPLEMENTED]
6. **Interrupted Snapshot Creation Test**: Force-kill process mid-way through `STATE_3_SNAPSHOT_INIT`; verify startup handler detects incomplete snapshot, purges snapshot, and preserves active workspace. [NOT IMPLEMENTED]
7. **Interrupted Marker Write Test**: Force-kill process while writing state markers; verify atomic file write prevents corrupt marker files and defaults safely to previous state. [NOT IMPLEMENTED]
8. **Crashes During Activation & Rollback Test**: Force-kill process during `STATE_6_ACTIVATING_STAGED_DATA` and during rollback recovery itself; verify idempotent recovery restores previous workspace 100% intact. [NOT IMPLEMENTED]
9. **Repeated Crash Loop Recovery Test**: Simulate repeated crashes during startup recovery execution; verify idempotent handler cleanly completes rollback without cascading data corruption. [NOT IMPLEMENTED]

---

## 8. Unresolved Design Decisions Requiring Review [PROPOSED]

1. **Storage Component Choice**: Standard SQLite + application-level AES-256-GCM record payload encryption ($0 added dependencies, zero native binaries) vs SQLCipher for Android (full-database page encryption, ~12MB native binary footprint). [PROPOSED]
2. **Password KDF Selection**: Standard Java 17 `PBKDF2WithHmacSHA256` ($0 native libraries) vs Argon2id (requires third-party JNI native build). [PROPOSED]
3. **KDF Parameter Benchmarking**: Establishing benchmark thresholds (e.g. 300ms-500ms max unlock latency on target mobile hardware) to finalize PBKDF2/Argon2 iteration parameters. [PROPOSED]
4. **Recovery Mnemonic Word Count**: 12-word BIP39 mnemonic seed (128-bit seed entropy) vs 24-word BIP39 mnemonic seed (256-bit seed entropy). [PROPOSED]
5. **Rollback Attack Checkpoint Policy**: Using user-confirmed state timestamps on launch vs storing an out-of-band sequence log in the offline recovery kit. [PROPOSED]
