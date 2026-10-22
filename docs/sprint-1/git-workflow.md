# Sprint 1 — GitHub operating rules

## Branch strategy

- `main`: tagged, assessed/released snapshots (`sprint-1`, `sprint-2`, `sprint-3`, `v1.0.0`). No direct push.
- `develop`: team integration branch. No direct push.
- `feature/<issue-number>-<short-name>`: start from `develop` for each story/task.
- `fix/<issue-number>-<short-name>`: bugfix from `develop`.
- `release/<version>`: optional stabilization. `hotfix/<issue>-...`: optional urgent released version repair.

## Issue → PR → review → merge

1. Create a GitHub Issue with owner, acceptance criteria, estimate, priority, dependencies.
2. Assign contributor; branch `feature/12-patient-register`.
3. Keep commits small and link with `Refs #12` / `Closes #12` on the PR.
4. Open PR into `develop`; update tests, docs and OpenAPI if the contract changed.
5. Request at least **one peer approval**. Review code correctness, contract, privacy/safety and tests.
6. Required checks pass; merge into `develop` using a consistent squash policy.
7. For sprint submission, review a PR from `develop` to `main`; merge after green CI; create annotated tag `sprint-1` etc.

## GitHub setup checklist (web UI)

- Create public or private repository per rights review (private until publication authorization).
- Create `develop` branch from `main` after first push.
- Settings → Rules → Rulesets (or Branch protection): for **both** `main` and `develop`, require PR, one approval, passing `backend-tests`, `frontend-build`, `api-validation`, prohibit force push and deletion. Rule support varies with GitHub plan and repository visibility.
- Settings → Actions: allow CI; configure security and workflow permissions to read-only unless needed.
- GitHub Projects → board columns `Backlog / Ready / In Progress / Review / Done`.
- Enable Issues and Discussions if relevant; assign every team member their own account.
- Never commit secret tokens, private keys or identifiable patient data.

The scaffold **cannot enable GitHub rulesets** on its own; maintainers must activate them in repo settings.
