# Our Family free relay

Cloudflare Workers Free and SQLite-backed Durable Objects. The relay accepts the Android application's native ntfy-compatible JSON publish, recent message reads, and encrypted file uploads. There is no server-side access to family chat plaintext or the family code.

## Deploy

1. Log into a Cloudflare account with Workers Free enabled.
2. In this directory run `npx wrangler deploy` and record the resulting HTTPS `workers.dev` URL.
3. On **each phone**, open “Сервер сообщений”, enter that same URL, and press “Проверить и сохранить”.

Encrypted attachments expire after seven days. Durable Objects Free includes 5 GB total stored data; Workers Free is limited to 100,000 requests/day, so monitor dashboard usage before adding more devices. Do not add a credit card or upgrade the plan for this configuration.

The update feed remains on `family-stable-6` and is independent of this relay. Switching the relay does not erase encrypted local chat history, although messages delivered only to the previous relay will not follow to the new relay.
