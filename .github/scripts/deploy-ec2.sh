#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

[[ $EC2_HOST =~ ^[a-zA-Z0-9][a-zA-Z0-9.-]*$ ]]
[[ $RELEASE_IMAGE =~ ^docker[.]io/[a-z0-9][a-z0-9_-]*/restaurant-backend@sha256:[a-f0-9]{64}$ ]]
[[ -n $EC2_SSH_KEY && -n $EC2_KNOWN_HOSTS ]]

ssh_dir=$(mktemp -d)
trap 'rm -f -- "$ssh_dir/key" "$ssh_dir/known_hosts"; rmdir -- "$ssh_dir"' EXIT
printf '%s\n' "$EC2_SSH_KEY" > "$ssh_dir/key"
printf '%s\n' "$EC2_KNOWN_HOSTS" > "$ssh_dir/known_hosts"
unset EC2_SSH_KEY EC2_KNOWN_HOSTS

ssh -i "$ssh_dir/key" -o BatchMode=yes -o IdentitiesOnly=yes \
    -o ConnectTimeout=15 -o ServerAliveInterval=15 -o ServerAliveCountMax=4 \
    -o StrictHostKeyChecking=yes -o UserKnownHostsFile="$ssh_dir/known_hosts" \
    "ubuntu@$EC2_HOST" "bash /home/ubuntu/restaurant-ec2/deploy-release.sh '$RELEASE_IMAGE'"
