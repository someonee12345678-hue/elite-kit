# ELITE KIT — MASTER CONTROL

Version: 0.1
Status: Governing development document; not software or a security certification.
Last reviewed: 2026-10-12

## 1. Mission

Build and maintain a local-first Android legal workspace that can be securely updated, tested, published, recovered, and handed to another authorized maintainer. The development and release workflow must work from an Android phone.

## 2. Separate Systems

- Installed application: the user's legal workspace and local data.
- Master Program: canonical source, build and test automation, debugging, release controls, recovery-kit generation, and maintainer handover.
- Installer and publishing system: installation packages, instructions, update delivery, release channels, repair, and rollback.
- Capability catalogue: approved compatible features that can update without a full app reinstall when technically safe.
- Recovery kit: independent copies of source, build instructions, last-known-good release, verification information, and recovery procedures.

These responsibilities must remain distinct even if later presented through one control interface.

## 3. Non-Negotiable Safety Rules

1. Do not put real confidential legal records into prototypes or test builds.
2. Never claim software is secure or production-ready without evidence supporting that exact claim.
3. Never execute arbitrary remote code as a shortcut for updating features.
4. Verify authenticity, integrity, compatibility, and permissions before activating an update.
5. Preserve a last-known-good release and provide a tested rollback path.
6. Never repair corruption by silently deleting user records.
7. Require human approval for publishing, destructive actions, consequential migrations, or key changes.
8. Keep passwords, recovery codes, encryption keys, signing secrets, and confidential legal data out of source control, screenshots, logs, and build artifacts.
9. Keep application-data backups separate from source-code backups.
10. A backup is not verified until restoration succeeds in a clean test environment.
11. Preserve earlier prototype files and releases unchanged unless explicitly authorized otherwise.
12. Do not modify separate legal matters or advance pending TEST-003 without explicit authorization.

## 4. Current Checkpoint — 2026-10-12

- Private GitHub repository: `elite-kit`, branch `main`.
- Existing workflows: `.github/workflows/main.yml` and `.github/workflows/android-smoke-test.yml`.
- The diagnostic workflow passed; this proves only that its runner and workflow worked.
- The Android smoke workflow built a small fictional test APK. It is not the Elite Kit application or a production release.
- An antivirus warning was reported for that APK. Do not treat it as trusted or production-ready.
- Candidate 0.5.6 reported 21/21 self-checks passing on the user's phone.
- One isolated fictional case-and-notes backup/restore round trip passed.
- Earlier prototype versions must remain preserved.
- Current HTML prototypes use unencrypted browser storage and unencrypted JSON exports.
- Production encryption, recovery-key handling, trusted dynamic updates, production signing, publishing controls, and independent security review are not implemented.
- The smoke-test app source was generated inside its workflow and is not yet the canonical saved application source.

## 5. Release Gates

Before a release advances, assess:

- Authorization and scope.
- Canonical source and recorded revision.
- Build integrity and artifact provenance.
- Relevant functional and regression tests.
- Data integrity, migration, backup, and restore.
- Security and privacy.
- Update authenticity, compatibility, and rollback.
- Matching installation and recovery instructions.
- Explicit publishing approval.
- Disaster recovery and successor readiness.

A successful build is not proof of security. Self-checks are not independent verification. Record the exact test type and result.

## 6. Permanent Core and Dynamic Capabilities

Keep the security-critical core small and slow-changing. It must open local records without depending on an online update service.

Prefer compatible non-executable updates first: help content, lessons, templates, form metadata, declarative workflows, and diagnostic definitions. Native code, permissions, encryption, database-engine, or security-boundary changes may require a signed application update.

Stage updates, verify them, test compatibility, then activate. If validation fails, reject the update and retain the last-known-good version. Offline use must continue if the catalogue is unavailable.

## 7. Disaster Recovery

Maintain at least three copies of the source and recovery kit across at least two independent storage types, including one offline copy. Do not depend on one account or provider.

The recovery kit should include canonical source and history, build instructions, tool and dependency versions, architecture and schema records, tests, last-known-good release, checksums and signatures, installation and rollback instructions, and a dated record of restoration tests.

Keep recovery secrets separate from ordinary archives. Document account recovery, source recovery, signing-key recovery, and user-data recovery separately. Do not promise encrypted-data recovery if every usable copy of the required key is lost.

## 8. Phone-First Operating Method

- Make small, reversible changes.
- Inspect before editing.
- Preserve existing working workflows.
- Record every material change and test result.
- Do not repeat a passed test unless a change, failure, or evidence gap justifies it.
- Use fictional data for development and testing.
- Keep instructions understandable without private chat history.
- Do not publish automatically after a build or commit.

## 9. Next Authorized Task

Inspect the repository and existing workflows, then establish a canonical, reviewable Android application source tree committed to the repository. Do not create another disposable prototype. Preserve both existing workflows. Do not install the previously flagged smoke-test APK. Use fictional data only.

## 10. Change Record

Every significant change must record the date, version, rationale, files affected, risks, tests performed and test type, results, unresolved issues, and rollback method.

This document is a working control record. It does not mean the security controls or production application described above have already been implemented.
