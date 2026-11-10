# Contributions server

A write-only [Cloudflare Worker](https://developers.cloudflare.com/workers/) that receives swipe contributions (#34)
from TitanKeys and keeps them, still sealed, in an R2 bucket. The privacy policy (`site/privacy/index.html`) describes
it to users, so change both together.

- `POST /v1/batch`: one sealed batch (at most 1 MB) with the header `X-Contributor`, the SHA-256 of the contributor ID
  in hex. It is stored as `batches/<that hash>/<date>-<random>.tkc`. Answers 204.
- `POST /v1/delete`: `{"contributor": "<the ID itself>"}`. Deletes every batch under the hash of that ID. Only the
  phone knows the ID, so the stored names cannot be used to delete anything. Answers 200 with the count.
- Anything else answers 404. There is no way to read or list batches through the Worker.

Limits against abuse, since uploads need no account:

- Per client: 20 uploads and 5 deletes a minute (Workers Rate Limiting, in `wrangler.toml`; Cloudflare allows only
  10- or 60-second periods). The key is a SHA-256 of `RATE_SALT` and the connecting address, counted in memory by
  Cloudflare, never stored or logged. Set a random salt once: `npx wrangler secret put RATE_SALT`.
- For everyone together: 2000 uploads a day, and 8 GB stored in all (inside R2's free 10 GB), held by one small
  Durable Object (`Cap`) that stores only a date and counts. It counts stored bytes per month and only the last 24
  months, as R2 deletes files after 2 years, so the cap follows the real size without upkeep.
- 320 KB a batch. The app sends at most 256 KB of examples before gzip, and no example over 128 KB.
- Limited requests get 429 (per client) or 503 (daily or storage cap). The app keeps its queue and tries again at its
  next run. Deletes retry with growing waits until they succeed.

## For training (workstream B): check every batch

Uploads are not signed in, by design, so anyone who finds the address can send batches. Before any batch is used:

- It must open with the private key (`tools/contributions/open.ts`); drop any that do not.
- Check the fields and ranges: `v` is 1; `how` is kept, chosen or corrected; `x`, `y` and `t` have the same length,
  1 to 4096 points, positions within about -0.5 to 1.5, times rising from 0 to at most 10 000 ms; `size` is -1 to 255;
  every row has 18 numbers; `kept` is -1 or a row; `word` is a dictionary word; the versions are real ones.
- Cap what one contributor ID adds, and what one day adds, so that no one source can outweigh the rest.
- Look for outliers: paths that do not fit their word, copies of the same path, and IDs whose examples all look
  alike. Hold out each contributor's data when testing a model, as #34 plans.

The Worker cannot open a batch. The phone seals each batch with the project's X25519 public key
(`android/kotlin/com/atlas/connected/Seal.kt`); only the owner's private key, kept offline, opens it with
`tools/contributions/open.ts`. The Worker logs nothing and keeps no internet addresses.

Tests: `bun test tools/checks/connected.test.ts` (with a stand-in bucket).

## Deploy (needs the owner's Cloudflare login)

From this folder (`server/contributions`), in that order. The owner signs in in the browser at step 1. R2 needs a
payment method on the account, even on the free tier; the caps above keep the use inside the free amounts.

```bash
npx wrangler login
npx wrangler r2 bucket create titankeys-contributions
# The policy promises deletion after at most 2 years (730 days).
npx wrangler r2 bucket lifecycle add titankeys-contributions expire-after-2-years batches/ --expire-days 730
# Before the first deploy: a random salt for the rate-limit keys. Paste the output of `openssl rand -hex 32` when
# asked. Wrangler offers to create the Worker if it does not exist yet: answer yes.
npx wrangler secret put RATE_SALT
npx wrangler deploy
```

The salt is never shown again and never leaves Cloudflare. Without it, the Worker answers 503 to every request.

`wrangler deploy` prints the address, such as `https://titankeys-contributions.<account>.workers.dev`. Put it in
`CONTRIBUTIONS_URL` in `android/kotlin/com/atlas/connected/Connected.kt`: until then the app keeps examples on the
phone and sends nothing. Then check it:

```bash
curl -s -o /dev/null -w "%{http_code}\n" -X POST https://titankeys-contributions.<account>.workers.dev/v1/batch   # 400
curl -s -o /dev/null -w "%{http_code}\n" https://titankeys-contributions.<account>.workers.dev/                   # 404
```

Keep the bucket private: no public access and no custom domain on it.

## Delete on request (owner)

A user who removed the app can send their contributor ID (shown in the app's Settings). Delete everything for it:

```bash
curl -s -X POST https://titankeys-contributions.<account>.workers.dev/v1/delete   -H "Content-Type: application/json" -d '{"contributor":"<the ID>"}'
```

Also delete that contributor's examples from any copy opened for training: the `contributor` field of each batch.

## Read the contributions (owner only)

On the offline computer that holds `contributions-x25519.pem`:

```bash
npx wrangler r2 object get titankeys-contributions/batches/<hash>/<name>.tkc --file batch.tkc --remote
bun tools/contributions/open.ts contributions-x25519.pem *.tkc > examples.jsonl
```

Each line is one batch: `{"v":1,"contributor":"…","examples":[…]}`. Each example has `how` (kept, chosen or corrected),
`word`, `x`, `y` and `t` (ms from the start) for the path, `size` (sensor steps, `round(getSize() * 255)`, -1 if
unknown), `rows` (the ranker's 18 numbers per candidate, in `SwipeRanker.FEATURES` order, null for NaN), `kept` (the
row of the kept word, or -1), and `app`, `models`, `phone` and `build`.
