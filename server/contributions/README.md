# Contributions server

A write-only [Cloudflare Worker](https://developers.cloudflare.com/workers/) that receives swipe contributions (#34)
from TitanKeys and keeps them, still sealed, in an R2 bucket. The privacy policy (`site/privacy/index.html`) describes
it to users, so change both together.

- `POST /v1/batch`: one sealed batch (at most 1 MB) with the header `X-Contributor`, the SHA-256 of the contributor ID
  in hex. It is stored as `batches/<that hash>/<date>-<random>.tkc`. Answers 204.
- `POST /v1/delete`: `{"contributor": "<the ID itself>"}`. Deletes every batch under the hash of that ID. Only the
  phone knows the ID, so the stored names cannot be used to delete anything. Answers 200 with the count.
- Anything else answers 404. There is no way to read or list batches through the Worker.

The Worker cannot open a batch. The phone seals each batch with the project's X25519 public key
(`android/kotlin/com/atlas/connected/Seal.kt`); only the owner's private key, kept offline, opens it with
`tools/contributions/open.ts`. The Worker logs nothing and keeps no internet addresses.

Tests: `bun test tools/checks/connected.test.ts` (with a stand-in bucket).

## Deploy (needs the owner's Cloudflare login)

From this folder, on the build machine:

```bash
npx wrangler login
npx wrangler r2 bucket create titankeys-contributions
# The policy promises deletion after at most 2 years (730 days).
npx wrangler r2 bucket lifecycle add titankeys-contributions expire-after-2-years batches/ --expire-days 730
npx wrangler deploy
```

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
