#!/usr/bin/env bash
set -euo pipefail

image=${1:?Usage: smoke-backend.sh IMAGE}
run_name="restaurant-smoke-$$-$RANDOM"
mysql_container="$run_name-mysql"
backend_container="$run_name-backend"
mysql_password=$(openssl rand -hex 24)

cleanup() {
  local status=$?
  if (( status != 0 )); then
    docker logs "$mysql_container" >&2 || true
    docker logs "$backend_container" >&2 || true
  fi
  docker rm -fv "$backend_container" "$mysql_container" >/dev/null 2>&1 || true
  docker network rm "$run_name" >/dev/null 2>&1 || true
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

wait_until_ready() {
  local service=$1
  local container=$2
  local timeout_seconds=$3
  shift 3
  local deadline=$((SECONDS + timeout_seconds))

  until timeout 5s "$@" >/dev/null 2>&1; do
    if [[ $(docker inspect --format '{{.State.Running}}' "$container") != true ]]; then
      echo "$service stopped before it became ready." >&2
      return 1
    fi
    if (( SECONDS >= deadline )); then
      echo "$service did not become ready within ${timeout_seconds}s." >&2
      return 1
    fi
    sleep 2
  done
}

docker network create "$run_name" >/dev/null
docker run -d --name "$mysql_container" --network "$run_name" --network-alias mysql \
  -e MYSQL_DATABASE=restaurant \
  -e MYSQL_USER=restaurant \
  -e MYSQL_PASSWORD="$mysql_password" \
  -e MYSQL_RANDOM_ROOT_PASSWORD=yes \
  mysql:8.4 --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci >/dev/null

wait_until_ready MySQL "$mysql_container" 180 \
  docker exec -e MYSQL_PWD="$mysql_password" "$mysql_container" \
  mysql --protocol=TCP -h 127.0.0.1 -u restaurant restaurant -e 'SELECT 1'

docker run -d --name "$backend_container" --network "$run_name" \
  -e SPRING_PROFILES_ACTIVE=docker \
  -e SERVER_PORT=8080 \
  -e API_PREFIX=/api/v1 \
  -e 'SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/restaurant?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Ho_Chi_Minh&characterEncoding=UTF-8' \
  -e SPRING_DATASOURCE_USERNAME=restaurant \
  -e SPRING_DATASOURCE_PASSWORD="$mysql_password" \
  -e SPRING_DATASOURCE_DRIVER_CLASS_NAME=com.mysql.cj.jdbc.Driver \
  -e SPRING_JPA_HIBERNATE_DDL_AUTO=update \
  -e SPRING_JPA_SHOW_SQL=false \
  -e SPRING_JPA_PROPERTIES_HIBERNATE_DIALECT=org.hibernate.dialect.MySQLDialect \
  -e SPRING_JPA_DEFER_DATASOURCE_INITIALIZATION=false \
  -e SPRING_SQL_INIT_MODE=never \
  -e JWT_ACCESS_SECRET="$(openssl rand -base64 32)" \
  -e JWT_REFRESH_SECRET="$(openssl rand -base64 32)" \
  -e JWT_ACCESS_EXPIRATION_MS=3600000 \
  -e JWT_REFRESH_EXPIRATION_DAYS=7 \
  -e CLOUDINARY_URL=cloudinary://local:local@local-demo \
  "$image" >/dev/null

wait_until_ready Backend "$backend_container" 300 \
  docker exec "$backend_container" \
  curl --fail --silent --max-time 4 http://127.0.0.1:8080/actuator/health/readiness

echo 'Backend readiness check passed with MySQL.'
