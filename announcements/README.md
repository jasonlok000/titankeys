# Announcements

The news that TitanKeys shows (#42): a new version, the move to Google Play, or other important news. The app downloads
`site/feed.json` from `https://jasonlok000.github.io/titankeys/feed.json` at most once a day, checks its Ed25519
signature, and shows each new entry once as a silent notification, only while no text field is active. The News page
in Settings shows the same entries.

## Send news

1. Add an entry to `entries.json`. Keep old entries only while they still matter.
2. Sign it on the build machine. The private key never goes into git:

   ```bash
   bun tools/announcements/sign.ts ~/build/titankeys/signing/announcements-ed25519.pem
   ```

3. Commit `entries.json` and `site/feed.json`, then publish the site: `bash tools/site/publish.sh`.

Phones see it the next time they check, within a day. Test first with `"builds": ["debug"]`: only TitanKeys Debug shows
it. On the phone, `adb shell cmd jobscheduler run -f com.titankeys.debug 42001` makes the debug app check at once.

## An entry

```json
{
  "id": "v2-2-0",
  "title": "TitanKeys 2.2 is out",
  "text": "It swipes better. Get it from GitHub.",
  "action": { "label": "Get it", "url": "https://github.com/jasonlok000/titankeys/releases/latest" },
  "from": "2027-01-10T00:00:00Z",
  "until": "2027-02-10T00:00:00Z",
  "minVersion": "2.1.0",
  "maxVersion": "2.1.99",
  "builds": ["github"],
  "notify": true
}
```

- `id`: lower case letters, digits, dots and dashes. It never changes: the app remembers it for Snooze and Don't show
  again. A new message needs a new id.
- `title` (up to 80 characters) and `text` (up to 600).
- `action` (optional): a button with a `label` (up to 24 characters) and either an https `url` or an app `page` such as
  `/settings/contribute`.
- `from` and `until` (optional): UTC times. It shows from `from` and stops at `until`.
- `minVersion` and `maxVersion` (optional): the app versions it is for, both ends included.
- `builds`: some of `github`, `play` and `debug`.
- `notify` (optional, true at first): false shows it only on the News page.

The app refuses the whole feed if one entry breaks these rules, and so does the signing tool. It also refuses a feed
older than one that it already has, so an old signed feed cannot come back.
