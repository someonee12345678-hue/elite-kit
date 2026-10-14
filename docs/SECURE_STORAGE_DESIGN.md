STATUS: PROPOSED SECURE STORAGE SPECIFICATION — NOT IMPLEMENTED
BASELINE: dbd4153ca39ede69890ebaf47feaab7f393c3c5a
PR REFERENCE: Pull Request #1 (`feature/secure-storage-foundation`)
SCOPE: Local-First Secure Storage for Elite Kit (`com.elitekit.app`)
FINANCIAL CONSTRAINT: $0 Ongoing Cost (Zero Paid APIs, Subscriptions, or Cloud Services)
DATA POLICY: 100% Synthetic Fictional Test Data Only

# ELITE KIT — SECURE STORAGE ARCHITECTURE SPECIFICATION

Document Path: `docs/SECURE_STORAGE_DESIGN.md`
Authoritative Baseline SHA: `dbd4153ca39ede69890ebaf47feaab7f393c3c5a`
Governing Authority: `MASTER_CONTROL.md`

---

## 1. KEY HIERARCHY AND REPLACEMENT-DEVICE RECOVERY

### 1.1 Non-Circular Key Flow Architecture

```
[ Primary Unlock Flow (Device A) ]
Master Passphrase ──> PBKDF2WithHmacSHA256 (salt_passphrase.bin) ──> KEK_passphrase ──┐
                                                                                     ├──> unwraps ──> DEK (256-bit AES)
Android KeyStore TEE ──────────────────────────────────────────────> KEK_keystore ──┘                │
                                                                                                     v
                                                                                           SQLite DB & Files

[ Replacement-Phone Recovery Chain (Device B - Zero KeyStore Reliance) ]
24-Word BIP39 Seed Material ──> PBKDF2WithHmacSHA512 (salt_recovery.bin) ──> KEK_recovery ──> unwraps ──> DEK
```

1. **Data Encryption Key (DEK)**: Randomly generated 256-bit AES key. Encrypts local SQLite payload columns and evidence files. `DEK` is generated once on first setup and is never altered when passphrases change.
2. **Key-Encryption Key Passphrase (`KEK_passphrase`)**: Derived from the user's Master Passphrase via `PBKDF2WithHmacSHA256` (100,000+ iterations). Unwraps `wrapped_dek_passphrase.bin` stored in private app storage.
3. **Android KeyStore TEE Key (`KEK_keystore`)**: Non-exportable hardware-backed key generated inside Android KeyStore TEE (`"EliteKit_Keystore_KEK"`). Unwraps `wrapped_dek_keystore.bin` for PIN/biometric fast unlock on Device A.
4. **Recovery Key-Encryption Key (`KEK_recovery`)**: Derived from the 24-word BIP39 recovery mnemonic via `PBKDF2WithHmacSHA512`. Unwraps `wrapped_dek_recovery.bin`.
5. **Replacement-Phone Recovery**: On clean hardware (Device B), entering the 24-word seed derives `KEK_recovery`, which unwraps `wrapped_dek_recovery.bin` from the user's `.eltkbackup` archive, recovering `DEK` directly without access to Device A's KeyStore. Device B then generates a new local KeyStore alias `KEK_keystore` and re-wraps `DEK` locally for biometric unlock on Device B.

---

## 2. BACKUP FORMAT SPECIFICATION (`.eltkbackup`)

```
+-----------------------------------------------------------------------+
| Magic Header: "ELITEKIT_BKUP_V1" (16 bytes)                           |
+-----------------------------------------------------------------------+
| Format Version: 0x0001 (uint16, 2 bytes)                              |
+-----------------------------------------------------------------------+
| Cipher Suite ID: 0x0001 (AES-256-GCM-HKDF-4KB, uint16, 2 bytes)       |
+-----------------------------------------------------------------------+
| KDF Salt: Random KDF Salt (32 bytes)                                  |
+-----------------------------------------------------------------------+
| Wrapped DEK Recovery Blob: wrapped_dek_recovery.bin (80 bytes)        |
+-----------------------------------------------------------------------+
| Payload Size: Encrypted Payload Byte Length (uint64, 8 bytes)         |
+-----------------------------------------------------------------------+
| Streaming AEAD Payload: Chunked 4KB Ciphertext Segments + Tags         |
+-----------------------------------------------------------------------+
```

- **Zero Plaintext Credentials**: Neither Master Passphrases nor 24-word BIP39 seeds are ever stored or exported in backup archives.
- **Key Wrapping Payload**: The archive contains `wrapped_dek_recovery.bin` (AES-256-GCM encrypted `DEK` wrapped under `KEK_recovery`). Unwrapping requires inputting the 24-word seed on restore.

---

## 3. AUTHENTICATION AND STREAMING AEAD PROCESSING

- **Streaming AEAD Container**: Uses Google Tink `AesGcmHkdfStreaming` (AES-256-GCM with HKDF key derivation and 4KB payload segment chunking) to prevent `OutOfMemoryError` on large workspaces.
- **Nonce & IV Uniqueness**: Each 4KB payload segment receives a unique 12-byte IV derived deterministically via HKDF from segment index counters, guaranteeing zero nonce reuse across backup streams.
- **Segment Authentication & Unauthenticated Data Isolation**: Plaintext payload is written exclusively into `cache/restore_staging/`. Staged files are **NEVER** exposed to active application storage or parsed by database engines until all segment GCM authentication tags pass validation at EOF. If any segment fails tag authentication, staging is aborted and purged instantly.

---

## 4. CRASH-SAFE TRANSACTIONAL RESTORE ENGINE

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
                                     [ STATE_ACTIVATING ] ──> Swap staging -> files/data/
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

1. **Storage Pre-Check**: Before restore begins, available disk space must be at least **2.5x** archive size (1.0x staging + 1.0x pre-restore snapshot + 0.5x safety buffer). If storage is insufficient, restore halts in `STATE_VERIFYING` with zero state changes.
2. **Pre-Restore Snapshot**: Active database and evidence files (`files/data/`) are copied to `files/rollback_backup/`. State `STATE_SNAPSHOT` is written to `restore_state.json` and flushed via `FileOutputStream.getFD().sync()`.
3. **Startup Auto-Rollback**: Staged data replaces active data (`files/data/`). If process death or power loss occurs during `STATE_ACTIVATING`, `Application.onCreate()` checks `restore_state.json` upon next restart, detects incomplete activation, and automatically restores `files/rollback_backup/` pre-restore snapshot intact. Damaged archives or interrupted restores never result in data loss or a wiped workspace.

---

## 5. ROLLBACK AND INTEGRITY LIMITATIONS

- **Tamper & Corruption Detection**: AEAD authentication tags (`AES-256-GCM`) guarantee bit-for-bit integrity and authenticity of ciphertext. Any bit-flip or payload alteration throws `AEADBadTagException` and halts processing instantly.
- **Valid Older Backup Replay Limitation**: Cryptographic hash chains and AEAD tags verify archive integrity but **cannot** prevent an attacker with physical storage access from restoring a valid, untampered older backup export (replay attack).
- **Mitigation & Detection**: Local launch sequence counters stored in KeyStore preferences and Workspace Timeline Audit Journals record restore events, alerting the user upon startup if an older timeline sequence is detected.

---

## 6. RECOVERY AND FAILURE MATRIX

| Scenario | System Impact | Mitigation / Recovery Procedure | Result |
| :--- | :--- | :--- | :--- |
| **Lost Master Passphrase** | Cannot derive `KEK_passphrase`. | Enter 24-word BIP39 seed to derive `KEK_recovery` and unwrap `DEK`. Set new Master Passphrase. | Workspace Recovered |
| **Replacement Phone (Clean OS)** | KeyStore `KEK_keystore` unavailable. | Enter 24-word BIP39 seed to derive `KEK_recovery` and unwrap `DEK`. Generate new local KeyStore key. | Workspace Recovered |
| **Lost BOTH Passphrase & Seed** | `DEK` unwrap impossible. | None. Cryptographically fails closed (no backdoor). | Data Unrecoverable |
| **Corrupted Backup Archive** | AEAD tag verification fails in staging. | Restore halts in `STATE_VERIFYING`. Staging directory purged; active database untouched. | Active Data Preserved |
| **Interrupted Restore / Crash** | Process killed in `STATE_ACTIVATING`. | `Application.onCreate()` detects `STATE_ACTIVATING` on startup; restores `files/rollback_backup/`. | Active Data Preserved |
| **Insufficient Storage** | Disk full during staging or snapshot. | Pre-check detects storage < 2.5x archive size; restore aborts in `STATE_VERIFYING`. | Active Data Preserved |

---

## 7. AUTOMATED TEST REQUIREMENTS MATRIX

*(Note: All listed tests below are PROPOSED specifications marked **NOT RUN** at baseline SHA `dbd4153ca39ede69890ebaf47feaab7f393c3c5a`)*

| Test ID | Test Case Scope | Verification Execution Command | Minimum Acceptance Criteria | Status |
| :--- | :--- | :--- | :--- | :--- |
| **T1** | **Passphrase Authentication** | `./gradlew test --tests "*KeyManagerTest.testPassphrase*"` | Correct passphrase unwraps DEK; wrong passphrase fails gracefully. | **NOT RUN** |
| **T2** | **Ciphertext Confidentiality** | `./gradlew test --tests "*StorageTest.testCiphertext*"` | Stored payload contains zero plaintext; bit-flip throws `AEADBadTagException`. | **NOT RUN** |
| **T3** | **Backup Round Trip** | `./gradlew test --tests "*BackupTest.testRoundTrip*"` | Exported `.eltkbackup` restored to fresh DB produces identical record hashes. | **NOT RUN** |
| **T4** | **Corrupted Backup Rejection** | `./gradlew test --tests "*BackupTest.testCorrupt*"` | Malformed archive rejected in staging; existing database 100% untouched. | **NOT RUN** |
| **T5** | **Interrupted Restore Crash Recovery** | `./gradlew connectedAndroidTest --tests "*RestoreCrashTest*"` | Simulated SIGKILL mid-restore auto-recovers pre-restore snapshot on restart. | **NOT RUN** |
| **T6** | **Replacement Phone Recovery** | `./gradlew connectedAndroidTest --tests "*RecoveryTest*"` | Fresh instance with empty KeyStore recovers workspace using 24-word seed. | **NOT RUN** |
| **T7** | **Preservation After Failure** | `./gradlew test --tests "*PreservationTest*"` | Disk-full or invalid header during restore leaves active dataset 100% unmodified. | **NOT RUN** |
| **T8** | **Rollback Sequence Detection** | `./gradlew test --tests "*RollbackDetectionTest*"` | Restoring an older valid backup increments timeline journal and alerts user. | **NOT RUN** |

---

## 8. SCOPE CONTROL AND CONSTRAINTS

- **Language & Runtime**: Native Java 17, Android minSdk 26+, targetSdk 35, AGP 8.7.3.
- **Financial Limit**: $0 ongoing development cost. Zero third-party cloud APIs, subscriptions, or paid services.
- **Fictional Test Data Policy**: 100% synthetic fictional test data only. Real legal records remain strictly prohibited until Increments 1–5 are implemented and all tests T1–T8 pass CI verification.
- **Deferred Nonessentials**: Defer AI integration, dynamic executable plugin loaders, cloud synchronization, and complex case management features. Focus exclusively on the minimum secure storage foundation.
