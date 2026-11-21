# Why TitanKeys asks for each permission

For #36. One short reason each, for the store listing, the app's own permission prompts, and any Play Console text box. Only
the accessibility service needs its own Play Console form (`accessibility.md`). Use the tone from #54 for the final wording.

## What 3.0.0's merged manifest has

Read on 2026-11-21 from the merged manifest of a 3.0.0 release build (version code 59), in the WSL build copy
(`.ink/build/android/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml`), and checked against
`android/src/main/AndroidManifest.xml`. Ink adds no permissions here: `ink.toml` asks only for its `image` capability, which
has none, and Ink's own manifest asks for none.

| Permission | From | Kind |
|---|---|---|
| `VIBRATE` | the app's manifest | normal |
| `SYSTEM_ALERT_WINDOW` | the app's manifest | special access, the user turns it on |
| `INTERNET` | the app's manifest | normal |
| `ACCESS_NETWORK_STATE` | the app's manifest | normal |
| `POST_NOTIFICATIONS` | the app's manifest | runtime |
| `READ_CONTACTS` | the app's manifest | runtime (dangerous, not restricted) |
| `com.android.vending.BILLING` | the Play Billing Library 9.1.0 | normal |
| `com.titankeys.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | AndroidX Core, added to every app that uses it | signature, the app's own |

Services, not asked of the user: `BIND_INPUT_METHOD` (the keyboard), `BIND_ACCESSIBILITY_SERVICE` (`BackService`) and
`BIND_JOB_SERVICE` (the daily jobs).

**Not in it**, as Data safety needs: no `com.google.android.gms.permission.AD_ID`, no `QUERY_ALL_PACKAGES`, no
`RECEIVE_BOOT_COMPLETED`, no foreground-service permissions. Check again on each Play bundle with
`bundletool dump manifest` (`build-tasks.md`, C4); the Play build differs only by its version code and the
`com.titankeys.build` meta-data.

## Display over other apps (`SYSTEM_ALERT_WINDOW`)

**Short reason:** "The Titan 2 Elite sends touchpad input to the window on top. TitanKeys keeps a window over other apps so your
swipes stay on the keys, even when an app shows its own pop-up, such as text selection buttons. It doesn't read what's on the
screen."

**Where it shows:** the guide's setup step "Allow it over other apps", before it opens Android's page, and the listing's
permissions section.

**Play:** no declaration form for this permission. Android shows its own "displaying over other apps" notification, which the
user can turn off.

## Notifications (`POST_NOTIFICATIONS`)

**Short reason:** "For news about TitanKeys, such as a new version or a tip. They're silent, they never show while you're
typing, and you can turn them off in Settings > News."

**Where it shows:** right before Android's notification prompt, which the app shows only when it first has news to show or when
the user turns on News. Not at first launch with nothing to explain. News also shows in Settings without it.

**Play:** a normal runtime permission. No declaration.

## Internet (`INTERNET`, `ACCESS_NETWORK_STATE`)

**Short reason:** "TitanKeys uses the internet for a few things only. It downloads a short, signed news file at most once a
day, with nothing that identifies you. It sends a bug report when you press Send. The Support page shows the supporters board,
and posts to it only if you join. And if you turn on Sharing, it sends swipe examples on Wi-Fi and a day's totals, encrypted.
It never sends what you type."

**Why both:** `INTERNET` makes the connection. `ACCESS_NETWORK_STATE` lets Android's job scheduler wait for a connection, and for
Wi-Fi before swipe examples are sent, so TitanKeys doesn't use mobile data for them. From Android 14, an app "must hold the
ACCESS_NETWORK_STATE permission to schedule a job that requires a network"
([JobInfo.Builder](https://developer.android.com/reference/android/app/job/JobInfo.Builder#setRequiredNetworkType(int))), and
`Connected.schedule` sets its jobs that way.

**Where it shows:** Android doesn't ask for these, so the listing and the privacy policy are where users read it.

**Play:** normal permissions. No declaration. The Data safety form covers what is sent (`data-safety.md`).

## Contacts (`READ_CONTACTS`)

**Short reason:** "Optional. If you turn on Names from my contacts, TitanKeys reads your contacts' first and last names so you
can swipe and type them. They stay on your phone and are never sent."

**Where it shows:** Android's prompt comes only after the user says yes, never at first launch. It comes from three places:

- The guide's setup, in an optional step after the others. The step first explains what the names are for and that they stay
  on the phone. Android's prompt shows only after Yes there. Not now moves on, and the guide doesn't ask again.
- Settings > Setup > Optional > Names from my contacts.
- Settings > Your words > Names from my contacts.

The setting is on only once Android allows it.

**Play:** a dangerous permission, but not a restricted one, so no declaration form. The Data safety form stays "Not collected"
for Contacts, because nothing leaves the phone (`data-safety.md`).

## Accessibility service (`BIND_ACCESSIBILITY_SERVICE`, `BackService`)

**Short reason:** "Optional. It lets one press of Back close the keyboard and go back. At a few set times, such as while the Sym
panel is open or in the Smart Launcher search field, it also sees which keys you press, so the keyboard's own keys work there.
It can't read your screen, and it doesn't keep or send anything."

**Where it shows:** its own disclosure screen in the guide, with "No thanks" and "Agree" (`accessibility.md`).

**Play:** needs the accessibility declaration in Play Console, the in-app disclosure, and a video.

## Google Play billing (`com.android.vending.BILLING`)

**Short reason:** "For the Support page, so you can buy the project a coffee through Google Play. Google Play handles the
payment."

**Where it shows:** Android doesn't ask for it. The listing shows "In-app purchases" once the products are active
(`products.md`).

**Play:** added by the Billing Library. No declaration.

## The rest (no reason needed)

- `VIBRATE`: the ticks you feel while steering and typing. A normal permission.
- `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`: AndroidX adds it so the app's own unexported receivers can't be reached by other
  apps. Only TitanKeys holds it.
- `BIND_INPUT_METHOD`, `BIND_JOB_SERVICE`: not asked of the user. They protect the keyboard service and the job service so only
  Android can use them.
- The `<queries>` entries:
  - launcher apps (`MAIN` with `HOME` and `LAUNCHER`): let TitanKeys see which launcher is the home screen, and read the names
    of home-screen apps so they can be swiped and typed with their capitals;
  - `com.agui.shortcutsettings`: the phone's Shortcut keys page, which Settings > Setup opens;
  - Play's billing service and its test companion: added by the Billing Library;
  - Custom Tabs and `https` links, `com.lightos` and the Light Phone SDK marker: added by Ink.

  None of them is `QUERY_ALL_PACKAGES`, so no declaration.
