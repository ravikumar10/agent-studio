# Moving Agent Studio to a new laptop

This runbook preserves both the source code and the local Agent Studio state. GitHub preserves source and history; it does **not** preserve Docker volumes, PostgreSQL data, local API credentials, or the encryption key.

## What must be transferred

| Asset | Why it is required | Storage recommendation |
|---|---|---|
| Git repository/branch | Application source, migrations, documentation | GitHub; merge or retain the feature branch |
| PostgreSQL custom-format dump | Agents, versions, profiles, encrypted secrets, bindings, runs, events, sessions and configuration | Encrypted external storage |
| `AGENT_STUDIO_ENCRYPTION_KEY` | Decrypts credentials already stored in PostgreSQL | Password manager or secret manager |
| External credentials not stored in Agent Studio | GitHub CLI login/token, cloud identity, registry login and optional environment-only model keys | Password/secret manager |
| Optional sample database dump | Preserves changes made to the demonstration `sample_catalog` database | Encrypted external storage |

Redis is a disposable hot projection/cache. Durable conversational turns and evidence are in PostgreSQL, so Redis normally does not need to be transferred.

Never commit database dumps, plaintext keys, tokens, `.env` files, or credential exports to Git.

## Recommended one-command transfer

The repository includes `laptop-transfer.sh`, which performs the manual steps in this runbook and validates the result.

On the old laptop:

```bash
./laptop-transfer.sh backup /path/to/encrypted-external-storage
```

This produces one owner-only `agent-studio-transfer-YYYYMMDD-HHMMSS.tar.gz` archive containing the main PostgreSQL dump, manifest, exact running encryption key, source metadata, checksums, and the sample database when it is running. The archive is sensitive even though database credentials are encrypted.

On the new laptop, after cloning the repository:

```bash
./laptop-transfer.sh restore /path/to/agent-studio-transfer-YYYYMMDD-HHMMSS.tar.gz
```

The restore command validates every checksum, restores a new empty `agent-studio` PostgreSQL volume, supplies the recovered key to the services, builds the images, and starts the application. It refuses to overwrite an initialized target. Use `--force` only when the target database is known to be disposable:

```bash
./laptop-transfer.sh restore /path/to/agent-studio-transfer-YYYYMMDD-HHMMSS.tar.gz --force
```

The detailed manual procedures below remain the troubleshooting and audit reference.

## 1. Confirm the source is remote

From the repository root:

```bash
git status
git branch --show-current
git log -1 --oneline
git remote -v
```

Confirm the required commit exists on GitHub. At the time this runbook was written, the latest checkpoint was commit `afed909` on branch `feat/newrelic-document-ocr-mcp`, with pull request `ravikumar10/agent-studio#6`. A later commit or merged `main` supersedes that checkpoint.

## 2. Identify the encryption-key source

The application uses one key in control-plane and runtime-service. The Compose development fallback is:

```text
local-development-encryption-key-change-me
```

That value is acceptable only for local development. It is nevertheless the **correct recovery key** if the old stack used the default. Do not replace it during migration; changing the key makes existing encrypted database values unreadable.

Check whether the running container uses the development default without printing the key:

```bash
docker compose --project-name agent-studio \
  -f src/deploy/compose/compose.yml exec -T control-plane \
  sh -c 'if [ "$AGENT_STUDIO_ENCRYPTION_KEY" = "local-development-encryption-key-change-me" ]; then echo DEFAULT_DEVELOPMENT_KEY; else echo CUSTOM_KEY; fi'
```

- If it prints `DEFAULT_DEVELOPMENT_KEY`, save the exact fallback value above in your password manager.
- If it prints `CUSTOM_KEY`, recover the original value from the password manager, shell profile, deployment environment, or secret manager that supplied it.
- `printenv AGENT_STUDIO_ENCRYPTION_KEY` only checks the current terminal. An empty result does not prove the container used the default.

If a custom key exists only inside the local container, make a private temporary export without printing it:

```bash
mkdir -p backups
umask 077
docker inspect agent-studio-control-plane-1 \
  --format '{{range .Config.Env}}{{println .}}{{end}}' \
  | sed -n 's/^AGENT_STUDIO_ENCRYPTION_KEY=//p' \
  > backups/agent-studio-encryption-key.txt
test -s backups/agent-studio-encryption-key.txt
```

Immediately move that file into encrypted storage or a password manager, then securely remove the plaintext temporary copy. Container names can be confirmed with `docker compose --project-name agent-studio -f src/deploy/compose/compose.yml ps`.

## 3. Back up PostgreSQL

Keep the stack running so PostgreSQL is available. `pg_dump` creates a transactionally consistent logical backup:

```bash
mkdir -p backups
umask 077

docker compose --project-name agent-studio \
  -f src/deploy/compose/compose.yml exec -T postgres \
  pg_dump -U agent_studio -d agent_studio -Fc \
  > backups/agent-studio.dump

test -s backups/agent-studio.dump

docker compose --project-name agent-studio \
  -f src/deploy/compose/compose.yml exec -T postgres \
  pg_restore --list \
  < backups/agent-studio.dump \
  > backups/agent-studio.dump.manifest
```

The dump contains sensitive application data and encrypted credential material. Copy the dump and manifest to encrypted external/cloud storage; do not add them to Git.

If the demonstration database was modified and matters, back it up separately:

```bash
docker compose --project-name agent-studio \
  -f src/deploy/compose/compose.yml exec -T sample-data \
  pg_dump -U sample_reader -d sample_catalog -Fc \
  > backups/sample-catalog.dump
```

## 4. Record external dependencies

Store a checklist—not secret values—in the migration package:

- GitHub account and private-repository access for `ravikumar10/agent-studio` and the registry repository.
- Model provider accounts and any environment-only OpenAI/Anthropic keys.
- Slack app/workspace, bot-token location, scopes and channel IDs.
- New Relic account/region and API-key location.
- Cloud/Kubernetes subscription, cluster and workload-identity references.
- Container registry credentials and non-public image references.
- Current `AGENT_STUDIO_ENCRYPTION_KEY` secret-manager entry.

Named Agent Studio integration credentials stored through the UI are included in the PostgreSQL dump and remain usable only with the matching encryption key.

## 5. Prepare the new laptop

Install Git and Docker Desktop on macOS, or Git plus Docker Engine and Compose v2 on Linux. Then clone the repository:

```bash
git clone https://github.com/ravikumar10/agent-studio.git
cd agent-studio

# Use main after PR #6 is merged; otherwise recover the feature branch.
git checkout main
# git checkout feat/newrelic-document-ocr-mcp

chmod +x start.sh
```

Place `agent-studio.dump` outside the Git repository when possible. If it is temporarily under `backups/`, verify that it remains untracked.

## 6. Restore with the original encryption key

Export the exact old key into the current terminal. The value below is correct only when step 2 reported `DEFAULT_DEVELOPMENT_KEY`:

```bash
export AGENT_STUDIO_ENCRYPTION_KEY='local-development-encryption-key-change-me'
```

For a custom key, retrieve it interactively from your secret manager. Avoid placing it in shell history. For example, on a trusted interactive terminal:

```bash
read -r -s -p 'Agent Studio encryption key: ' AGENT_STUDIO_ENCRYPTION_KEY
echo
export AGENT_STUDIO_ENCRYPTION_KEY
```

Start only PostgreSQL, restore into the new local database, and then start the full platform:

```bash
docker compose --project-name agent-studio \
  -f src/deploy/compose/compose.yml up -d postgres

docker compose --project-name agent-studio \
  -f src/deploy/compose/compose.yml exec -T postgres \
  pg_restore -U agent_studio -d agent_studio \
  --clean --if-exists --no-owner --no-privileges \
  < /absolute/path/to/agent-studio.dump

./start.sh
```

`--clean` is destructive to the target database. Use it only on the new/disposable target, never casually against the original laptop's only database.

Flyway starts after restoration and applies only migrations newer than the dump. Never edit or delete applied Flyway rows to force recovery.

## 7. Verify recovery

```bash
./start.sh --status
curl -fsS http://localhost:8080/

docker compose --project-name agent-studio \
  -f src/deploy/compose/compose.yml exec -T postgres \
  psql -U agent_studio -d agent_studio \
  -c 'select version, description, success from flyway_schema_history order by installed_rank desc limit 5;'
```

In Studio, verify:

1. Agents, versions, skills, guardrails, capabilities and registry configuration are present.
2. Model and integration profiles appear masked and can pass backend verification.
3. A known agent run succeeds with its configured model/MCP providers.
4. Runs contain correlated tool/model/session events.
5. Observability shows tokens, calls and estimated cost.
6. A reused `sessionId` restores prior context.
7. Published endpoints/widgets resolve and receive unique run IDs.

Do not erase the original laptop or backup until these checks pass.

## 8. Start a new coding-agent chat

Use this prompt on the new laptop:

```text
Read AGENTS.md, README.md, codex/START_HERE.md, docs/rebuild/README.md, and docs/rebuild/RECOVERY_PROGRESS.md. Inspect git status, the current branch, migrations, and recent commits before editing. Preserve existing work, run the documented verification checklist, and continue from the recorded checkpoint. Do not assume conversation history from the old laptop is available.
```

## Recovery boundary

The migration is recoverable only when all three independent assets remain available:

1. source history from GitHub;
2. PostgreSQL backup containing local platform state;
3. the exact encryption key plus access to external secret providers.

Losing Redis is acceptable. Losing PostgreSQL loses local agents/configuration/runs. Losing the encryption key leaves encrypted credentials present but unusable.
