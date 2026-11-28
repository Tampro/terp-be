#!/usr/bin/env bash
# Brings an ERP stack created before delivery-service up to date, on its existing volumes:
#   1. database erp_delivery + user (the MySQL init script only runs on an empty volume)
#   2. Keycloak client erp-delivery-service (realm erp) (realm import only runs when the realm does not exist yet)
#   3. rebuild + recreate iam (rider check, DELIVERY_* seed), order-service (order.paid, internal read) and
#      delivery-service, then the gateway (delivery route)
#
# Needs in .env (read by docker compose only; this script never reads or prints it):
#   DELIVERY_DB_PASSWORD, DELIVERY_SERVICE_CLIENT_SECRET
# Safe to run again: creates what is missing and resets the delivery DB password / client secret to the .env values.
#
# Usage (repo root):  scripts/upgrade-delivery-service.sh [--legacy-build]
#   --legacy-build  build the images with `DOCKER_BUILDKIT=0 docker build` (when `compose --build` is refused by buildx);
#                   needs the jars first: mvn -DskipTests package
set -euo pipefail

PROJECT="${COMPOSE_PROJECT:-erp}"
SERVICES=(iam-service order-service delivery-service)
LEGACY=0
[[ "${1:-}" == "--legacy-build" ]] && LEGACY=1

cd "$(dirname "$0")/.."
compose() { docker compose -p "$PROJECT" "$@"; }
step() { printf '\n== %s\n' "$*"; }

wait_healthy() { # container, tries
  for _ in $(seq 1 "$2"); do
    [[ "$(docker inspect -f '{{.State.Health.Status}}' "$1" 2>/dev/null)" == healthy ]] && return 0
    sleep 3
  done
  echo "$1 is not healthy" >&2
  return 1
}

step "MySQL: database erp_delivery and its user"
compose up -d --no-deps --no-build mysql
wait_healthy "$PROJECT-mysql-1" 60
docker exec "$PROJECT-mysql-1" sh -c '
  : "${DELIVERY_DB_PASSWORD:?DELIVERY_DB_PASSWORD missing in the mysql container: add it to .env}"
  U="${DELIVERY_DB_USERNAME:-erp_delivery}"
  mysql -uroot -p"$MYSQL_ROOT_PASSWORD" 2>&1 <<SQL | grep -v "Using a password" || true
CREATE DATABASE IF NOT EXISTS erp_delivery CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '"'"'$U'"'"'@'"'"'%'"'"' IDENTIFIED BY '"'"'$DELIVERY_DB_PASSWORD'"'"';
ALTER USER '"'"'$U'"'"'@'"'"'%'"'"' IDENTIFIED BY '"'"'$DELIVERY_DB_PASSWORD'"'"';
GRANT ALL PRIVILEGES ON erp_delivery.* TO '"'"'$U'"'"'@'"'"'%'"'"';
FLUSH PRIVILEGES;
SQL
  mysql -u"$U" -p"$DELIVERY_DB_PASSWORD" erp_delivery -e "SELECT CURRENT_USER()" -N 2>&1 | grep -v "Using a password"'

step "Keycloak: client erp-delivery-service"
compose up -d --no-deps --no-build keycloak
for _ in $(seq 1 60); do
  docker exec "$PROJECT-keycloak-1" sh -c 'exec 3<>/dev/tcp/127.0.0.1/8080' 2>/dev/null && break
  sleep 3
done
sleep 5
docker exec "$PROJECT-keycloak-1" sh -c '
  : "${DELIVERY_SERVICE_CLIENT_SECRET:?DELIVERY_SERVICE_CLIENT_SECRET missing in the keycloak container: add it to .env}"
  K=/opt/keycloak/bin/kcadm.sh
  ok=0
  for _ in 1 2 3 4 5 6 7 8 9 10; do
    $K config credentials --server http://localhost:8080 --realm master \
      --user "$KC_BOOTSTRAP_ADMIN_USERNAME" --password "$KC_BOOTSTRAP_ADMIN_PASSWORD" >/dev/null 2>&1 && { ok=1; break; }
    sleep 3
  done
  [ "$ok" = 1 ] || { echo "kcadm login failed" >&2; exit 1; }
  ID=$($K get clients -r erp -q clientId=erp-delivery-service --fields id --format csv --noquotes 2>/dev/null | head -1)
  if [ -n "$ID" ]; then
    $K update "clients/$ID" -r erp -s "secret=$DELIVERY_SERVICE_CLIENT_SECRET" && echo "client exists: secret set"
  else
    $K create clients -r erp -s clientId=erp-delivery-service -s "name=delivery-service account (order snapshot, rider check)" \
      -s enabled=true -s publicClient=false -s clientAuthenticatorType=client-secret \
      -s "secret=$DELIVERY_SERVICE_CLIENT_SECRET" -s standardFlowEnabled=false -s directAccessGrantsEnabled=false \
      -s implicitFlowEnabled=false -s serviceAccountsEnabled=true >/dev/null && echo "client created"
  fi
  exec 3<>/dev/tcp/127.0.0.1/8080
  B="grant_type=client_credentials&client_id=erp-delivery-service&client_secret=$DELIVERY_SERVICE_CLIENT_SECRET"
  printf "POST /realms/erp/protocol/openid-connect/token HTTP/1.1\r\nHost: localhost\r\nContent-Type: application/x-www-form-urlencoded\r\nContent-Length: ${#B}\r\nConnection: close\r\n\r\n$B" >&3
  status=$(head -1 <&3)
  echo "token check: $status"
  case "$status" in *" 200 "*) ;; *) exit 1 ;; esac'

step "Services: ${SERVICES[*]} and the gateway"
if [[ "$LEGACY" == 1 ]]; then
  for s in "${SERVICES[@]}" gateway-server; do
    (cd "services/$s" && DOCKER_BUILDKIT=0 docker build -q -t "$PROJECT-$s" . >/dev/null)
  done
  compose up -d --no-deps --no-build --force-recreate "${SERVICES[@]}"
else
  compose up -d --no-deps --build --force-recreate "${SERVICES[@]}"
fi
for s in "${SERVICES[@]}"; do
  for _ in $(seq 1 60); do
    docker logs "$PROJECT-$s-1" 2>&1 | grep -qE "Started [A-Za-z]+Application|Application run failed" && break
    sleep 4
  done
  docker logs "$PROJECT-$s-1" 2>&1 | grep -oE "Started [A-Za-z]+Application|Application run failed" | tail -1 | sed "s/^/$s: /"
done
# Recreated (not restarted): the gateway must load its new image and resolve the new upstream IPs.
if [[ "$LEGACY" == 1 ]]; then
  compose up -d --no-deps --no-build --force-recreate gateway-server
else
  compose up -d --no-deps --build --force-recreate gateway-server
fi

step "Done. Check with: node e2e/run.mjs delivery   (give the gateway ~30 s to warm up)"
