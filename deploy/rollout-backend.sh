#!/usr/bin/env bash
# Zero-downtime rollout of the API.
#
# The compose file defines two identical slots, backend-blue and backend-green, that both
# answer to the hostname `backend`. This starts the idle slot on the current
# habtamuwgs/housing-platform-backend:latest image, waits for its health check, then
# stops the slot that was serving. The frontend's nginx resolves `backend` per request
# (resolver 127.0.0.11, valid=10s) and retries the next address on a connection error,
# so traffic moves to the new slot without a gap.
#
# Memory: both slots run side by side for a minute or two. A slot needs about 400 MB
# while it boots (512 MB heap ceiling, 1 GB hard cap). On a host with less than
# MIN_FREE_MB available while the old slot is still up, it falls back to
# stop-then-start and says so, rather than letting the kernel pick a process to kill.
#
# Usage: bash deploy/rollout-backend.sh            (from the backend checkout)
# Exit 0 when the new slot is serving; non-zero and the old slot left running otherwise.
set -euo pipefail

# Both profiles on, so ps/stop/rm/logs can name either slot; `up -d --no-deps <slot>`
# still starts only the slot named.
COMPOSE=${COMPOSE:-"docker compose --profile blue --profile green -f deploy/docker-compose.prod.yml"}
MIN_FREE_MB=${MIN_FREE_MB:-550}
HEALTH_TIMEOUT=${HEALTH_TIMEOUT:-240}

running() { # running <service> -> prints container id if the slot is up
  docker ps -q --filter label=com.docker.compose.project=ethiobuildconnect \
                --filter label=com.docker.compose.service="$1" | head -n1
}

blue=$(running backend-blue || true)
green=$(running backend-green || true)
# The container from before the two-slot layout (service "backend"), if it is still
# serving. Compose no longer knows that service, so it is found by label.
legacy=$(docker ps -q --filter label=com.docker.compose.project=ethiobuildconnect \
                      --filter label=com.docker.compose.service=backend | head -n1)

if [ -n "$blue" ] && [ -n "$green" ]; then
  # Both up (an interrupted rollout). Keep blue, retire green, then continue from blue.
  echo "Both slots are running; stopping backend-green before the rollout."
  $COMPOSE stop backend-green && $COMPOSE rm -f backend-green
  green=""
fi

if [ -n "$green" ]; then old=backend-green; new=backend-blue
else                     old=backend-blue;  new=backend-green; fi
[ -n "$blue$green" ] || old=""
if [ -z "$old" ] && [ -n "$legacy" ]; then old="legacy:$legacy"; fi

stop_old() { # stop_old <slot|legacy:id>
  case "$1" in
    legacy:*) docker stop -t 30 "${1#legacy:}" >/dev/null && docker rm "${1#legacy:}" >/dev/null ;;
    *) $COMPOSE stop "$1" && $COMPOSE rm -f "$1" ;;
  esac
}

echo "Rollout: ${old:-nothing} -> $new"

overlap=true
if [ -n "$old" ]; then
  avail=$(awk '/MemAvailable/ {print int($2/1024)}' /proc/meminfo)
  echo "Memory available before rollout: ${avail} MB (need ${MIN_FREE_MB} MB to overlap)"
  free -m | sed -n '1,3p'
  docker stats --no-stream --format 'table {{.Name}}\t{{.MemUsage}}' 2>/dev/null | sed -n '1,25p' || true
  if [ "$avail" -lt "$MIN_FREE_MB" ]; then
    echo "::warning::Only ${avail} MB available; not enough for two API slots. Stopping $old first (brief downtime)."
    stop_old "$old"
    overlap=false
  fi
fi

$COMPOSE up -d --no-deps "$new"

cid=$($COMPOSE ps -q "$new" | head -n1)
echo "Waiting for $new ($cid) to become healthy..."
deadline=$(( $(date +%s) + HEALTH_TIMEOUT ))
status=starting
while [ "$(date +%s)" -lt "$deadline" ]; do
  status=$(docker inspect -f '{{.State.Health.Status}}' "$cid" 2>/dev/null || echo missing)
  case "$status" in
    healthy) break ;;
    missing|unhealthy) break ;;
  esac
  sleep 5
done

if [ "$status" != "healthy" ]; then
  echo "::error::$new did not become healthy (status: $status)."
  $COMPOSE logs --tail 80 "$new" || true
  $COMPOSE stop "$new" >/dev/null 2>&1 || true
  $COMPOSE rm -f "$new" >/dev/null 2>&1 || true
  if [ "$overlap" = true ] && [ -n "$old" ]; then
    echo "$old is still serving the previous release."
  fi
  exit 1
fi
echo "$new is healthy."

if [ "$overlap" = true ] && [ -n "$old" ]; then
  # Stop the old slot. Docker drops it from the `backend` alias at once; nginx keeps a
  # resolved address for at most 10 s and retries the other address on a refused
  # connection, so the switch is seamless. Spring finishes in-flight requests first.
  echo "Stopping $old..."
  stop_old "$old"
fi
echo "Rollout complete: $new is serving."
