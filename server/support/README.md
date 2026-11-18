# Support leaderboard

This Cloudflare Worker publishes the top ten verified, opted-in TitanKeys supporters. The leaderboard shows cups and any name or message the supporter chose to share.

## App API

Send JSON over HTTPS. The app only reads the board while the support page is open and submits receipts after the supporter chooses to share. There is no background app upload.

| Method and path | Request | Result |
| --- | --- | --- |
| `GET /v1/leaderboard` | No body | `{"entries":[{"id":"…","name":null,"message":"Cheers","cups":1.4}]}` |
| `POST /v1/support` | See below | `{"cups":1.4}` |
| `POST /v1/remove` | `{"accountKey":"…"}` | `204` |
| `POST /v1/admin/delete` | `{"id":"…"}` with the owner's bearer token | `204` |

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

Send one to ten receipts per request. Use `tip` for the one-time products and `subscription` for monthly products. The name and message are optional and each accepts up to 80 Unicode code points. Anonymous clears the name and lets the supporter still choose to share a message. The renderer should treat both fields as plain text.

Malformed inputs return `400`. Pending purchases or refunds return `409`, receipts Play cannot verify return `422`, and transient failures return `503`. Failures have an `error` code and contain no purchase or provider details. When Play explicitly reports that a subscription expired too long ago to query, its old proof earns no new credit and can be skipped in a batch that has a verified receipt. A batch containing only such proofs returns `422 purchase_expired`. All other failures reject the batch before writing.

## How cups are calculated

The Worker checks Google Play's [one-time purchase](https://developers.google.com/android-publisher/api-ref/rest/v3/purchases.products) or [subscription](https://developers.google.com/android-publisher/api-ref/rest/v3/purchases.subscriptionsv2) and the matching [order](https://developers.google.com/android-publisher/api-ref/rest/v3/orders). Test purchases, pending charges and unpaid orders earn no credit.

The USD base price comes from the approved product list. Set the two sip prices to the USD base in Play Console. Multiply the $1 custom unit by Google's verified purchase quantity. Only the custom unit can have quantity above one. The fallback products cost $3, $5, $10, $20 and $50. The app never supplies the price, quantity or order ID to the Worker.

Take the paid USD amount before tax and cap it at that item's nominal value. For other currencies, start from the Play USD base and prorate for confirmed refunds in the purchase currency. This uses Play's own regional pricing and avoids a separate exchange-rate service. Add retained cents across the account, then divide by 500 and round to one decimal for the response. The database sorts by the unrounded total.

Retrying the same order cannot add it again. A subscription token can be reused across renewals. Each verified renewal earns credit for its own latest successful order ID. Google returns that latest order on receipt submission, so a skipped renewal that was never submitted cannot be reconstructed from a later receipt alone. The daily reconciliation checks Google's [Voided Purchases API](https://developers.google.com/android-publisher/api-ref/rest/v3/purchases.voidedpurchases/list) and lowers known balances for full voids and confirmed partial refunds. A refund made without revocation might not appear in that feed and is picked up if the corresponding receipt is submitted again.

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
npx wrangler deploy
```

Generate the admin token in the owner's password manager and paste it when Wrangler prompts. Use integer USD cents for the sip amounts below $1. Once the app is configured with the deployed HTTPS address, the owner can delete a listing by copying its public `id` from the leaderboard and running:

```bash
curl --fail-with-body -X POST "$SUPPORT_URL/v1/admin/delete" \
  -H "Authorization: Bearer $SUPPORT_ADMIN_TOKEN" \
  -H "Content-Type: application/json" -d '{"id":"<leaderboard id>"}'
```
