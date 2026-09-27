# Recovery progress

Copy this file to `RECOVERY_PROGRESS.md` during a reconstruction. Update it after every phase and commit it. Never include credentials or unmasked secrets.

## Repository checkpoint

- Date/time:
- Branch:
- Commit SHA:
- Coding agent/tool:
- Current phase:
- Working tree status:

## Completed acceptance criteria

- [ ] Requirement and architecture documents read
- [ ] Phase scope implemented
- [ ] Unit/contract tests pass
- [ ] UI build passes
- [ ] Docker images build
- [ ] Flyway succeeds on an empty database
- [ ] Flyway succeeds when upgrading the retained test database
- [ ] `./start.sh` reaches readiness
- [ ] Manual acceptance path passes
- [ ] `git diff --check` passes
- [ ] Documentation reflects actual behavior and limitations

## Migrations added

| Migration | Purpose | Applied to empty DB | Applied as upgrade |
|---|---|---:|---:|
| None | | | |

Do not edit the checksum or content of an already applied migration.

## Commands and results

```text
# Paste exact commands and concise pass/fail results. Remove tokens and secrets.
```

## Known limitations and blockers

- None recorded.

## Data/backup notes

- PostgreSQL backup location/reference:
- Container image tags/digests:
- Registry repository revision:
- Encryption key location reference (never the key):

## Next prompt

```text
Paste the single next phase prompt from 17-ai-coding-prompts.md and add only necessary local context.
```
