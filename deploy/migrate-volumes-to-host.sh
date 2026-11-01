#!/usr/bin/env bash
# One-time move of the database and uploads from Docker named volumes to host directories.
#
# Earlier versions of docker-compose.prod.yml kept both in named volumes
# (ethiobuildconnect_postgres-data, ethiobuildconnect_uploads). The compose file now bind
# mounts $DATA_DIR/postgres and $DATA_DIR/uploads instead, so Docker can never delete them.
# This copies whatever the old volumes hold into those directories — once.
#
# Idempotent and safe to run on every deploy (the workflow does):
#   - a volume that does not exist          -> nothing to do
#   - a host directory already populated     -> left alone, never overwritten
#   - otherwise                              -> postgres/backend/frontend stopped, volume
#                                               copied in, then `up -d frontend` recreates all
#                                               three on the new mounts (caddy never stops)
# The old volumes are left in place. Remove them by hand once the site has been verified:
#   docker volume rm ethiobuildconnect_postgres-data ethiobuildconnect_uploads
#
# Runs as any user in the docker group; root is not needed because the copy and chown
# happen inside a throwaway container.
set -euo pipefail
STOPPED=0

HERE="$(cd "$(dirname "$0")" && pwd)"
COMPOSE="docker compose -f $HERE/docker-compose.prod.yml"
# Same default as the compose file; deploy/.env may override it.
DATA_DIR="$(grep -E '^DATA_DIR=' "$HERE/.env" 2>/dev/null | cut -d= -f2- | tr -d '"' || true)"
DATA_DIR="${DATA_DIR:-/var/lib/ethiobuildconnect}"

copy_volume() {
  local volume="$1" dest="$2" owner="$3"
  docker volume inspect "$volume" >/dev/null 2>&1 || { echo "$volume: no such volume, nothing to migrate"; return 0; }
  # Bind-mounting creates $dest (root-owned) if it is missing; the copy is skipped if it has content.
  if docker run --rm -v "$dest":/dst alpine sh -c '[ -n "$(ls -A /dst)" ]'; then
    echo "$dest already has content — leaving it alone (volume $volume untouched)"; return 0
  fi
  echo "stopping services that use $volume"
  $COMPOSE stop backend frontend postgres >/dev/null; STOPPED=1
  echo "copying $volume -> $dest"
  # cp -a keeps numeric uid/gid, so postgres' files stay postgres' (uid 999) as they were.
  docker run --rm -v "$volume":/src:ro -v "$dest":/dst alpine sh -c 'cp -a /src/. /dst/'
  [ -n "$owner" ] && docker run --rm -v "$dest":/dst alpine chown -R "$owner" /dst
  echo "done: $(docker run --rm -v "$dest":/dst alpine sh -c 'du -sh /dst | cut -f1') in $dest"
}

# The backend runs as the unprivileged `spring` user and must be able to write uploads;
# ask the image for its uid:gid rather than guessing.
BACKEND_IMAGE="habtamuwgs/housing-platform-backend:latest"
SPRING_IDS="$(docker run --rm --entrypoint sh "$BACKEND_IMAGE" -c 'echo "$(id -u):$(id -g)"' 2>/dev/null || echo "")"

copy_volume ethiobuildconnect_postgres-data "$DATA_DIR/postgres" ""
copy_volume ethiobuildconnect_uploads       "$DATA_DIR/uploads"  "$SPRING_IDS"

# Even with nothing to copy, the uploads directory must exist and be writable by the
# backend before it starts — a fresh droplet with no old volume lands here.
docker run --rm -v "$DATA_DIR/uploads":/dst alpine sh -c "chmod 755 /dst; [ -n '$SPRING_IDS' ] && chown $SPRING_IDS /dst || true"
# LiveKit egress (uid 1001, gid 0) writes MP4 recordings here; a root-owned directory
# makes it fail silently. Harmless when live video is not enabled.
docker run --rm -v "$DATA_DIR/uploads":/dst alpine sh -c "mkdir -p /dst/live-recordings && chown 1001:0 /dst/live-recordings && chmod 775 /dst/live-recordings"

# Containers created against the old volumes must be recreated, not merely started, to
# pick up the bind mounts. frontend depends on backend depends on postgres, so this one
# `up` brings all three back on the new paths; the caller's own `up -d` is then a no-op.
if [ "$STOPPED" = 1 ]; then
  echo "recreating postgres, backend and frontend on the host directories"
  $COMPOSE up -d frontend
fi
