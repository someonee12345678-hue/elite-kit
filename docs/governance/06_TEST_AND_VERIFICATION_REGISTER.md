# ELITE KIT — TEST AND VERIFICATION REGISTER

Document Path: `docs/governance/06_TEST_AND_VERIFICATION_REGISTER.md`
Authoritative Baseline SHA: `dbd4153ca39ede69890ebaf47feaab7f393c3c5a`
Governing Authority: `MASTER_CONTROL.md`

---

## 1. AUTOMATED TEST MATRIX (T0 – T8)

| Test ID | Test Scope & Purpose | Execution Command | Acceptance Criteria | Status & Evidence |
| :--- | :--- | :--- | :--- | :--- |
| **T0** | **Test Harness Smoke Test** | `./gradlew test` | Verifies Gradle 8.9 test harness and Robolectric 4.11.1 functional. | **PASSED** (`HarnessSmokeTest` 48/48 tasks executed) |
| **T1** | **Passphrase & Seed KDF** | `./gradlew test --tests "*KeyManagerTest*"` | Correct passphrase/seed unwraps DEK; wrong credential throws `GeneralSecurityException`. | **PROPOSED (NOT RUN)** |
| **T2** | **Ciphertext Confidentiality** | `./gradlew test --tests "*CiphertextTest*"` | Stored payload contains 0% plaintext; bit-flip throws `AEADBadTagException`. | **PROPOSED (NOT RUN)** |
| **T3** | **Backup Round Trip** | `./gradlew test --tests "*BackupRoundTripTest*"` | Exported `.eltkbackup` restored to fresh DB produces 100% identical record SHA-256 digests. | **PROPOSED (NOT RUN)** |
| **T4** | **Corrupt Backup Rejection** | `./gradlew test --tests "*CorruptBackupTest*"` | Malformed archive rejected in staging; existing database 100% untouched. | **PROPOSED (NOT RUN)** |
| **T5** | **SQLCipher Side-File Check** | `./gradlew test --tests "*DatabaseSideFileTest*"` | Confirms `.db-wal` and `.db-journal` on disk contain zero plaintext substrings. | **PROPOSED (NOT RUN)** |
| **T6** | **Interrupted Restore Crash** | `./gradlew connectedAndroidTest --tests "*RestoreCrashTest*"` | SIGKILL mid-restore auto-recovers pre-restore snapshot on application restart. | **PROPOSED (NOT RUN)** |
| **T7** | **Replacement Phone Recovery** | `./gradlew connectedAndroidTest --tests "*RecoveryTest*"` | Fresh instance with empty KeyStore recovers workspace using 24-word seed. | **PROPOSED (NOT RUN)** |
| **T8** | **Preservation After Failure** | `./gradlew test --tests "*PreservationTest*"` | Disk-full or invalid header during restore leaves active dataset 100% unmodified. | **PROPOSED (NOT RUN)** |
