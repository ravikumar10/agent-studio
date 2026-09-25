#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="$ROOT_DIR/src/deploy/compose/compose.yml"
PROJECT_NAME="agent-studio"
export COMPOSE_PROJECT_NAME="$PROJECT_NAME"
UI_URL="${AGENT_STUDIO_URL:-http://localhost:8080}"
WAIT_SECONDS="${AGENT_STUDIO_START_TIMEOUT:-180}"

usage() {
  cat <<'EOF'
Agent Studio local/on-prem launcher

Usage:
  ./start.sh                 Build, start, and wait for Agent Studio
  ./start.sh --no-build      Start existing images without rebuilding
  ./start.sh --pull          Pull base images before building
  ./start.sh --stop          Stop the stack (preserves database volumes)
  ./start.sh --status        Show service and container status
  ./start.sh --logs          Follow service logs
  ./start.sh --help          Show this help

Environment overrides:
  AGENT_STUDIO_URL=http://localhost:8080
  AGENT_STUDIO_START_TIMEOUT=180
  AGENT_STUDIO_ENCRYPTION_KEY=<strong-random-secret>
  RUNTIME_WORKER_TOKEN=<strong-random-secret>
  OPENAI_API_KEY=<optional>
  ANTHROPIC_API_KEY=<optional>

Host prerequisite: Docker Engine/Desktop with Docker Compose v2.
Java, Maven, Node, PostgreSQL, and Redis run inside containers.
EOF
}

die() { printf 'Error: %s\n' "$*" >&2; exit 1; }
info() { printf '\n==> %s\n' "$*"; }

require_docker() {
  command -v docker >/dev/null 2>&1 || die "Docker is not installed. Install Docker Desktop (macOS) or Docker Engine with Compose v2: https://docs.docker.com/engine/install/"
  docker compose version >/dev/null 2>&1 || die "Docker Compose v2 is required. Confirm that 'docker compose version' works."
  docker info >/dev/null 2>&1 || die "Docker is installed but its daemon is unavailable. Start Docker Desktop or the Docker service."
}

compose() { docker compose --project-name "$PROJECT_NAME" -f "$COMPOSE_FILE" "$@"; }

wait_for_studio() {
  local elapsed=0
  info "Waiting for Agent Studio at $UI_URL (timeout: ${WAIT_SECONDS}s)"
  while (( elapsed < WAIT_SECONDS )); do
    if command -v curl >/dev/null 2>&1; then
      if curl --silent --fail --max-time 2 "$UI_URL/" >/dev/null 2>&1; then return 0; fi
    elif compose exec -T studio-web wget -q -O /dev/null http://localhost/ >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
    elapsed=$((elapsed + 2))
    printf '.'
  done
  printf '\n' >&2
  compose ps >&2 || true
  compose logs --tail=100 control-plane runtime-service studio-web >&2 || true
  die "Agent Studio did not become ready within ${WAIT_SECONDS}s."
}

action="start"
build=true
pull=false
for argument in "$@"; do
  case "$argument" in
    --no-build) build=false ;;
    --pull) pull=true ;;
    --stop) action="stop" ;;
    --status) action="status" ;;
    --logs) action="logs" ;;
    --help|-h) usage; exit 0 ;;
    *) usage >&2; die "Unknown option: $argument" ;;
  esac
done

require_docker

case "$action" in
  stop)
    info "Stopping Agent Studio (persistent volumes are preserved)"
    compose down
    exit 0
    ;;
  status)
    compose ps
    exit 0
    ;;
  logs)
    compose logs --follow --tail=200
    exit 0
    ;;
esac

if [[ "$pull" == true ]]; then
  info "Pulling container base images"
  compose pull --ignore-buildable
fi

if [[ "$build" == true ]]; then
  info "Building Agent Studio and the ephemeral Docker worker image"
  compose build docker-agent-worker
  compose build
fi

info "Starting Agent Studio services"
if [[ "$build" == true ]]; then
  compose up --detach --remove-orphans
else
  compose up --detach --remove-orphans --no-build
fi

wait_for_studio
printf '\n'
compose ps
cat <<EOF

Agent Studio is ready: $UI_URL

Useful commands:
  ./start.sh --status
  ./start.sh --logs
  ./start.sh --stop

Data is stored in Docker volumes and survives normal stops/restarts.
EOF
