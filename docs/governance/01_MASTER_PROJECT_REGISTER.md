# ELITE KIT — MASTER PROJECT REGISTER

Document Path: `docs/governance/01_MASTER_PROJECT_REGISTER.md`
Authoritative Baseline SHA: `dbd4153ca39ede69890ebaf47feaab7f393c3c5a`
Governing Authority: `MASTER_CONTROL.md`

---

## 1. GOVERNANCE RECORD INDEX

| Record Path | Record Name | Description & Status |
| :--- | :--- | :--- |
| `docs/governance/00_PROJECT_CHARTER.md` | Project Charter | Mission, non-negotiable boundaries, completion criteria. |
| `docs/governance/01_MASTER_PROJECT_REGISTER.md` | Master Project Register | Repository index, current baseline pointer, and active task state. |
| `docs/governance/02_REQUIREMENTS_REGISTER.md` | Requirements Register | Traceable requirements captured from `MASTER_CONTROL.md`. |
| `docs/governance/03_ARCHITECTURE_AND_BUILD_PLAN.md` | Architecture & Build Plan | Minimum viable architecture, SQLCipher storage, Tink AEAD, increment plan. |
| `docs/governance/04_DECISION_REGISTER.md` | Decision Register | Architectural decisions, rationale, status, and owner approval gates. |
| `docs/governance/05_MILESTONE_AND_TASK_REGISTER.md` | Milestone & Task Register | Milestones M0–M6, task prioritization, and next authorized task. |
| `docs/governance/06_TEST_AND_VERIFICATION_REGISTER.md` | Test & Verification Register | Test matrix T1–T8, execution logs, and evidence tracking. |
| `docs/governance/07_CHANGE_AND_RELEASE_LOG.md` | Change & Release Log | Chronological commit and release change log. |
| `docs/governance/08_AI_AGENT_OPERATING_RULES.md` | AI Agent Operating Rules | Mandatory operating directives for all AI coding agents. |
| `docs/governance/09_CONTINUITY_AND_RECOVERY.md` | Continuity & Recovery | Protocol for resuming work across fresh agent conversations. |

---

## 2. CURRENT PROJECT STATE

- **Verified Baseline Commit**: `dbd4153ca39ede69890ebaf47feaab7f393c3c5a` (`Restore app module configuration` on `main`).
- **Current Active Feature Branch**: `feature/project-governance` (or `feature/secure-storage-foundation` PR #1).
- **Current Capability Status**:
  - *Implemented*: Native Activity Shell (`MainActivity.java`), AGP 8.7.3 Gradle Build, GitHub Actions workflows, Test Harness setup (JUnit 4, Robolectric, AndroidX Test Core).
  - *Unimplemented / Missing*: SQLCipher page-level encryption, KeyManager (PBKDF2 / BIP39 seed recovery), Tink streaming AEAD backup exporter/importer, Transactional Restore State Machine.
- **Current Milestone**: Milestone M1 — Governance & Test Harness Foundation.
- **Single Next Authorized Task**: Finalize repository governance initialization and await explicit authorization for Phase 3, Increment 2 (Key Management System implementation).
