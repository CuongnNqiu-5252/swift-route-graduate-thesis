# SwiftRoute Agent Operating Policy

## Scope and task ledger

- Treat a request as **impacting** when it can change source, tests, configuration, dependencies, schemas, data, CI/CD, external systems, or architecture.
- Before an impacting action, read `tasks/task-map.md` only when needed to create, continue, update, or verify the approval of that task. Then read only its mapped task file.
- Do not scan `/tasks` by default. Read-only research and ordinary questions do not need a task file unless they become an approved implementation proposal.
- A task file contains at most five small tasks. Follow the schema in `.agents/skills/swiftroute-governance/references/task-ledger.md`.

## Approval and safety

- Ask for explicit scope approval before a formal review, major change, upgrade, migration, broad refactor/restructure, bulk delete/move, CI/deployment action, or installation/execution of a third-party repo or skill.
- A request to implement a normal scoped change is not approval for a major action hidden within it.
- Before an approved major action, state impact, affected paths/systems, risks, rollback, and backup approach. Create and verify the approved backup before writing.
- Store material backups outside the application repository at a user-approved location. For schema/data work, use a restorable export; Git diff alone is insufficient.
- Preserve unrelated dirty-worktree changes. Never use destructive recovery commands such as `git reset --hard` or `git checkout --`.

## Completion

- After an approved change, report changed paths, verification actually run, remaining limitations, and update the mapped task.
- Governance files and this policy never authorize application-code changes by themselves.
