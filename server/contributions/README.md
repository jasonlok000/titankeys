# Contributions server

A [Cloudflare Worker](https://developers.cloudflare.com/workers/) that receives swipe contributions (#34) from
TitanKeys and keeps them, still sealed, in SQLite-backed [Durable Objects](https://developers.cloudflare.com/durable-objects/).
It runs on the **Workers Free plan, on an account with no payment method**, so it can never cost anything. The privacy
policy (`site/privacy/index.html`) describes it to users, so change both together.

## Routes

For the app, write-only:

- `POST /v1/batch`: one sealed batch (at most 320 KB) with the header `X-Contributor`, the SHA-256 of the contributor
  ID in hex. Answers 204.
- `POST /v1/delete`: `{"contributor": "<the ID itself>"}`. Deletes every batch stored under the hash of that ID. Only
  the phone knows the ID, so the stored hashes cannot be used to delete anything. Answers 200 with the count.

For the owner only, with `Authorization: Bearer <EXPORT_TOKEN>` (without it, these answer 404, as if absent):

- `GET /v1/export?shard=N&after=ID`: up to 500 batches of shard N (0 to 63) as `{id, month, size}`, oldest first,
  and `more` if more follow.
- `GET /v1/export/batch?shard=N&id=ID`: one sealed batch.
- `POST /v1/export/confirm` with `{"shard": N, "through": ID}`: deletes that shard's batches up to that ID, after they
  were exported safely.

Everything else answers 404. The batches stay sealed on the way out: only the owner's private key, kept offline,
opens them (`tools/contributions/open.ts`). A leaked export token shows only sealed files. The Worker logs nothing.

## Storage

- 64 `Shard` objects, chosen by the first two digits of the folder hash. Each holds a SQLite table of batches (folder
  hash, upload time, month, size, the sealed bytes) and its own byte total. Each stops taking batches at 900 MB, below
  the Free plan's 1 GB per object.
- One `Cap` object holds the caps: 2000 uploads a day for everyone together, and 4 GB stored in all (the Free plan
  allows 5 GB for the account). It stores a date and byte counts per month, nothing about any phone. Only the last 24
  months count, since batches are deleted after 2 years. Deletes take bytes off the month each batch was uploaded in.
- Expiry: each shard has a daily alarm that deletes batches older than 2 years (730 days), as the policy promises.

## Limits against abuse

Uploads need no account, so anyone who finds the address can send batches. So:

- Per client: 20 uploads and 5 deletes a minute. `Cap` counts them in memory only, keyed by a SHA-256 of `RATE_SALT`
  and the connecting address. The keys are never written to storage, and they go when the minute ends. (Cloudflare's
  rate-limiting binding is not used: its availability on the Free plan is not documented.)
- The daily and storage caps above. The app sends at most 256 KB of examples before gzip, and no example over 128 KB.
- Limited requests get 429 (per client) or 503 (a cap, or storage full). The app keeps its queue and tries again at its
  next run, 12 hours later on Wi-Fi. Deletes retry with growing waits until they succeed.
- Without `RATE_SALT`, the Worker answers 503 to everything.

## Costs

None, by design: the account is on the Workers Free plan and has **no payment method**, so Cloudflare cannot charge
it. Durable Objects with SQLite storage are part of the Free plan. When a free limit is reached, requests fail instead
of being billed:

| Free limit | When it is reached | What the app does |
|---|---|---|
| 100,000 Worker requests a day | Requests fail until midnight UTC | Keeps its queue; tries at its next run |
| Durable Object requests, rows read and written a day | Requests fail until the next day | The same |
| 5 GB of Durable Object storage (our cap: 4 GB) | Uploads get 503 | The same; deletes still work |
| 1 GB per object (our cap: 900 MB a shard) | That shard's uploads get 503 | The same |

Real use is far below these: an upload is about 3 rows written, and a phone sends a few batches a day at most.

## For training (workstream B): check every batch

Uploads are not signed in, by design. Before any batch is used:

- It must open with the private key (`tools/contributions/open.ts`); drop any that do not.
- Check the fields and ranges: `v` is 1; `how` is kept, chosen or corrected; `x`, `y` and `t` have the same length,
  1 to 4096 points, positions within about -0.5 to 1.5, times rising from 0 to at most 10 000 ms; `size` is -1 to 255;
  every row has 18 numbers; `kept` is -1 or a row; `word` is a dictionary word; the versions are real ones.
- Cap what one contributor ID adds, and what one day adds, so that no one source can outweigh the rest.
- Look for outliers: paths that do not fit their word, copies of the same path, and IDs whose examples all look
  alike. Hold out each contributor's data when testing a model, as #34 plans.

Tests: `bun test tools/checks/connected.test.ts` runs the Worker against real SQLite with stand-in objects.

## Deploy (needs the owner's Cloudflare login)

On an account with **no payment method**. From this folder (`server/contributions`), in this order. On Windows, type
`npx.cmd` instead of `npx`, because PowerShell may block `npx.ps1`.

```bash
npx wrangler login
# A random salt for the per-client keys. Paste the output of `openssl rand -hex 32` when asked.
# If Wrangler offers to create the Worker "titankeys-contributions", answer yes.
npx wrangler secret put RATE_SALT
# The owner's export token: a long random value, kept with the private key. `openssl rand -hex 32` again.
npx wrangler secret put EXPORT_TOKEN
npx wrangler deploy
```

There are no storage commands: the Durable Objects are made by the deploy. Secrets are never shown again and never
leave Cloudflare. `npx wrangler telemetry disable` turns off Wrangler's own anonymous telemetry, if wanted.

`wrangler deploy` prints the address, such as `https://titankeys-contributions.<account>.workers.dev`. Put it in
`CONTRIBUTIONS_URL` in `android/kotlin/com/atlas/connected/Connected.kt`: until then the app keeps examples on the
phone and sends nothing. Then check it:

```bash
curl -s -o /dev/null -w "%{http_code}\n" -X POST https://titankeys-contributions.<account>.workers.dev/v1/batch   # 400
curl -s -o /dev/null -w "%{http_code}\n" https://titankeys-contributions.<account>.workers.dev/                   # 404
curl -s -o /dev/null -w "%{http_code}\n" https://titankeys-contributions.<account>.workers.dev/v1/export?shard=0  # 404
```

## Delete on request (owner)

A user who removed the app can send their contributor ID (shown in the app's Settings). Delete everything for it:

```bash
curl -s -X POST https://titankeys-contributions.<account>.workers.dev/v1/delete -H "Content-Type: application/json" -d '{"contributor":"<the ID>"}'
```

Also delete that contributor's examples from any copy opened for training: the `contributor` field of each batch.

## Export and read (owner only)

On the offline computer that holds `contributions-x25519.pem`, with the export token in a file:

```bash
bun tools/contributions/export.ts https://titankeys-contributions.<account>.workers.dev export-token.txt batches/
bun tools/contributions/open.ts contributions-x25519.pem batches/*.tkc > examples.jsonl
# Once the export is safely kept, free the server's space:
bun tools/contributions/export.ts https://titankeys-contributions.<account>.workers.dev export-token.txt batches/ --confirm
```

The opened copy follows the same rule as the server: kept until used for training, and at most 2 years after the
batch arrived (its file name starts with that month).

Each line of `examples.jsonl` is one batch: `{"v":1,"contributor":"…","examples":[…]}`. Each example has `how` (kept,
chosen or corrected), `word`, `x`, `y` and `t` (ms from the start) for the path, `size` (sensor steps,
`round(getSize() * 255)`, -1 if unknown), `rows` (the ranker's 18 numbers per candidate, in `SwipeRanker.FEATURES`
order, null for NaN), `kept` (the row of the kept word, or -1), and `app`, `models`, `phone` and `build`.
