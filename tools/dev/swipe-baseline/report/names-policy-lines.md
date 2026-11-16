# Names on this phone: lines for the privacy policy, Play's forms and the permissions draft

## Privacy policy (new paragraph, under what stays on the phone)

> **Names on this phone.** TitanKeys knows the names of the apps on your home screen as words, so you can swipe and type them with the right capitals. If you turn on "Names from my contacts" in Settings > Your words, and allow Contacts when Android asks, TitanKeys also reads the first and last names of your contacts, and nothing else about them. These names are only used on your phone to recognise the words you type. They are kept in memory, never saved, never learned, never included in contributions, and never sent anywhere. Turning the setting off, or taking away the Contacts permission, removes them at once.

## Play data-safety form

Google counts data as "collected" only when it leaves the phone, so the answers stay the same and only the notes change:

| Category | Data type | Answer | Note |
|---|---|---|---|
| Contacts | Contacts | **Not collected** | Read on the phone only, with the user's opt-in and Android's Contacts permission, to recognise contacts' first and last names as words. Never sent or shared. |
| App activity | Installed apps | **Not collected** | The labels of home-screen apps are read on the phone to recognise app names as words. Never sent or shared. |

- In `docs/play/data-safety.md`, the row "Photos and videos, Audio, Files and docs, Calendar, Contacts" splits so that Contacts carries this note.
- The "Installed apps" row gets the note above.

## Permissions draft (`docs/play/permissions.md`)

New section:

> **Contacts (`READ_CONTACTS`)**
>
> **Short reason:** "Optional. If you turn on Names from my contacts, TitanKeys reads your contacts' first and last names so you can swipe and type them. They stay on your phone and are never sent."
>
> **Where it shows:** Android's own prompt, right after the user turns on Settings > Your words > Names from my contacts. It is never asked at first launch.
>
> **Play:** a dangerous runtime permission but not a restricted one (unlike SMS or Call log), so no declaration form. The data-safety answer stays "Not collected" because nothing leaves the phone.

Line to add under "The rest":

> The `<queries>` entry for launcher apps (MAIN/LAUNCHER) lets TitanKeys read the names of the apps on the home screen, to know them as words. It is not `QUERY_ALL_PACKAGES`, so no declaration.

## Contributions (for workstream F)

- A name on this phone is never in a contribution. Contributions only send kept words that are dictionary words, and names on this phone are by definition words that the base list lacks.
- `SwipeTyping.phoneName(word)` is there as a second filter if F wants one.
