# In-app products: the Support page's coffees

For #36. Written on 2026-11-21 from the 3.0.0 code:
- the app: `android/kotlin/com/titankeys/support/SupportRules.kt` (`SupportProduct`), `SupportBilling.kt` (Play Billing
  Library 9.1.0, `android/build.gradle.kts`);
- the page: `android/assets/support/index.html`, `app/support.tsx`, `lib/support.ts`;
- the server: `server/support/src/worker.js`, `server/support/src/play.js` and `server/support/README.md`.

Checked against Google's help pages for [in-app products](https://support.google.com/googleplay/android-developer/answer/1153481),
[subscriptions](https://support.google.com/googleplay/android-developer/answer/140504),
[getting ready for billing](https://developer.android.com/google/play/billing/getting-ready),
the [Payments policy](https://support.google.com/googleplay/android-developer/answer/9858738) and the
[Subscriptions policy](https://support.google.com/googleplay/android-developer/answer/9900533). This is not legal advice.

## The products

A cup is $5. Each kind has three tiers, and one-time also has the $1 unit for Custom (owner, 2026-11-19). The product IDs
must be exactly these: the app, the page and the Worker all name them.

| Product ID | Type | Page label | USD price | Notes |
|---|---|---|---|---|
| `support_tip_sip` | One-time, consumable | A sip | Play's lowest USD price | The app counts it as 50 cents outside USD (`SupportProduct.TipSip`); the page's sample shows $0.50 |
| `support_tip_small` | One-time, consumable | A big sip | $1.00 | |
| `support_tip_full` | One-time, consumable | Half a coffee | $2.50 | |
| `support_tip_unit` | One-time, consumable, **multi-quantity** | Custom | $1.00 each | Custom's amounts are $3, $5, $10, $20 and $50 (`customPrices`). The quantity is set in Play's purchase screen, not by the app. |
| `support_monthly_sip` | Subscription, auto-renewing monthly | A sip | Play's lowest USD price | Counted as 50 cents outside USD |
| `support_monthly_small` | Subscription, auto-renewing monthly | A big sip | $1.00 | |
| `support_monthly_full` | Subscription, auto-renewing monthly | Half a coffee | $2.50 | |

**Consumable** is not a setting in Play Console. The app consumes each one-time purchase once its credit is on the phone
(`consumeAsync` in `SupportBilling.finish`), so it can be bought again. It acknowledges each subscription instead.

**What the app looks for:**
- One-time products: the plain buy option, with no offer, rental or pre-order.
- Subscriptions: a base plan with no offer, whose last price phase is monthly (`P1M`) and renews with no end. A base plan with a
  free trial or an intro price is skipped, so don't add offers.
- The obfuscated account ID on each purchase: the SHA-256 of the app's random account key. The Worker checks it.

## Before Play Console lets you make them

1. Link a **payments profile** to the developer account (Google: needed to sell in-app purchases). Play then shows your
   address in public, as `summary.md` warns.
2. Upload a build that has the Billing Library to any track. **Internal testing** is enough, and it's the only place the
   purchases can be tried before the closed test (`docs/todo-3.0.md`). Purchases there have spend limits.
3. Add yourself as a **license tester** (Settings > License testing), so test buys aren't charged. The Worker gives test
   purchases no credit, by design.

## Play Console steps

### One-time products (4)

Go to **Monetize with Play > Products > In-app products** and click **Create product**. (Newer consoles may call the page
"One-time products", with a "Buy" purchase option under each product. Use the default buy option with no offers.)

For each one-time product above:
1. **Product ID:** exactly as in the table. It can't be changed or reused later.
2. **Title:** short, 25 characters or fewer shows best. For example "A sip", "A big sip", "Half a coffee", "A dollar toward a coffee".
3. **Description:** up to 200 characters. Say what it is: a tip to support TitanKeys, with nothing unlocked.
4. **Price:** set the USD price from the table and let Play convert it to local prices. For the sip, pick the lowest USD price
   Play offers, and write that figure down (below).
5. For `support_tip_unit` only, tick **Allow multi-quantity checkout for this product**. Google: multi-quantity needs Billing
   Library 4.0 or later (TitanKeys has 9.1.0), isn't offered everywhere, and in most countries a basket must stay under
   about US$100 before tax. Custom's largest amount, $50, is under that.
6. **Save**, then **Activate**.

### Subscriptions (3)

Go to **Monetize with Play > Products > Subscriptions** and click **Create subscription**. For each monthly product:
1. **Product ID:** exactly as in the table (up to 40 characters). **Name:** for example "A sip every month".
2. Click **Create**, then **Edit subscription details**. Under Benefits, **+ Add benefit**: up to 4 lines of 40 characters,
   with no prices or trials. See the policy note below: say what the supporter gets each month. **Save changes**.
3. Back on the Subscriptions page, open it and click **Add base plan**.
4. **Base plan ID:** for example `monthly` (lowercase letters, numbers and hyphens). The app doesn't read it.
5. Type **Auto-renewing**, billing period **Monthly**.
6. **Grace period** and **Account hold:** leave Google's defaults. The Worker credits only paid months, so a month in grace or
   on hold earns nothing until it's paid.
7. **Price and availability:** **Manage country/region availability**, pick the countries, then **Update prices** with the USD
   price from the table (tax-exclusive) and let Play convert it.
8. **Save**, then **Activate**. Add **no offers**: no free trial, no intro price.

## What the support Worker needs from Play

The Worker (`server/support/`) checks each receipt with the Google Play Developer API before it counts on the board. It reads
one-time purchases (`purchases.products`), subscriptions (`purchases.subscriptionsv2`), orders (`orders`), and once a day the
voided purchases (`purchases.voidedpurchases`), with the OAuth scope `https://www.googleapis.com/auth/androidpublisher`.

**Owner steps** (`server/support/README.md`, "Set up and deploy"):
1. In a Google Cloud project, enable the **Google Play Android Developer API** and create a **service account**. Make a JSON
   key for it. Keep the file outside the repo, in the password manager.
2. In Play Console, **Users and permissions**: invite the service account's email, give it this app, and give it permission
   to view app information, **view financial data** (orders and voided purchases need it, as Google's billing guide says) and
   manage orders. **Not verified:** the exact names of these permissions in the 2026 console. The Worker only reads; it never
   refunds or changes anything.
3. Create the products above in Play Console.
4. Deploy the Worker with these settings:

| Name | Kind | What it holds |
|---|---|---|
| `APP_PACKAGE` | `[vars]` in `wrangler.toml` | `com.titankeys` (already set) |
| `PLAY_SERVICE_ACCOUNT` | secret | The service account's whole JSON key file. The Worker reads its `client_email` and `private_key`. |
| `TIP_SIP_CENTS` | secret | The sip's USD price in cents, 1 to 99, exactly as set in Play Console |
| `MONTHLY_SIP_CENTS` | secret | The monthly sip's USD price in cents, 1 to 99 |
| `ADMIN_TOKEN` | secret | A random token from the password manager, for deleting entries and reading reports |
| `RATE_SALT` | secret | Random text of at least 16 characters, for the rate counters |
| `DB` | D1 binding | The `titankeys-support` database; put its ID in `wrangler.toml` |

The board answers with `X-TitanKeys-Posting: ready` once the package, a readable service-account key, the admin token and
both sip prices are set. It doesn't call Play to say so.

Real-time developer notifications (Pub/Sub) are **not needed**: the Worker checks receipts when the app posts them, and reads
the voided purchases once a day (cron `17 2 * * *`).

**Keep the sip price in step.** If the sip isn't 50 cents, set both secrets to the real figure. The app's own total for a
non-USD buyer still counts the sip as 50 cents (`SupportProduct`), so the total on the phone could then differ slightly from
the board's order. Change `SupportProduct` too if the sip ends up at another price.

## Google Play's rules for tips

- **Play Billing is required.** A tip to the developer buys nothing outside the app, so it falls under the Payments policy's
  in-app purchases and must use Google Play's billing system, as TitanKeys does. The policy says Play Billing must not be used
  for tax-exempt donations; TitanKeys' coffees are not donations to a charity, so that rule doesn't apply. Don't call them
  donations in the listing or the app.
- **Say what it is.** The product descriptions and the page say it's support and unlocks nothing, so no one expects a feature.
- **Subscriptions must give something every month.** Google: "Subscriptions must provide sustained or recurring value to users
  throughout the life of the subscription." Products without that "must use an in-app product". **Risk, for the owner to
  decide:** a monthly tip that only raises the supporter's place on the board may be judged a one-time benefit. If review
  rejects the monthly products, keep the three one-time tiers and drop or rethink monthly. The benefits text in step 2 above
  should name the monthly value plainly, for example "Your place on the board rises every month".
- **Subscription disclosure.** The policy wants the price, the billing period, the automatic renewal and how to cancel shown
  clearly before buying, and an easy way to cancel, such as a link to Google Play's subscription center. The page shows
  the price and "Every month · cancel any time in Play" (`monthlyNote`), but has no link to the subscription center
  (`https://play.google.com/store/account/subscriptions`). **Check** with the owner whether to add one before the monthly
  products go live.
- **Store listing:** Play shows "In-app purchases" on the listing once products are active. The Price is still Free.
- **Refunds:** Google handles them. The Worker lowers a refunded supporter's total from the voided purchases.

## Not verified

- Play's lowest USD price for an in-app product and for a subscription today. Read it in Play Console when setting the sips.
- Whether the 2026 console shows "In-app products" or "One-time products" with purchase options. The steps above cover both.
- The exact Play Console permission names for the service account.
