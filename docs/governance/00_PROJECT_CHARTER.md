# ELITE KIT — PROJECT CHARTER

Document Path: `docs/governance/00_PROJECT_CHARTER.md`
Authoritative Baseline SHA: `dbd4153ca39ede69890ebaf47feaab7f393c3c5a`
Governing Authority: `MASTER_CONTROL.md`

---

## 1. PURPOSE AND LONG-TERM OBJECTIVE
Build Elite Kit as a self-contained, Android-native, local-first legal workspace. The primary objective is to create a secure, recoverable, and maintainable workspace that allows legal professionals to organize matters, notes, research, and evidence files directly on their Android devices without depending on third-party cloud services or online account infrastructures.

---

## 2. PROJECT BOUNDARIES AND NON-NEGOTIABLE CONSTRAINTS
1. **$0 Financial Boundary**: Development and operational dependencies must remain $0. Zero reliance on paid cloud APIs, subscriptions, or AI credits.
2. **Local-First Storage & Confidentiality**: 100% of user data remains stored locally on-device in encrypted storage.
3. **Synthetic Fictional Test Data Policy**: All development and testing must strictly use synthetic fictional legal data. Real confidential legal records remain strictly prohibited until all encryption, key management, backup, restore, and replacement-phone recovery gates are fully implemented and verified.
4. **Preservation of Main & Workflows**: The `main` branch baseline (`dbd4153ca39ede69890ebaf47feaab7f393c3c5a`) and existing GitHub Actions workflows (`.github/workflows/main.yml` and `.github/workflows/android-smoke-test.yml`) must remain untouched.
5. **No Unsupported Claims**: Never claim security, encryption, or recovery capabilities are implemented or verified without automated test evidence.

---

## 3. MINIMUM-VIABLE-PROGRAM-FIRST STRATEGY
Development follows a strict minimum-viable-program (MVP) progression. Features are added in testable, reversible increments starting from the security and build harness foundations. Speculative features, device-local AI engines, cloud synchronization adapters, and dynamic plugin loaders are explicitly deferred until core security and storage gates pass.

---

## 4. DEFINITION OF PROJECT COMPLETION
Project completion requires a fully local-first Android workspace meeting all functional specifications, where:
- All legal records and attachments are encrypted at rest via page-level database and file stream encryption.
- Zero-KeyStore replacement-phone recovery using 24-word BIP39 seeds is verified.
- Transactional, crash-safe backup and restore round-trips pass automated CI testing.
- The real-record release gate passes all automated security tests with 0 failures.
