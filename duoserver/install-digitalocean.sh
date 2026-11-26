#!/usr/bin/env bash
set -euo pipefail

if [[ "${EUID}" -ne 0 ]]; then
  echo "Run as root"; exit 1
fi

PUBLIC_IP="${PUBLIC_IP:-$(curl -fsS https://api.ipify.org)}"
DOMAIN="${DOMAIN:-${PUBLIC_IP//./-}.sslip.io}"
TURN_USER="${TURN_USER:-ourchat}"
TURN_PASS="${TURN_PASS:-$(openssl rand -hex 24)}"

apt-get update
DEBIAN_FRONTEND=noninteractive apt-get install -y docker.io docker-compose-plugin nginx certbot python3-certbot-nginx ufw openssl curl
systemctl enable --now docker nginx

mkdir -p /opt/ourchat
cd /opt/ourchat

cat > docker-compose.yml <<'YAML'
services:
  ntfy:
    image: binwiederhier/ntfy:latest
    command: ["serve", "--cache-file=/var/cache/ntfy/cache.db"]
    restart: unless-stopped
    ports:
      - "127.0.0.1:2586:80"
    volumes:
      - ./ntfy-cache:/var/cache/ntfy
  coturn:
    image: coturn/coturn:latest
    restart: unless-stopped
    network_mode: host
    volumes:
      - ./turnserver.conf:/etc/coturn/turnserver.conf:ro
      - /etc/letsencrypt:/etc/letsencrypt:ro
    command: ["-c", "/etc/coturn/turnserver.conf"]
YAML

cat > /etc/nginx/sites-available/ourchat <<NGINX
server {
  listen 80;
  server_name ${DOMAIN};
  location / {
    proxy_pass http://127.0.0.1:2586;
    proxy_http_version 1.1;
    proxy_set_header Host \$host;
    proxy_set_header Upgrade \$http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_read_timeout 1d;
  }
}
NGINX
ln -sf /etc/nginx/sites-available/ourchat /etc/nginx/sites-enabled/ourchat
rm -f /etc/nginx/sites-enabled/default
nginx -t
systemctl reload nginx

ufw allow OpenSSH
ufw allow 80/tcp
ufw allow 443/tcp
ufw allow 3478/tcp
ufw allow 3478/udp
ufw allow 5349/tcp
ufw allow 49160:49200/udp
ufw --force enable

certbot --nginx -n --agree-tos --register-unsafely-without-email -d "${DOMAIN}"

cat > /opt/ourchat/turnserver.conf <<TURN
listening-port=3478
tls-listening-port=5349
fingerprint
lt-cred-mech
realm=${DOMAIN}
user=${TURN_USER}:${TURN_PASS}
external-ip=${PUBLIC_IP}
min-port=49160
max-port=49200
stale-nonce=600
no-cli
no-multicast-peers
cert=/etc/letsencrypt/live/${DOMAIN}/fullchain.pem
pkey=/etc/letsencrypt/live/${DOMAIN}/privkey.pem
TURN

docker compose up -d

cat > /root/ourchat-v3-connection.txt <<INFO
Relay: https://${DOMAIN}
TURN URL: turns:${DOMAIN}:5349
TURN user: ${TURN_USER}
TURN password: ${TURN_PASS}
INFO
chmod 600 /root/ourchat-v3-connection.txt

echo
echo "Our Chat v3 server is ready."
cat /root/ourchat-v3-connection.txt
