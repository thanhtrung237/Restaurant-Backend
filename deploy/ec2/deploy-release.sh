#!/usr/bin/env bash
set -Eeuo pipefail
umask 077
cd /home/ubuntu/restaurant-ec2

mkdir -p .runtime
exec 9>.runtime/deploy.lock
flock -n 9 || { echo 'Another deployment is running.' >&2; exit 1; }
unset BACKEND_IMAGE
compose=(docker compose --env-file .env.ec2 -f compose.yaml)
"${compose[@]}" config --quiet
for service in mysql backend; do
    container=$("${compose[@]}" ps -q "$service")
    [[ -n "$container" && $(docker inspect -f '{{.State.Health.Status}}' "$container") == healthy ]] || {
        echo "$service must be healthy before release." >&2
        exit 1
    }
done

if [[ ${1:-} == --check ]]; then
    docker info --format 'Docker {{.ServerVersion}}'
    "${compose[@]}" ps
    exit 0
fi

image=${1:-}
[[ $image =~ ^docker[.]io/[a-z0-9][a-z0-9_-]*/restaurant-backend@sha256:[a-f0-9]{64}$ ]] || {
    echo 'Expected a digest from the restaurant-backend Docker Hub repository.' >&2
    exit 2
}
grep -q '^BACKEND_IMAGE=' .env.ec2
previous_id=$(docker inspect -f '{{.Image}}' "$container")
previous_image=$(docker inspect -f '{{.Config.Image}}' "$container")
docker image inspect "$previous_image" >/dev/null
[[ $(docker image inspect -f '{{.Id}}' "$previous_image") == "$previous_id" ]] || {
    echo 'The active image reference has changed. Restore it before releasing.' >&2
    exit 1
}
docker pull "$image"
candidate=$(mktemp .env.ec2.release.XXXXXX)
awk -v image="$image" '/^BACKEND_IMAGE=/ {$0="BACKEND_IMAGE=" image} {print}' .env.ec2 > "$candidate"
candidate_compose=(docker compose --env-file "$candidate" -f compose.yaml)
"${candidate_compose[@]}" config --quiet
switched=false
cleanup() {
    local status=$?
    trap - EXIT INT TERM
    if [[ $switched == true && -f "$candidate" ]]; then
        echo "Release failed. Restoring $previous_image." >&2
        if ! BACKEND_IMAGE="$previous_image" "${compose[@]}" up -d --no-deps --no-build --pull never --wait --wait-timeout 180 backend; then
            echo 'Automatic rollback failed. Inspect backend on EC2.' >&2
        fi
    fi
    rm -f -- "$candidate"
    exit "$status"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
switched=true
"${candidate_compose[@]}" up -d --no-deps --no-build --pull never --wait --wait-timeout 180 backend
curl -fsS --max-time 15 https://restaurant.thanhtrung.click/ws/info >/dev/null
cp -p .env.ec2 .runtime/env-before-release
mv -f -- "$candidate" .env.ec2
echo "Deployed $image"
echo "Previous image: $previous_image"
