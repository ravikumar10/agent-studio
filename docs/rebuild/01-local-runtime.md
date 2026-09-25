# Local runtime and `start.sh`

## Host prerequisites

Only Docker Desktop on macOS or Docker Engine plus Compose v2 on Linux is required. Java, Maven, Node, PostgreSQL, Redis, browsers, and services run in containers.

```bash
git clone https://github.com/ravikumar10/agent-studio.git
cd agent-studio
chmod +x start.sh
./start.sh
```

Open `http://localhost:8080` after the readiness message.

## Launcher behavior

`start.sh` resolves paths relative to itself, selects `src/deploy/compose/compose.yml`, validates Docker/Compose, optionally pulls images, builds every service plus `agent-studio/runtime-worker:local`, starts dependencies, waits for the UI, and prints container status. Persistent PostgreSQL and Redis volumes survive ordinary stops.

Commands:

```bash
./start.sh                 # build and start
./start.sh --no-build      # start existing images
./start.sh --pull          # refresh base images, build, start
./start.sh --status        # inspect containers
./start.sh --logs          # follow logs
./start.sh --stop          # stop, preserve volumes
```

Important environment overrides:

- `AGENT_STUDIO_URL`
- `AGENT_STUDIO_START_TIMEOUT`
- `AGENT_STUDIO_ENCRYPTION_KEY`
- `RUNTIME_WORKER_TOKEN`
- `OPENAI_API_KEY` and `ANTHROPIC_API_KEY` only for optional bootstrap; normal keys belong in encrypted user connection profiles.
- `WEB_ALLOWED_HOSTS` and `BROWSER_ALLOWED_HOSTS`, default `*` for all public HTTP(S) hosts.

## Recovery

If the runtime reports `No such image: agent-studio/runtime-worker:local`, run `./start.sh` without `--no-build`. If migrations fail, inspect control-plane logs and correct the migration; never edit an already-applied migration. Add a new migration instead. If the UI is stale, rebuild and recreate `studio-web`, then reload the browser.

Do not delete volumes as a normal repair step. Destructive reset is an explicit operator decision because it erases profiles, agents, runs, and secrets.
