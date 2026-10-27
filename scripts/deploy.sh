#!/usr/bin/env bash
# Idempotent rolling deploy with Docker Compose.
#  - Replicas are updated one at a time; each must be healthy before the next is touched,
#    so the proxy always has at least one healthy backend (no downtime on re-runs).
#  - If nothing changed, every `up -d` is a no-op, so re-running is safe.
# Usage: IMAGE_TAG=<tag> DOCKERHUB_USER=<user> ./scripts/deploy.sh
set -euo pipefail

PROJECT="calculator"
COMPOSE="docker compose -p ${PROJECT}"
: "${IMAGE_TAG:=latest}"
export IMAGE_TAG

wait_healthy() {
  local svc="$1" cid status
  cid=$($COMPOSE ps -q "$svc")
  for i in $(seq 1 40); do
    status=$(docker inspect --format '{{.State.Health.Status}}' "$cid" 2>/dev/null || echo "unknown")
    if [ "$status" = "healthy" ]; then
      echo "  $svc is healthy"
      return 0
    fi
    sleep 3
  done
  echo "  $svc did not become healthy (last status: $status)" >&2
  docker logs --tail 50 "$cid" >&2 || true
  return 1
}

echo "Deploying ${DOCKERHUB_USER:-maxinh0}/calculator:${IMAGE_TAG}"
for svc in calculator-1 calculator-2; do
  echo "Updating $svc ..."
  $COMPOSE up -d --no-deps "$svc"
  wait_healthy "$svc"
done

echo "Bringing up the rest of the stack (proxy) ..."
$COMPOSE up -d --remove-orphans
wait_healthy nginx
# Make Nginx re-resolve backend IPs immediately (belt and braces with `resolve`)
$COMPOSE exec -T nginx nginx -s reload

$COMPOSE ps
