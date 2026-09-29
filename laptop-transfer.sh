#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="$ROOT_DIR/src/deploy/compose/compose.yml"
TARGET_PROJECT="agent-studio"

usage() {
  cat <<'EOF'
Agent Studio laptop transfer

Usage:
  ./laptop-transfer.sh backup [destination-directory]
  ./laptop-transfer.sh restore <transfer-archive.tar.gz> [--force]

backup  Creates one owner-only archive containing the PostgreSQL dump,
        dump manifest, exact running encryption key, metadata and checksums.
        The default destination is ./backups.

restore Validates an archive, restores PostgreSQL into the agent-studio Docker
        project, exports the recovered encryption key only inside this process,
        and builds/starts the complete stack. Refuses to overwrite an initialized
        database unless --force is supplied.

The transfer archive contains sensitive data. Keep it in encrypted storage and
never commit it to Git or send it through an untrusted channel.
EOF
}

die() { printf 'Error: %s\n' "$*" >&2; exit 1; }
info() { printf '\n==> %s\n' "$*"; }

require_command() {
  command -v "$1" >/dev/null 2>&1 || die "$1 is required"
}

require_docker() {
  require_command docker
  docker compose version >/dev/null 2>&1 || die "Docker Compose v2 is required"
  docker info >/dev/null 2>&1 || die "Docker daemon is unavailable"
}

compose_for() {
  local project="$1"
  shift
  docker compose --project-name "$project" -f "$COMPOSE_FILE" "$@"
}

running_container() {
  local project="$1" service="$2"
  compose_for "$project" ps -q "$service" 2>/dev/null || true
}

detect_source_project() {
  local id project
  id="$(running_container "$TARGET_PROJECT" postgres)"
  if [[ -n "$id" ]]; then
    printf '%s' "$TARGET_PROJECT"
    return
  fi

  id="$(docker compose -f "$COMPOSE_FILE" ps -q postgres 2>/dev/null || true)"
  [[ -n "$id" ]] || die "No running Agent Studio PostgreSQL container was found. Run ./start.sh first."
  project="$(docker inspect "$id" --format '{{ index .Config.Labels "com.docker.compose.project" }}')"
  [[ -n "$project" ]] || die "Could not determine the running Compose project"
  printf '%s' "$project"
}

backup() {
  local destination="${1:-$ROOT_DIR/backups}"
  local source_project postgres_id control_id sample_id stamp package_name stage archive

  require_docker
  require_command tar
  require_command shasum

  source_project="$(detect_source_project)"
  postgres_id="$(running_container "$source_project" postgres)"
  control_id="$(running_container "$source_project" control-plane)"
  [[ -n "$postgres_id" ]] || die "PostgreSQL is not running"
  [[ -n "$control_id" ]] || die "Control plane is not running; the active encryption key cannot be captured"

  mkdir -p "$destination"
  chmod 700 "$destination"
  stamp="$(date +%Y%m%d-%H%M%S)"
  package_name="agent-studio-transfer-$stamp"
  stage="$(mktemp -d "${TMPDIR:-/tmp}/${package_name}.XXXXXX")"
  archive="$destination/$package_name.tar.gz"
  trap "rm -rf -- '$stage'" EXIT
  mkdir -p "$stage/$package_name"
  chmod 700 "$stage/$package_name"

  info "Exporting Agent Studio PostgreSQL from Compose project $source_project"
  docker exec "$postgres_id" pg_dump -U agent_studio -d agent_studio -Fc \
    > "$stage/$package_name/agent-studio.dump"
  [[ -s "$stage/$package_name/agent-studio.dump" ]] || die "PostgreSQL dump is empty"

  docker exec -i "$postgres_id" pg_restore --list \
    < "$stage/$package_name/agent-studio.dump" \
    > "$stage/$package_name/agent-studio.dump.manifest"
  [[ -s "$stage/$package_name/agent-studio.dump.manifest" ]] || die "Dump manifest is empty"

  docker inspect "$control_id" --format '{{range .Config.Env}}{{println .}}{{end}}' \
    | sed -n 's/^AGENT_STUDIO_ENCRYPTION_KEY=//p' \
    > "$stage/$package_name/agent-studio-encryption-key.txt"
  [[ -s "$stage/$package_name/agent-studio-encryption-key.txt" ]] || die "Encryption key was not present in the control-plane container"

  sample_id="$(running_container "$source_project" sample-data)"
  if [[ -n "$sample_id" ]]; then
    info "Exporting optional sample catalog database"
    docker exec "$sample_id" pg_dump -U sample_reader -d sample_catalog -Fc \
      > "$stage/$package_name/sample-catalog.dump"
  fi

  {
    printf 'created_at=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    printf 'source_compose_project=%s\n' "$source_project"
    printf 'git_commit=%s\n' "$(git -C "$ROOT_DIR" rev-parse HEAD 2>/dev/null || printf unknown)"
    printf 'git_branch=%s\n' "$(git -C "$ROOT_DIR" branch --show-current 2>/dev/null || printf unknown)"
    printf 'database=agent_studio\n'
    printf 'database_user=agent_studio\n'
  } > "$stage/$package_name/metadata.txt"

  chmod 600 "$stage/$package_name"/*
  (
    cd "$stage/$package_name"
    shasum -a 256 agent-studio.dump agent-studio.dump.manifest agent-studio-encryption-key.txt metadata.txt > CHECKSUMS.sha256
    if [[ -f sample-catalog.dump ]]; then shasum -a 256 sample-catalog.dump >> CHECKSUMS.sha256; fi
  )
  chmod 600 "$stage/$package_name/CHECKSUMS.sha256"

  tar -C "$stage" -czf "$archive" "$package_name"
  chmod 600 "$archive"
  [[ -s "$archive" ]] || die "Transfer archive is empty"

  info "Transfer archive created and validated"
  printf 'Archive: %s\n' "$archive"
  printf 'SHA-256: '
  shasum -a 256 "$archive" | awk '{print $1}'
  printf 'Copy this archive to encrypted external storage before leaving the old laptop.\n'
}

restore() {
  local archive="${1:-}" force="${2:-}" work package_dir postgres_id sample_id key initialized
  [[ -n "$archive" ]] || die "A transfer archive path is required"
  [[ -f "$archive" ]] || die "Transfer archive not found: $archive"
  if [[ -n "$force" && "$force" != "--force" ]]; then die "Unknown restore option: $force"; fi

  require_docker
  require_command tar
  require_command shasum

  work="$(mktemp -d "${TMPDIR:-/tmp}/agent-studio-restore.XXXXXX")"
  trap "rm -rf -- '$work'" EXIT
  tar -xzf "$archive" -C "$work"
  package_dir="$(find "$work" -mindepth 1 -maxdepth 1 -type d -name 'agent-studio-transfer-*' | head -n 1)"
  [[ -n "$package_dir" ]] || die "Archive does not contain an Agent Studio transfer package"

  for required in agent-studio.dump agent-studio.dump.manifest agent-studio-encryption-key.txt metadata.txt CHECKSUMS.sha256; do
    [[ -s "$package_dir/$required" ]] || die "Transfer package is missing $required"
  done

  info "Validating transfer checksums"
  (cd "$package_dir" && shasum -a 256 -c CHECKSUMS.sha256)

  IFS= read -r key < "$package_dir/agent-studio-encryption-key.txt"
  [[ -n "$key" ]] || die "Recovered encryption key is empty"
  export AGENT_STUDIO_ENCRYPTION_KEY="$key"

  info "Preparing PostgreSQL in Compose project $TARGET_PROJECT"
  compose_for "$TARGET_PROJECT" down >/dev/null 2>&1 || true
  compose_for "$TARGET_PROJECT" up -d postgres
  postgres_id="$(running_container "$TARGET_PROJECT" postgres)"
  [[ -n "$postgres_id" ]] || die "PostgreSQL container did not start"

  for _ in $(seq 1 30); do
    if docker exec "$postgres_id" pg_isready -U agent_studio -d agent_studio >/dev/null 2>&1; then break; fi
    sleep 1
  done
  docker exec "$postgres_id" pg_isready -U agent_studio -d agent_studio >/dev/null 2>&1 || die "PostgreSQL did not become ready"

  initialized="$(docker exec "$postgres_id" psql -U agent_studio -d agent_studio -Atc "select count(*) from information_schema.tables where table_schema='public'" | tr -d '[:space:]')"
  if [[ "${initialized:-0}" != "0" && "$force" != "--force" ]]; then
    die "Target database already contains tables. Re-run with --force only if it is safe to overwrite this target."
  fi

  info "Restoring Agent Studio PostgreSQL"
  docker exec -i "$postgres_id" pg_restore -U agent_studio -d agent_studio \
    --clean --if-exists --no-owner --no-privileges \
    < "$package_dir/agent-studio.dump"

  if [[ -s "$package_dir/sample-catalog.dump" ]]; then
    info "Starting and restoring optional sample catalog database"
    compose_for "$TARGET_PROJECT" up -d sample-data
    sample_id="$(running_container "$TARGET_PROJECT" sample-data)"
    for _ in $(seq 1 30); do
      if docker exec "$sample_id" pg_isready -U sample_reader -d sample_catalog >/dev/null 2>&1; then break; fi
      sleep 1
    done
    docker exec -i "$sample_id" pg_restore -U sample_reader -d sample_catalog \
      --clean --if-exists --no-owner --no-privileges \
      < "$package_dir/sample-catalog.dump"
  fi

  info "Building and starting Agent Studio with the recovered encryption key"
  "$ROOT_DIR/start.sh"
  printf '\nRestore completed. Verify models/integrations, one agent run, sessions, Runs and Observability before deleting the original laptop or archive.\n'
}

case "${1:-}" in
  backup) backup "${2:-}" ;;
  restore) restore "${2:-}" "${3:-}" ;;
  --help|-h|help|"") usage ;;
  *) usage >&2; die "Unknown action: $1" ;;
esac
