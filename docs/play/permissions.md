# Why TitanKeys asks for each permission

For #36. One short reason each, for the store listing, the app's own permission prompts, and any Play Console text box. None of these four needs its own Play Console form, except the accessibility service (see `accessibility.md`). Use the tone from #54 for the final wording.

## Display over other apps (`SYSTEM_ALERT_WINDOW`)

**Short reason:** "The Titan 2 Elite sends touchpad input to the window on top. TitanKeys keeps a window over other apps so your swipes stay on the keys, even when an app shows its own pop-up, such as text selection buttons. It doesn't read what's on the screen."

**Where it shows:** the guide's setup step "Allow it over other apps", before it opens Android's page, and the listing's permissions section.

**Play:** no declaration form for this permission. Android shows its own "displaying over other apps" notification, which the user can turn off.

## Notifications (`POST_NOTIFICATIONS`)

**Short reason:** "For news about TitanKeys, such as a new version or a tip. They're silent, they never show while you're typing, and you can turn them off in Settings > News."

**Where it shows:** right before Android's notification prompt, which the app shows only when it first has news to show or when the user turns on News. Not at first launch with nothing to explain.

**Play:** a normal runtime permission. No declaration.

## Internet (`INTERNET`, `ACCESS_NETWORK_STATE`)

**Short reason:** "TitanKeys uses the internet for two things only. It downloads a short, signed news file at most once a day, with nothing that identifies you. And if you choose to share swipe examples, it sends them encrypted, on Wi-Fi. It never sends what you type."

**Why both:** `INTERNET` makes the connection. `ACCESS_NETWORK_STATE` lets Android's job scheduler wait for a connection, and for Wi-Fi before contributions are sent, so TitanKeys doesn't use mobile data for them. From Android 14, an app "must hold the ACCESS_NETWORK_STATE permission to schedule a job that requires a network" ([JobInfo.Builder](https://developer.android.com/reference/android/app/job/JobInfo.Builder#setRequiredNetworkType(int))), and the 2.1 code schedules its jobs that way.

**Where it shows:** Android doesn't ask for these, so the listing and the privacy policy (#53) are where users read it. The README and the app must no longer say "no network access" (#53).

**Play:** normal permissions. No declaration. The Data safety form covers what is sent (`data-safety.md`).

## Accessibility service (`BIND_ACCESSIBILITY_SERVICE`, `BackService`)

**Short reason:** "Optional. It lets one push of Back close the keyboard and go back. At a few set times, such as while the Sym panel is open or in the Smart Launcher search field, it also sees which keys you push, so the keyboard's own keys work there. It can't read your screen, and it doesn't keep or send anything."

**Where it shows:** its own disclosure screen in the guide, with "No thanks" and "Agree" (`accessibility.md`).

**Play:** needs the accessibility declaration in Play Console, the in-app disclosure, and a video.

## The rest (no reason needed)

- `VIBRATE`: the ticks you feel while steering and typing. A normal permission.
- `BIND_INPUT_METHOD`: not asked of the user. It protects the keyboard service so only Android can use it.
- The `<queries>` entry for the home screen app: lets TitanKeys see which launcher is the home screen. It is not `QUERY_ALL_PACKAGES`, so no declaration.

**Check on the final App Bundle** that these are all the permissions it asks for. Ink and libraries can add more (`build-tasks.md`, task C4).
