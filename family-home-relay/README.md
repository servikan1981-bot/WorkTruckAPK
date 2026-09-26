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

The service deliberately listens only on loopback. The Android app requires a
public **HTTPS** hostname. Configure TLS via a reverse proxy only after
checking whether the home connection has a public IP and router port forwarding.
If behind CGNAT, choose a stable tunnel with a documented service limit. Do not
replace the currently configured Cloudflare URL on phones before the public
`/health`, message publish, long poll, and file round-trip pass from mobile data.

The application already offers “Сервер сообщений” to change the relay URL on
each phone. Existing encrypted local chat history remains on each phone.
Previously uploaded attachments retain their old Cloudflare URL; the client
restricts downloads to the currently selected host, so old attachments require
a separate migration strategy before switching. This relay does not replace
the WebRTC STUN/TURN infrastructure or the APK update feed.

Run the existing protocol checks from repository root with:

```bash
RELAY_TEST_URL=http://127.0.0.1:8787 node --test family-relay/test/protocol-live.mjs
```
