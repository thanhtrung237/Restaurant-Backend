#!/usr/bin/env bash
set -Eeuo pipefail
umask 077
cd -- "$(dirname -- "${BASH_SOURCE[0]}")"
mkdir -p backups
destination="backups/restaurant-$(date -u +%Y%m%dT%H%M%SZ)-$$.sql.gz"
temporary="$destination.tmp"
trap 'rm -f -- "$temporary"' EXIT
docker compose --env-file .env.ec2 -f compose.yaml exec -T mysql sh -ec '
  export MYSQL_PWD="$MYSQL_PASSWORD"
  exec mysqldump -u"$MYSQL_USER" --single-transaction --quick --no-tablespaces \
    --set-gtid-purged=OFF --column-statistics=0 --complete-insert "$MYSQL_DATABASE"
' | gzip > "$temporary"
mv -- "$temporary" "$destination"
echo "Backup saved: $destination. Copy it outside EC2 for disaster recovery."
