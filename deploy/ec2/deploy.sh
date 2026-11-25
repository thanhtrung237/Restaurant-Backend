#!/usr/bin/env bash
set -Eeuo pipefail
umask 077
cd -- "$(dirname -- "${BASH_SOURCE[0]}")"
target=${1:-all}
skip_pull=${2:-}
[[ -z "$skip_pull" || "$skip_pull" == --skip-pull ]] || {
  echo 'Optional second argument: --skip-pull (for images already loaded on the server).' >&2; exit 2;
}
case "$target" in
  all) services=(backend frontend) ;;
  backend|frontend) services=("$target") ;;
  *) echo 'Usage: bash deploy.sh [all|backend|frontend] [--skip-pull]' >&2; exit 2 ;;
esac
[[ -f .env.ec2 ]] || { echo 'Create .env.ec2 from .env.example first.' >&2; exit 1; }
if awk '/^[[:space:]]*#/ {next} /REPLACE_WITH_|your-domain[.]com/ {found=1} END {exit !found}' .env.ec2; then
  echo 'Replace the example image/origin placeholders in .env.ec2 first.' >&2
  exit 1
fi
mkdir -p .runtime
exec 9>.runtime/deploy.lock
flock -n 9 || { echo 'Another deployment is running.' >&2; exit 1; }
compose=(docker compose --env-file .env.ec2 -f compose.yaml)
"${compose[@]}" config --quiet
mysql_id=$("${compose[@]}" ps --quiet mysql)
[[ -n "$mysql_id" ]] || { echo 'Start MySQL and import the schema/data first. See the EC2 section in README.' >&2; exit 1; }
[[ $(docker inspect --format '{{.State.Health.Status}}' "$mysql_id") == healthy ]] || {
  echo 'MySQL must be healthy before deploying applications.' >&2; exit 1;
}
if [[ "$skip_pull" != --skip-pull ]]; then
  "${compose[@]}" pull "${services[@]}"
fi
# Do not recreate or upgrade MySQL as part of an application deployment.
"${compose[@]}" up -d --no-deps --no-build --pull never --wait --wait-timeout 300 "${services[@]}"
"${compose[@]}" ps
echo 'Deployment healthy. If a release must be reverted, restore its previous SHA image tag in .env.ec2 and rerun this script.'
