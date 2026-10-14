# ELITE KIT — CONTINUITY CHECKPOINT DOCUMENT

Task ID: CONTINUITY-CHECKPOINT-001
Document Path: docs/ELITE_KIT_CONTINUITY_CHECKPOINT.md
Status: Operational Continuity Record — Documentation Only
Authoritative Baseline: main (dbd4153ca39ede69890ebaf47feaab7f393c3c5a)
Governing Authority: MASTER_CONTROL.md

---

## 1. Project Identity & Operational Boundaries

- **Project Name**: Elite Kit
- **Repository**: `someonee12345678-hue/elite-kit`
- **Target Platform**: Local-first, Android-native, phone-first legal workspace (`com.elitekit.app`).
- **Technical Target**: Java 17, minSdk 26, targetSdk 35, compileSdk 35, AGP 8.7.3.
- **Financial Boundary**: $0 tool cost; zero dependencies on paid APIs, paid AI models, cloud subscriptions, or online account infrastructures.
- **Operating Context**: Phone-first maintainer workflow using Android web and terminal interfaces.
- **Authoritative Baseline Commit**: `dbd4153ca39ede69890ebaf47feaab7f393c3c5a` on `main`.

---

## 2. Current Verified State & Unimplemented Capabilities

### 2.1 Verified State Facts
1. **Repository Root**: Contains `MASTER_CONTROL.md`, `README.md`, `build.gradle.kts`, `settings.gradle.kts`, `.github/workflows/main.yml`, and `.github/workflows/android-smoke-test.yml`.
2. **App Module Shell**: Contains minimal Android native shell (`app/src/main/java/com/elitekit/app/MainActivity.java`).
3. **Design Proposal**: Proposed secure storage design specification exists in `docs/SECURE_STORAGE_DESIGN.md` on branch `feature/secure-storage-foundation`.
4. **Workflows**: Both GitHub Actions workflows (`main.yml` and `android-smoke-test.yml`) are intact and pristine.

### 2.2 Unimplemented Security Capabilities [FACT]
The following security capabilities are **NOT IMPLEMENTED** in application source code at baseline `dbd4153ca39ede69890ebaf47feaab7f393c3c5a`:
- Encrypted SQLite database tables or payload encryption engines.
- Password-based key derivation functions (`PBKDF2WithHmacSHA256`) or key wrapping modules.
- Android KeyStore TEE integrations (`KEK_keystore`).
- BIP39 recovery seed generators or key derivation modules (`KEK_recovery`).
- Encrypted backup exporter (`.eltkbackup`) or AEAD stream encryption generators.
- Transactional 9-state crash-resilient restore engine (`restore_state.json`).
- Automated security unit, integration, or instrumentation test suites.

---

## 3. Mandatory Safety Rules & Prohibitions

1. **Play Protect / Smoke-Test APK Warning**:
   - The previously built smoke-test APK triggered a Play Protect / antivirus warning.
   - **DO NOT INSTALL, TRUST, OR EXECUTE THAT APK.** It is a disposable CI build test artifact and NOT the Elite Kit application.
2. **Main Branch & Workflow Protection**:
   - `main` branch must remain pristine.
   - `.github/workflows/main.yml` and `.github/workflows/android-smoke-test.yml` must NOT be modified, replaced, or deleted without authorization.
3. **Fictional Data Policy**:
   - 100% synthetic fictional test data during all design, development, and testing phases.
   - **REAL CONFIDENTIAL LEGAL RECORDS ARE STRICTLY PROHIBITED** until all security protections, key management systems, backup, restore, and replacement-phone recovery mechanisms have been implemented and verified passing in CI.
4. **Human Approval Gate**:
   - Explicit modal human approval is required prior to:
     - Merging pull requests into `main`.
     - Executing destructive database/file actions or evidence purging.
     - Activating a backup restoration (overwriting active workspace files).
     - Rotating passphrases or regenerating recovery seed material.
     - Publishing releases.
5. **No False Security Claims**:
   - Never claim security properties (e.g. "encrypted", "rollback-proof", "secure") without implemented source code and verifiable automated CI test evidence.

---

## 4. Approved Task Boundaries: Documentation vs. Implementation

- **CURRENT APPROVED TASK**: Documentation and Continuity Checkpoint Integration (`docs/ELITE_KIT_CONTINUITY_CHECKPOINT.md` and `docs/SECURE_STORAGE_DESIGN.md`).
- **AUTHORIZED WORK**: Inspecting repository state, documenting security architecture, creating continuity handoffs, defining test criteria, and establishing open decision frameworks on isolated feature branch `feature/secure-storage-foundation`.
- **UNAUTHORIZED WORK**: Modifying `main`, editing Android Java application source code (`MainActivity.java`), implementing database/encryption engines, adding third-party dependencies, executing merges, or releasing builds.

---

## 5. Unresolved Architectural Decisions

The following architectural choices are explicitly recorded as **UNRESOLVED OPEN DECISIONS** requiring formal review and approval prior to implementation:

1. **Storage Engine Choice**: Standard Android SDK SQLite (`android.database.sqlite`) with application-level AES-256-GCM payload encryption ($0 added dependencies, 0MB APK footprint, exposed table names/row counts) vs. SQLCipher for Android (`net.zetetic:android-database-sqlcipher`, full-database page encryption, ~12MB native JNI binary footprint).
2. **Recovery Seed Derivation Standardization**: Standard BIP39 derivation (`PBKDF2WithHmacSHA512`, 2048 iterations) vs. custom PBKDF2/HKDF key wrapping scheme.
3. **KDF Iteration Benchmarking**: Calibrating PBKDF2 iterations via a setup benchmark loop on target mobile hardware to balance unlock latency (300ms–500ms target) and brute-force resistance.
4. **Recovery Mnemonic Entropy**: 12-word BIP39 seed (128-bit entropy) vs. 24-word BIP39 seed (256-bit entropy).
5. **Rollback Attack Countermeasures**: User-confirmed launch state timestamps vs. out-of-band sequence numbers in offline recovery material.

---

## 6. Required Order of Operations for Future Increments

All future implementation work must strictly follow this dependency-ordered sequence of small, testable increments:

1. **Increment 1**: Establish Gradle 8.9 Wrapper and local build test harness (`./gradlew test`).
2. **Increment 2**: Implement Key Hierarchy and PBKDF2 Key Wrapping Engine (`crypto/`).
3. **Increment 3**: Implement Encrypted Database and Media Storage Repository (`storage/`).
4. **Increment 4**: Implement AEAD Encrypted Backup Exporter (`backup/`).
5. **Increment 5**: Implement Transactional Crash-Resilient Restore Engine (`restore/`).
6. **Increment 6**: Implement BIP39 Replacement-Phone Recovery Workflow (`recovery/`).
7. **Increment 7**: Implement Automated Security CI Test Suite & Acceptance Gates (`ci/`).

---

## 7. Continuity & Recovery Rule

In the event of agent trace reset, conversation loss, maintainer transition, or environment switching:
1. Check out feature branch `feature/secure-storage-foundation`.
2. Verify parent commit is `dbd4153ca39ede69890ebaf47feaab7f393c3c5a`.
3. Read `MASTER_CONTROL.md`, `docs/ELITE_KIT_CONTINUITY_CHECKPOINT.md`, and `docs/SECURE_STORAGE_DESIGN.md`.
4. Confirm `main` branch and workflows remain pristine before performing any work.
