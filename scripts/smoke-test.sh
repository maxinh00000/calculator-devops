#!/usr/bin/env bash
# Post-deploy verification: waits for the stack to become healthy, then checks the API.
# Usage: ./scripts/smoke-test.sh [base_url]
set -euo pipefail

BASE_URL="${1:-http://localhost:8085}"
RETRIES=30

echo "Waiting for ${BASE_URL}/actuator/health ..."
for i in $(seq 1 "$RETRIES"); do
  if curl -fs "${BASE_URL}/actuator/health" | grep -q '"status":"UP"'; then
    echo "Health is UP (attempt $i)"
    break
  fi
  if [ "$i" -eq "$RETRIES" ]; then
    echo "Service did not become healthy in time" >&2
    exit 1
  fi
  sleep 4
done

check() {
  local path="$1" expected_code="$2" expected_body="$3"
  local body code
  body=$(curl -s -w '\n%{http_code}' "${BASE_URL}${path}")
  code=$(echo "$body" | tail -n1)
  body=$(echo "$body" | sed '$d')
  if [ "$code" != "$expected_code" ] || ! echo "$body" | grep -q "$expected_body"; then
    echo "FAIL ${path} -> ${code} ${body}" >&2
    exit 1
  fi
  echo "PASS ${path} -> ${code} ${body}"
}

check "/api/v1/add?a=2&b=3"       200 '"result":5.0'
check "/api/v1/subtract?a=10&b=4" 200 '"result":6.0'
check "/api/v1/multiply?a=6&b=7"  200 '"result":42.0'
check "/api/v1/divide?a=7&b=2"    200 '"result":3.5'
check "/api/v1/divide?a=5&b=0"    400 'Division by zero'
check "/api/v1/add?a=2"           400 'Missing required parameter'

echo "All smoke tests passed."
