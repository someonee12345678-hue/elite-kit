STATUS: PROPOSED ARCHITECTURE AUDIT & DESIGN SPECIFICATION — NOT IMPLEMENTED
BASELINE: dbd4153ca39ede69890ebaf47feaab7f393c3c5a
SCOPE: Local-First Android-Native Legal Workspace (`com.elitekit.app`)
COST LIMIT: $0
DATA POLICY: Synthetic Fictional Test Data Only

# ELITE KIT — WHOLE-SYSTEM ARCHITECTURE AUDIT & SECURE STORAGE SPECIFICATION

Document Path: `docs/SECURE_STORAGE_DESIGN.md`
Authoritative Baseline SHA: `dbd4153ca39ede69890ebaf47feaab7f393c3c5a`
Governing Authority: `MASTER_CONTROL.md`

---

## 1. CURRENT-STATE FINDINGS (WITH FILE PATHS)

At baseline `dbd4153ca39ede69890ebaf47feaab7f393c3c5a`, the repository is a minimal native Android shell. The whole-system audit mapped the following source artifacts:

- **Application Activity Shell**: `app/src/main/java/com/elitekit/app/MainActivity.java`
  *Finding*: Minimal native Java activity rendering static text warning that security and encryption are NOT implemented yet.
- **Application Manifest**: `app/src/main/AndroidManifest.xml`
  *Finding*: Standard Android manifest (`minSdk 26`, `targetSdk 35`, `compileSdk 35`, single Activity `MainActivity`). Excludes cloud backup (`android:allowBackup="false"`).
- **Gradle Build Configuration**: `build.gradle.kts`, `app/build.gradle.kts`, `settings.gradle.kts`
  *Finding*: AGP 8.7.3 Gradle Kotlin DSL configuration targeting Java 17 compatibility. Zero third-party security or database dependencies configured.
- **CI/CD Workflows**: `.github/workflows/main.yml`, `.github/workflows/android-smoke-test.yml`
  *Finding*: Basic GitHub Actions workflow running `./gradlew test` and smoke build. Preserved 100% intact.
- **Governing Protocol**: `MASTER_CONTROL.md`
  *Finding*: Establishes non-negotiable rules for local-first operations, $0 budget, fictional test data, and user authorization release gates.
- **Field Documentation**: `docs/Docs/PHONE_FIELD_GUIDE.md`
  *Finding*: Documents phone-first operational procedures.

---

## 2. IMPLEMENTED-VERSUS-MISSING CAPABILITY MATRIX

| Capability Area | Status | Current Implementation Artifacts | Gaps / Required Implementation |
| :--- | :--- | :--- | :--- |
| **App Lifecycle & Shell** | **IMPLEMENTED** | `MainActivity.java`, `AndroidManifest.xml` | Single native Activity shell present. |
| **Build System & CI** | **IMPLEMENTED** | `build.gradle.kts`, `.github/workflows/*` | AGP 8.7.3 & GitHub Actions workflows active. |
| **Governance & Policy** | **IMPLEMENTED** | `MASTER_CONTROL.md` | $0 budget & fictional test data policies enforced. |
| **Key Hierarchy & Wrapping** | **MISSING** | None | No KeyStore wrapping, PBKDF2 key derivation, or DEK management. |
| **Replacement-Phone Recovery** | **MISSING** | None | No 24-word BIP39 seed derivation or zero-KeyStore unwrapping chain. |
| **Encrypted Storage Engine** | **MISSING** | None | No SQLite AEAD payload layer or SQLCipher integration. |
| **Streaming AEAD Backup** | **MISSING** | None | No `.eltkbackup` container exporter/importer or Tink streaming AEAD. |
| **Transactional Restore** | **MISSING** | None | No crash-safe 7-state restore state machine or staging rollback. |
| **Audit Journal & Hash Chain** | **MISSING** | None | No local tamper-evident audit journal or timeline tracking. |
| **Local AI Capabilities** | **MISSING** | None | Zero device-local AI engines present. AI capabilities disabled/unverified. |

---

## 3. ARCHITECTURE AND DEPENDENCY MAP

### 3.1 Component Layering
```
+-------------------------------------------------------------------+
|                    UI Layer (Activity / Views)                    |
|       - Single Approval Dialogs for Destructive Actions           |
|       - Key Management UI (Passphrase Entry, Seed Display)        |
+-------------------------------------------------------------------+
                                  |
                                  v
+-------------------------------------------------------------------+
|                 Workspace Service Layer & Restore SM              |
|       - Transactional Restore Engine (7-State Crash Safe)         |
|       - Audit Journal & Launch Sequence Counter                   |
+-------------------------------------------------------------------+
                                  |
       +--------------------------+--------------------------+
       |                                                     |
       v                                                     v
+-----------------------------+               +-----------------------------+
|    Secure Storage Engine    |               |  Backup & Recovery Engine   |
| - SQLite Payload AEAD       |               | - Google Tink Streaming AEAD|
| - AES-256-GCM (DEK)         |               | - `.eltkbackup` Container   |
+-----------------------------+               +-----------------------------+
               |                                             |
               +----------------------+----------------------+
                                      |
                                      v
+-------------------------------------------------------------------+
|                 Key Management System (KMS / Keyring)             |
|  - Android KeyStore TEE (KEK_keystore)                            |
|  - Passphrase PBKDF2WithHmacSHA256 (KEK_passphrase)               |
|  - 24-Word BIP39 PBKDF2WithHmacSHA512 (KEK_recovery)              |
+-------------------------------------------------------------------+
```

### 3.2 Non-Circular Key Flow & Replacement-Phone Recovery Chain

```
[ Primary Unlock Flow (Device A) ]
Master Passphrase ──> PBKDF2WithHmacSHA256 ──> KEK_passphrase ──┐
                                                                 ├──> unwraps ──> DEK (256-bit AES)
Android KeyStore TEE ────────────────────────> KEK_keystore ──┘                │
                                                                                v
                                                                      SQLite DB & Workspace

[ Replacement-Phone Recovery Chain (Device B - Zero KeyStore Access) ]
24-Word Recovery Seed Material ──> PBKDF2WithHmacSHA512 ──> KEK_recovery
                                                                  │
                                                                  v unwraps
                                                    wrapped_dek_recovery.bin (from backup)
                                                                  │
                                                                  v
                                                        DEK (256-bit AES)
                                                                  │
                                                                  v generates
                                                 New Android KeyStore KEK_keystore (Device B)
```

1. **Passphrase Modification Lifecycle**:
   - Modifying the Master Passphrase re-derives `KEK_passphrase_new`.
   - `DEK` is re-wrapped into `wrapped_dek_passphrase.bin` on local storage.
   - `DEK` itself is **NEVER CHANGED**. Consequently, historical backups encrypted under `DEK` or `KEK_recovery` remain 100% valid and recoverable.

---

## 4. BACKUP FORMAT & CRASH-SAFE RESTORE SPECIFICATION

### 4.1 Streaming AEAD Backup Format (`.eltkbackup`)
To eliminate out-of-memory errors on large workspaces, the format adopts Google Tink's established `AesGcmHkdfStreaming` (AES-256-GCM-HKDF with 4KB payload chunking):

```
+-----------------------------------------------------------------------+
| Magic Header: "ELITEKIT_BKUP_V1" (16 bytes)                           |
+-----------------------------------------------------------------------+
| Format Version: 0x0001 (uint16, 2 bytes)                              |
+-----------------------------------------------------------------------+
| Cipher Suite: AES-256-GCM-HKDF-4KB (uint16, 2 bytes)                  |
+-----------------------------------------------------------------------+
| Salt Material: KDF Salt (32 bytes)                                    |
+-----------------------------------------------------------------------+
| Payload Length: Encrypted Payload Size in Bytes (uint64, 8 bytes)     |
+-----------------------------------------------------------------------+
| Streaming AEAD Payload: Chunked 4KB Ciphertext Segments + Tags         |
+-----------------------------------------------------------------------+
```

*Authentication Guarantee*: Plaintext payload is written exclusively into `cache/restore_staging/` and is **NEVER** exposed to application data storage until all chunk AEAD authentication tags are fully validated at EOF.

### 4.2 Crash-Safe 7-State Transactional Restore Engine

```
 [ STATE_IDLE ] ──(Select Backup)──> [ STATE_VERIFYING ]
                                            │
                                            v (AEAD Tag & Manifest OK)
                                     [ STATE_PROPOSING ]
                                            │
                                            v (User Explicitly Approves)
                                     [ STATE_SNAPSHOT ] ──> Copy files/data/ to files/rollback_backup/
                                            │
                                            v
                                     [ STATE_ACTIVATING ] ──> Atomic swap staging -> files/data/
                                            │
                                  +---------+---------+
                                  |                   |
                            (Success)             (Failure / Process Death)
                                  v                   v
                         [ STATE_COMPLETE ]   [ STATE_ROLLBACK ] ──> Restore files/rollback_backup/
                                  │                   │
                                  +---------+---------+
                                            │
                                            v
                                   [ STATE_CLEANUP ] ──> Delete staging & snapshot dirs
```

- **Process Death & Power Loss Mitigation**: State transitions are written to `restore_state.json` followed by `fsync()`. If the app crashes or loses power during `STATE_ACTIVATING`, `Application.onCreate()` checks `restore_state.json` upon restart, detects the incomplete activation, and immediately restores `files/rollback_backup/` to guarantee zero data loss.
- **Insufficient Disk Space Guard**: Before initiating restore, disk space must be at least **2.5x** the archive size (1.0x staging + 1.0x rollback snapshot + 0.5x buffer). If storage is insufficient, restore halts in `STATE_VERIFYING` with zero state changes.

---

## 5. THREAT MODEL AND OPERATIONAL SAFETY

1. **Android Backup Exclusions**: `android:allowBackup="false"` and `android:fullBackupContent="false"` in `AndroidManifest.xml` prevent OS auto-cloud syncing of unencrypted keys or files.
2. **Secret Leakage Prevention**:
   - `FLAG_SECURE` enabled on window layouts to block screenshot captures.
   - Clipboard auto-cleared after 30 seconds if recovery seeds or passphrases are copied.
   - Zero sensitive key material or credentials logged to Logcat.
3. **Memory Zeroization**: Best-effort in-memory byte array overwrites (`Arrays.fill(keyBytes, (byte) 0)`) immediately after key unwrapping operations.
4. **Offline Single-Device Rollback Limitation**: Single-device offline replay attacks cannot be prevented solely by cryptographic hash chains. Launch sequence counters in KeyStore preferences and Workspace Timeline Audit Journals serve as mitigations.

---

## 6. DEPENDENCY-ORDERED IMPLEMENTATION PLAN

### Increment 1: Build & Testing Harness Foundations
- **Files Affected**: `build.gradle.kts`, `app/build.gradle.kts`, `gradle/wrapper/`
- **Scope**: Establish reproducible Gradle build harness and local unit testing framework (JUnit 4/5, Robolectric).
- **Tests Required**: `./gradlew test` smoke test passing.
- **Acceptance Criteria**: CI workflow compiles and runs tests on clean checkout.
- **Rollback Consideration**: Revert `build.gradle.kts` modifications via git.

### Increment 2: Key Management System & Recovery Seed Core
- **Files Affected**: `app/src/main/java/com/elitekit/app/crypto/KeyManager.java`, `Bip39Util.java`
- **Scope**: Implement PBKDF2WithHmacSHA256/SHA512 derivation, Android KeyStore AES-GCM wrapping, and 24-word seed unwrapping.
- **Tests Required**: `PassphraseKeyTest`, `RecoverySeedTest`, `NonceUniquenessTest`.
- **Acceptance Criteria**: Valid passphrase/seed unwrapes DEK; invalid passphrase throws `GeneralSecurityException`; 100k nonces yield 0 duplicates.
- **Rollback Consideration**: Remove `crypto/` package files.

### Increment 3: Secure Encrypted Storage Engine
- **Files Affected**: `app/src/main/java/com/elitekit/app/storage/DatabaseHelper.java`
- **Scope**: Implement SQLite AEAD payload encryption layer for local database tables.
- **Tests Required**: `DatabaseEncryptionTest`, `TamperIntegrityTest`.
- **Acceptance Criteria**: Ciphertext stored on disk; bit-flip in database file halts read with `AEADBadTagException`.
- **Rollback Consideration**: Revert database layer to plain SQLite shell.

### Increment 4: Streaming AEAD Backup Exporter & Importer
- **Files Affected**: `app/src/main/java/com/elitekit/app/backup/BackupEngine.java`
- **Scope**: Implement Tink `AesGcmHkdfStreaming` container generator and chunked stream processor.
- **Tests Required**: `BackupRoundTripTest`, `CorruptArchiveTest`.
- **Acceptance Criteria**: Round-trip backup export and import yields identical SHA-256 record hashes; truncated file fails tag verification.
- **Rollback Consideration**: Delete `backup/` package files.

### Increment 5: Crash-Safe 7-State Transactional Restore Engine
- **Files Affected**: `app/src/main/java/com/elitekit/app/backup/RestoreStateMachine.java`, `EliteKitApp.java`
- **Scope**: Implement 7-state machine with `restore_state.json` persistence, disk space pre-check, and `Application.onCreate()` crash recovery.
- **Tests Required**: `InterruptedRestoreTest`, `DiskFullTest`.
- **Acceptance Criteria**: Simulated SIGKILL during `STATE_ACTIVATING` automatically rolls back active dataset intact on next startup.
- **Rollback Consideration**: Revert `RestoreStateMachine.java` and `Application` subclass.

---

## 7. TEST MATRIX & PROPOSED ACCEPTANCE CHECKLIST

*(Note: All listed tests are PROPOSED and UNEXECUTED at baseline `dbd4153ca39ede69890ebaf47feaab7f393c3c5a`)*

| Test Case | Scope | Execution Command | Acceptance Criteria |
| :--- | :--- | :--- | :--- |
| **Passphrase Unwrapping** | Unit (CI) | `./gradlew test --tests "*KeyManagerTest.testPassphrase*"` | Correct passphrase unwraps DEK; wrong passphrase fails gracefully. |
| **Recovery Seed Unwrapping** | Unit (CI) | `./gradlew test --tests "*KeyManagerTest.testSeed*"` | 24-word seed unwraps DEK on clean instance without KeyStore. |
| **Ciphertext Tampering** | Unit (CI) | `./gradlew test --tests "*TamperTest*"` | Bit-flip in ciphertext throws `AEADBadTagException`. |
| **Backup Round-Trip** | Unit (CI) | `./gradlew test --tests "*BackupTest.testRoundTrip*"` | Exported `.eltkbackup` restored to fresh DB produces identical record hashes. |
| **Interrupted Restore Crash Recovery** | Instrumentation | `./gradlew connectedAndroidTest --tests "*RestoreCrashTest*"` | SIGKILL in `STATE_ACTIVATING` auto-restores pre-restore snapshot on restart. |
| **Insufficient Storage Guard** | Integration | `./gradlew test --tests "*DiskFullTest*"` | Restore halts in `STATE_VERIFYING` if storage < 2.5x archive size. |
| **Key Loss Security** | Unit (CI) | `./gradlew test --tests "*KeyLossTest*"` | Total key loss renders ciphertext mathematically unrecoverable without corrupting disk. |

---

## 8. RISKS, UNRESOLVED DECISIONS, AND PROPOSED MITIGATIONS

1. **Storage Engine Choice**:
   - *Option A*: Standard Android SDK SQLite with AES-256-GCM payload encryption ($0 added dependencies, 0MB binary footprint). Exposes table metadata.
   - *Option B*: SQLCipher for Android (`net.zetetic:android-database-sqlcipher`). Full-page encryption, but adds ~12MB native binary footprint.
   - *Mitigation*: Evaluate Option A as baseline default; allow Option B if page-level metadata masking is mandated.
2. **Streaming AEAD Library Dependency**:
   - *Option A*: Google Tink (`com.google.crypto.tink:tink-android`). Tested and peer-reviewed streaming AEAD.
   - *Option B*: Java JCE `CipherOutputStream` with custom HMAC headers. Risks custom cryptographic container bugs.
   - *Mitigation*: Adopt Google Tink for streaming AEAD backup export/import under $0 budget.
3. **KDF Parameter Benchmarking**:
   - *Risk*: Static PBKDF2 iteration counts may cause excessive UI lag on low-end hardware.
   - *Mitigation*: Implement dynamic runtime benchmark on first setup, targeting ~350ms unlock delay.

---

## 9. REVIEW FINDINGS RESPONSE MATRIX

| Reviewer Finding / Blocker | Design Specification Section | Resolution & Mitigation Strategy |
| :--- | :--- | :--- |
| **1. Zero KeyStore Recovery** | **Section 3.2** | KeyStore decoupled from recovery chain. Unwrapping uses 24-word seed + PBKDF2WithHmacSHA512 to recover DEK on clean hardware. |
| **2. Passphrase Rotation & Backup Recovery** | **Section 3.2 (Item 1)** | Passphrase changes re-wrap DEK into `wrapped_dek_passphrase.bin` on disk without mutating DEK. Older backups remain 100% valid. |
| **3. Memory OOM & Unauthenticated Leakage** | **Section 4.1** | Adopted Tink `AesGcmHkdfStreaming` (4KB chunks). Payload isolated in staging until all chunk GCM tags validate. |
| **4. Crash-Safe State Machine & Storage** | **Section 4.2** | 7-state machine with `restore_state.json` persistence, `fsync()`, startup auto-rollback, and 2.5x storage pre-checks. |
| **5. Claim Verification & Fictional Data Policy** | **Section 1, 7, 8** | Explicitly labeled as UNEXECUTED proposals. 100% synthetic test data & $0 budget strictly enforced. |
