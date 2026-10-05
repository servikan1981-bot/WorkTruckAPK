# Our Chat v3 private server

This directory contains the server side for the private two-device messenger.

## Components

- **ntfy**: relay for encrypted messages and encrypted WebRTC signaling.
- **coturn**: dedicated TURN relay for audio/video when a direct peer-to-peer route cannot be established.
- **nginx + Let's Encrypt**: HTTPS/WSS endpoint for the app.

The Android client encrypts message/signaling payloads before the relay receives them. WebRTC media remains DTLS-SRTP encrypted. The relay and TURN server still see network metadata such as IP addresses and connection times.

## DigitalOcean deployment

Use a small Ubuntu Droplet with a public IPv4 address. Copy `install-digitalocean.sh` to it and run as root. The script automatically uses an `sslip.io` hostname based on the Droplet IP so a separate domain is not required for the first deployment.

The script prints four values:
- Relay URL
- TURN URL
- TURN username
- TURN password

Enter the same values on both phones in **Our Chat v3 → Own server**.

For a permanent deployment, replace the sslip.io hostname with a domain you control and reissue the Let's Encrypt certificate.
