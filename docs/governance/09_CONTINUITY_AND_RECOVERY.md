# ELITE KIT — CONTINUITY AND RECOVERY

Document Path: `docs/governance/09_CONTINUITY_AND_RECOVERY.md`
Authoritative Baseline SHA: `dbd4153ca39ede69890ebaf47feaab7f393c3c5a`
Governing Authority: `MASTER_CONTROL.md`

---

## 1. PROTOCOL FOR RESUMING WORK IN A NEW CONVERSATION

When a new AI agent or fresh conversation initializes, execute this exact recovery sequence:

### Step 1: Establish Verified Repository State
Run the following commands in bash:
```bash
git checkout main
git status
git log -1 dbd4153ca39ede69890ebaf47feaab7f393c3c5a
git branch -a
```
Confirm `main` matches baseline `dbd4153ca39ede69890ebaf47feaab7f393c3c5a`.

### Step 2: Read Governing & Governance Records
Read these files in order:
1. `MASTER_CONTROL.md`
2. `docs/governance/01_MASTER_PROJECT_REGISTER.md`
3. `docs/governance/05_MILESTONE_AND_TASK_REGISTER.md`

### Step 3: Identify Current Milestone & Next Authorized Task
Check `05_MILESTONE_AND_TASK_REGISTER.md` for the "Single Next Authorized Task". Do not start implementation without explicit user authorization for that task ID.

### Step 4: Standardized Handoff Report Format
Every task completion or conversation handoff report must include:
1. **Current Branch & Base SHA**
2. **Commit SHA & Changed File List**
3. **Verification Evidence** (test logs or file diffs)
4. **Current Milestone & Single Next Recommended Task**
5. **Confirmation of Baseline & Workflows Preservation**
