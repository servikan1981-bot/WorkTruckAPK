# OurFamily home relay (Windows)

This is a local replacement for the Cloudflare Worker messaging relay. It uses
Python's standard library and SQLite and has no provider request or storage
quota. Capacity depends on the host computer, disk, and Internet connection.
Only opaque encrypted application payloads are saved. Back up the `data` folder.

## Local preparation

1. Install Python 3.11 or newer on the always-on Windows computer.
2. Copy this folder to `C:\OurFamily\relay` and run `start-windows.ps1`.
3. Check `http://127.0.0.1:8787/health` on that computer. It should return
   `{"ok":true,"protocol":"ourfamily-relay-v1"}`.
4. Keep the machine awake, give the process an automatic restart, and make
   regular backups of `data` (SQLite database plus encrypted attachment files).

The Windows launcher listens on the local network so that KeenDNS can reach it.
Allow Python through Windows Firewall on the **private network only**. Restrict
port 8787 to the Keenetic's LAN address when setting a custom firewall rule.
The Android app requires a public **HTTPS** hostname. With a private WAN IP,
use KeenDNS Cloud access and a separate fourth-level web-app domain. In that
rule choose the registered Windows computer, HTTP, TCP port 8787; the router
provides the public HTTPS certificate. Do not
replace the currently configured Cloudflare URL on phones before the public
`/health`, message publish, long poll, and file round-trip pass from mobile data.

The application already offers “Сервер сообщений” to change the relay URL on
each phone. Existing encrypted local chat history remains on each phone.
Previously uploaded attachments retain their old Cloudflare URL; the client
restricts downloads to the currently selected host, so old attachments require
a separate migration strategy before switching. This relay does not replace
the WebRTC STUN/TURN infrastructure or the APK update feed.

## Temporary Railway Free hosting

Deploy this directory as the Railway service root (`family-home-relay`). The
included Dockerfile uses Python's standard library; Railway supplies `PORT`
and the server binds to all interfaces automatically. Create a Railway volume
with mount path `/data` **before** accepting any real messages. The container
stores its SQLite database and uploaded encrypted files there. Do not set
`FAMILY_DATA` to an ephemeral path. Generate a public Railway domain and check
`https://YOUR_DOMAIN/health` before changing any phones. Keep one service
replica because the SQLite database and long-poll notifications are local to
one process.

The Free plan has usage credits, not unlimited resources; monitor usage and
save a backup of `/data` regularly. The existing app setting “Сервер
сообщений” accepts the new HTTPS base URL on each family phone. Test the
protocol from outside Railway before switching, especially attachment upload,
incoming calls, and old attachment migration. Railway does not provide a TURN
relay for WebRTC in this setup.

Run the existing protocol checks from repository root with:

```bash
RELAY_TEST_URL=http://127.0.0.1:8787 node --test family-relay/test/protocol-live.mjs
```
