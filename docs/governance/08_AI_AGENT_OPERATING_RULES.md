# ELITE KIT — AI AGENT OPERATING RULES

Document Path: `docs/governance/08_AI_AGENT_OPERATING_RULES.md`
Authoritative Baseline SHA: `dbd4153ca39ede69890ebaf47feaab7f393c3c5a`
Governing Authority: `MASTER_CONTROL.md`

---

## 1. MANDATORY RULES FOR ALL AI CODING AGENTS

1. **Inspect Before Action**: Always inspect `MASTER_CONTROL.md`, current git branch, working tree status, and relevant files in `docs/governance/` before planning or proposing edits.
2. **Never Edit `main` Directly**: All development work must be performed on isolated feature branches derived from baseline SHA `dbd4153ca39ede69890ebaf47feaab7f393c3c5a`.
3. **Preserve Protected Artifacts**:
   - `main` branch HEAD must remain untouched.
   - Both GitHub Actions workflows (`.github/workflows/main.yml` and `.github/workflows/android-smoke-test.yml`) must remain 100% pristine.
   - `MASTER_CONTROL.md` must not be modified during feature implementation tasks.
4. **Enforce Fictional Data Policy**: Strictly 100% synthetic fictional test data across all development, testing, and documentation tasks. Real confidential legal records are strictly prohibited.
5. **No Unsupported Claims**: Never claim a build, test, security property, or repository write operation succeeded without verified evidence (execution output logs or git SHAs).
6. **$0 Budget Limit**: Never introduce dependencies on paid APIs, subscriptions, cloud services, or paid AI tokens.
7. **Small Reversible Increments**: Make small, testable, reversible changes. Do not refactor unrelated code or add unrequested features.
8. **Respect Authorization Boundaries**: Stop immediately at defined task boundaries and await explicit user authorization before advancing to the next implementation increment.
