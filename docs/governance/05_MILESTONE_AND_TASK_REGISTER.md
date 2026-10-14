# ELITE KIT — MILESTONE AND TASK REGISTER

Document Path: `docs/governance/05_MILESTONE_AND_TASK_REGISTER.md`
Authoritative Baseline SHA: `dbd4153ca39ede69890ebaf47feaab7f393c3c5a`
Governing Authority: `MASTER_CONTROL.md`

---

## 1. MILESTONE PROGRESSION

```
[ M0: Baseline Inspection ] ──> [ M1: Governance & Test Harness ] ──> [ M2: Key Management Core ]
           (PASSED)                            (IN PROGRESS)                         (PENDING)
                                                                                 │
                                                                                 v
[ M5: Real-Record Release Gate ] <── [ M4: Streaming Backup & Restore ] <── [ M3: Encrypted Storage ]
           (BLOCKED)                             (PENDING)                           (PENDING)
```

| Milestone ID | Milestone Description | Target Output | Status |
| :--- | :--- | :--- | :--- |
| **M0** | **Baseline Inspection & Audit** | Verify baseline `dbd4153ca39ede69890ebaf47feaab7f393c3c5a` and shell activity. | **COMPLETED** |
| **M1** | **Governance & Test Harness** | Repository governance records (`docs/governance/`) & Gradle 8.9 test harness. | **IN PROGRESS** |
| **M2** | **Key Management System Core** | Implement `KeyManager.java` (PBKDF2 key derivation, KeyStore wrapping, BIP39 seed recovery). | **PENDING AUTHORIZATION** |
| **M3** | **Encrypted Database Engine** | Implement SQLCipher page-level encrypted SQLite storage engine and evidence stream. | **PENDING** |
| **M4** | **Streaming Backup & Restore** | Implement Tink streaming AEAD container exporter/importer & 7-state restore state machine. | **PENDING** |
| **M5** | **Real-Record Release Gate** | Automated CI verification of tests T1–T8 passing with 0 failures using synthetic data. | **BLOCKED** |

---

## 2. SINGLE NEXT AUTHORIZED TASK

- **Task ID**: `TASK-M2-INC2`
- **Task Description**: Implement Phase 3, Increment 2 (Key Management System Core):
  - Implement `com.elitekit.app.crypto.KeyManager` for PBKDF2WithHmacSHA256 passphrase key derivation.
  - Implement 24-word BIP39 mnemonic seed derivation (`PBKDF2WithHmacSHA512`) and `DEK` unwrapping.
  - Execute automated tests T1 and T7 (`KeyManagerTest`).
- **Prerequisite**: Explicit user authorization before making changes to application source code.
