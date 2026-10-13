# ELITE KIT — PHONE FIELD GUIDE

Version: 0.1
Status: Initial navigation record
Last verified: 2026-10-12
Device context: Android phone using Chrome
Website: GitHub mobile web interface

## 1. Purpose

Record verified navigation paths and procedures for maintaining Elite Kit from an Android phone. Reduce repeated screenshots and unnecessary navigation.

This guide records observed behaviour, not permanent guarantees. GitHub may change its interface.

## 2. Repository

Repository: elite-kit
Visibility: Private
Working branch: main

## 3. Confirmed Repository Root

The repository root displays:
- .github/workflows/
- MASTER_CONTROL.md
- README.md

The root page also displays the branch selector, green Code button, and a three-dot menu beside Code.

## 4. Confirmed Workflow Folder

Path:
.github/workflows/

Files:
- main.yml
- android-smoke-test.yml

Purpose:
- main.yml: diagnostic workflow.
- android-smoke-test.yml: builds a small fictional Android test APK.

Do not overwrite or delete either workflow without a specific reason and authorization.

The smoke-test APK is not the Elite Kit application. An antivirus warning was reported for it. Do not treat it as a trusted production artifact.

## 5. Confirmed Navigation

### Repository root
1. Open the private elite-kit repository.
2. Select the Code tab.
3. The root file list appears below the branch selector.

### Workflow folder
1. From the repository root, tap .github/workflows/.
2. The workflow list should show main.yml and android-smoke-test.yml.
3. Use the elite-kit breadcrumb to return to the root.

### Actions
1. Tap Actions in the repository's top navigation.
2. Select a workflow or run to inspect its result.
3. A successful run proves only what that workflow actually tested.

### Commit history
The repository's commit history can be reached through the repository interface. The mobile layout may vary. Do not assume a control is in the same position without checking.

## 6. Creating Files on Mobile

Observed behaviour:
- The repository root has a three-dot menu beside the green Code button.
- GitHub's mobile Create new file editor may not interpret a slash in a filename as a directory path.
- If a proposed path is stripped or rejected, stop rather than assuming a folder was created.
- A root-level Markdown file is an acceptable temporary location.
- Do not create duplicate files merely to test navigation.

Preferred initial field-guide filename:
PHONE_FIELD_GUIDE.md

## 7. Working Rules

1. Inspect before editing.
2. Make one change at a time.
3. Preserve existing working workflows.
4. Record the filename and commit message before committing.
5. Verify that a committed file appears in the repository.
6. Never paste passwords, tokens, recovery codes, signing secrets, or private legal records into this guide.
7. Use fictional data in development and test builds.
8. If the screen differs from this guide, treat the current screen as authoritative and update this guide after confirming the new path.

## 8. Screenshot Reference Policy

Screenshots are useful for recording unfamiliar or error-prone screens, but the guide should not depend on collecting a screenshot for every routine action.

For each important procedure, record:
- page or screen name;
- entry point;
- steps;
- expected result;
- last verification date;
- known differences or uncertainties.

Do not retain screenshots that expose account recovery information, credentials, confidential legal information, or other secrets.

## 9. Known Limitations

- This guide covers only screens actually observed so far.
- Not every GitHub mobile menu has been mapped.
- The interface may change.
- A navigation route is not considered verified merely because it seems plausible.
- No production Android application has yet been established in the repository.

## 10. Next Task

Continue mapping the GitHub mobile interface only where needed to establish the canonical Android source tree and maintain the Master Program.

Preserve both existing workflows. Do not install the previously flagged smoke-test APK. Do not modify separate legal matters or advance pending TEST-003.

END OF FIELD GUIDE
