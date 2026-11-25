#!/usr/bin/env bash
set -Eeuo pipefail
if ! command -v docker >/dev/null 2>&1; then
    sudo apt-get -o DPkg::Lock::Timeout=180 update
    sudo apt-get -o DPkg::Lock::Timeout=180 install -y ca-certificates curl
    sudo install -m 0755 -d /etc/apt/keyrings
    sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
    sudo chmod a+r /etc/apt/keyrings/docker.asc
    . /etc/os-release
    printf 'Types: deb\nURIs: https://download.docker.com/linux/ubuntu\nSuites: %s\nComponents: stable\nArchitectures: %s\nSigned-By: /etc/apt/keyrings/docker.asc\n' \
        "${UBUNTU_CODENAME:-$VERSION_CODENAME}" "$(dpkg --print-architecture)" | sudo tee /etc/apt/sources.list.d/docker.sources >/dev/null
    sudo apt-get -o DPkg::Lock::Timeout=180 update
    sudo apt-get -o DPkg::Lock::Timeout=180 install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
fi
sudo systemctl enable --now docker
sudo usermod -aG docker ubuntu
if ! command -v caddy >/dev/null 2>&1; then
    sudo apt-get -o DPkg::Lock::Timeout=180 install -y debian-keyring debian-archive-keyring apt-transport-https curl gnupg
    curl -fsSL https://dl.cloudsmith.io/public/caddy/stable/gpg.key -o /tmp/restaurant-caddy-key.asc
    sudo gpg --batch --yes --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg /tmp/restaurant-caddy-key.asc
    curl -fsSL https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt | sudo tee /etc/apt/sources.list.d/caddy-stable.list >/dev/null
    sudo chmod o+r /usr/share/keyrings/caddy-stable-archive-keyring.gpg /etc/apt/sources.list.d/caddy-stable.list
    sudo apt-get -o DPkg::Lock::Timeout=180 update
    sudo apt-get -o DPkg::Lock::Timeout=180 install -y caddy
fi
mkdir -p /home/ubuntu/restaurant-ec2
chmod 700 /home/ubuntu/restaurant-ec2
sudo docker version --format '{{.Server.Version}}'
caddy version
