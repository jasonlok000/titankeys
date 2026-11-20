# Support leaderboard

This Cloudflare Worker publishes every verified, opted-in TitanKeys supporter, ranked by total. The board shows each one's place, name and message, never an amount (owner, 2026-11-19).

## App API

Send JSON over HTTPS. The app only reads the board while the support page is open and submits receipts after the supporter chooses to share. There is no background app upload.

| Method and path | Request | Result |
| --- | --- | --- |
| `GET /v1/leaderboard?offset=0&limit=20` | No body; `limit` 1 to 50 | `{"entries":[{"rank":1,"id":"…","name":null,"message":"Cheers"}],"total":42,"nextOffset":20}` |
| `POST /v1/support` | See below | `{"rank":4,"tier":"top10","messageLimit":80}` |
| `POST /v1/rank-preview` | `{"accountKey":"…","addCents":200}` | `{"rank":7,"tier":"top10","messageLimit":80}` |
| `POST /v1/report` | `{"id":"<public id>","reason":"optional, up to 120"}` | `204`, or `404` for an unknown id |
| `POST /v1/remove` | `{"accountKey":"…"}` | `204` |
| `POST /v1/admin/delete` | `{"id":"…"}` with the owner's bearer token | `204` |
| `GET /v1/admin/reports` | The owner's bearer token | `{"reports":[{"id":"…","name":"…","message":"…","reason":"…","created":1763500000000}]}` |

**Places and messages.** The board sorts by each account's retained total, ties by public id. A message shows as much as the place allows: 120 characters for the top 3, 80 for the top 10, 40 for the top 100, and beyond that the name alone. Posting keeps only what the new place allows (a place beyond 100th keeps 40 characters, unshown until it rises), and the board cuts again on every read, so a supporter who drops shows less. The response gives the poster's new `rank`, `tier` (`top3`, `top10`, `top100` or `name`) and `messageLimit`.

**Rank preview.** "This puts you at #N" before paying: the place the account would reach with `addCents` more, counted inside the Worker. Only the place comes back, never anyone's total. Each account may ask 30 times an hour and each address 60, under a salted hash; past that it answers `429`.

**Reports.** Anyone can report an entry, 5 times an hour per address (salted hash). Reports are kept for the owner; nothing is hidden by itself (owner: no filter). The owner reads them with `/v1/admin/reports` and removes an entry with `/v1/admin/delete`.

The leaderboard response includes `X-TitanKeys-Posting: ready` when the Play package, usable service-account key, admin token and both sip prices are configured. An unconfigured Worker still returns the same leaderboard JSON. The header reflects local configuration without contacting Play.

`accountKey` is a random 32-byte value generated and kept by the app, encoded as 64 lowercase hex characters. Before starting the Play purchase, set the Play obfuscated account ID to the lowercase hexadecimal SHA-256 of the **UTF-8 hex string**. The Worker verifies that purchase account ID against the same hash. Losing the local key means the supporter needs the owner to remove their entry.

```json
{
  "accountKey": "64 lowercase hex characters from the device",
  "optIn": true,
  "anonymous": false,
  "name": "Sunny",
  "message": "Cheers",
  "purchases": [
    {
      "kind": "tip",
      "productId": "support_tip_unit",
      "purchaseToken": "the token provided by Play"
    }
  ]
}
```

Send one to ten receipts per request. Use `tip` for the one-time products and `subscription` for monthly products. The name (up to 80 Unicode code points) and the message (up to 120, kept as far as the place allows) are optional. Anonymous clears the name: the supporter keeps their credit but isn't on the board, which lists only names. The renderer should treat both fields as plain text.

Malformed inputs return `400`. Pending purchases or refunds return `409`, receipts Play cannot verify return `422`, and transient failures return `503`. Failures have an `error` code and contain no purchase or provider details. When Play explicitly reports that a subscription expired too long ago to query, its old proof earns no new credit and can be skipped in a batch that has a verified receipt. A batch containing only such proofs returns `422 purchase_expired`. All other failures reject the batch before writing.

## How totals are counted

The Worker checks Google Play's [one-time purchase](https://developers.google.com/android-publisher/api-ref/rest/v3/purchases.products) or [subscription](https://developers.google.com/android-publisher/api-ref/rest/v3/purchases.subscriptionsv2) and the matching [order](https://developers.google.com/android-publisher/api-ref/rest/v3/orders). Test purchases, pending charges and unpaid orders earn no credit.

The USD base price comes from the approved product list (owner, 2026-11-19): a cup is $5 everywhere, and each of one-time and monthly has three tiers, the sip (the lowest price Play allows), $1 and $2.50 ("Half a coffee"), plus the one-time $1 unit for Custom.

| Product | USD base |
| --- | --- |
| `support_tip_sip`, `support_monthly_sip` | Play's lowest price; set `TIP_SIP_CENTS` and `MONTHLY_SIP_CENTS` to it in cents |
| `support_tip_small`, `support_monthly_small` | $1.00 |
| `support_tip_full`, `support_monthly_full` | $2.50 |
| `support_tip_unit` (Custom, multi-quantity) | $1.00 each |

In Play Console, create each product at that USD price and let Play convert it to local prices. Set the two sip prices to the USD base in Play Console. Multiply the $1 custom unit by Google's verified purchase quantity. Only the custom unit can have quantity above one. There is no fixed ladder beside it, and monthly has its three tiers only (owner, 2026-11-19). The app never supplies the price, quantity or order ID to the Worker.

Take the paid USD amount before tax and cap it at that item's nominal value. For other currencies, start from the Play USD base and prorate for confirmed refunds in the purchase currency. This uses Play's own regional pricing and avoids a separate exchange-rate service. Add retained cents across the account. The board sorts by that total and never shows it, in cents or cups.

Retrying the same order cannot add it again. A subscription token can be reused across renewals. Each paid month counts once by its own order: from the latest successful order, the Worker looks up every earlier renewal order (Play names them GPA.X, then GPA.X..0, ..1), so a month counts even if nobody posted during it. The daily reconciliation checks Google's [Voided Purchases API](https://developers.google.com/android-publisher/api-ref/rest/v3/purchases.voidedpurchases/list) and lowers known balances for full voids and confirmed partial refunds. A refund made without revocation might not appear in that feed and is picked up if the corresponding receipt is submitted again.

## Privacy and owner removal

The database holds the Play obfuscated account identifier, hashes of receipt tokens and order IDs, product and cents, and the opted-in text. The Worker stores no raw receipt token, raw order ID, email, address, or caller IP. Google API field masks omit buyer details. Publishing, refunds and removal use SQLite transactions where multiple writes are needed, and replays cannot restore refunded credit. An account removal deletes its entry and its locally held receipt history. Public entry IDs are independent random identifiers. Cloudflare Worker observability is disabled.

## Local checks

Run `bash tests/support-worker/run.sh` from the repository. The tests use an in-memory SQLite database and fake Google responses. For a Worker runtime check, use the separate `tests/support-worker/wrangler.toml` configuration with Wrangler's `--local` flag. That configuration uses a fixture verifier and accepts the token `offline-seven-unit-tip` for the account key made from 64 lowercase `f` characters.

## Set up and deploy (owner)

The app can show an empty board until this Worker is deployed. The owner creates the products in Play Console and enables multi-quantity on `support_tip_unit`. In a Google Cloud project, enable the Google Play Android Developer API and create a service account. Invite it to this app in Play Console and give it access to read purchases, orders and voided purchase financial data.

Run from this folder on the owner account. Put the database ID returned by the create command into `wrangler.toml`. Keep the service account credential file and generated admin token outside the repository.

```bash
export WRANGLER_SEND_METRICS=false
npx wrangler login
npx wrangler d1 create titankeys-support
npx wrangler d1 migrations apply titankeys-support --remote
npx wrangler secret put PLAY_SERVICE_ACCOUNT < /path/to/owner-service-account.json
npx wrangler secret put ADMIN_TOKEN
npx wrangler secret put TIP_SIP_CENTS
npx wrangler secret put MONTHLY_SIP_CENTS
npx wrangler secret put RATE_SALT
npx wrangler deploy
```

`RATE_SALT` is any random text of at least 16 characters, from the password manager. It salts the hashes the rank preview and reports count by; without it, those two answer `503` instead of counting by raw address. `migrations apply` also adds the reports and rate-counter tables (`0002_ranks_reports.sql`); an existing database gets them from the same command.

Generate the admin token in the owner's password manager and paste it when Wrangler prompts. Use integer USD cents for the sip amounts below $1. Once the app is configured with the deployed HTTPS address, the owner can delete a listing by copying its public `id` from the leaderboard and running:

```bash
curl --fail-with-body -X POST "$SUPPORT_URL/v1/admin/delete" \
  -H "Authorization: Bearer $SUPPORT_ADMIN_TOKEN" \
  -H "Content-Type: application/json" -d '{"id":"<leaderboard id>"}'
```

To read the reports people sent, newest first:

```bash
curl --fail-with-body "$SUPPORT_URL/v1/admin/reports" -H "Authorization: Bearer $SUPPORT_ADMIN_TOKEN"
```
