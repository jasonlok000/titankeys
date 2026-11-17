# What TitanKeys needs from the phone

The owner, 2026-11-17: "if a feature is not going to be working because a system settings or service is not on it shd say so
explicitly and prompt for that to be switched or be turned on etc."

TitanKeys only reads these settings. The user turns each one on in Android's own screen, and TitanKeys never changes a secure or
global setting itself.

Where a feature can't work, its own row in Settings says what's off and gives a button to the system screen. There is no banner
at the top of Settings (owner: "unnecessary"). Settings and Your words read the phone's state again whenever the app comes back
to the front, so a warning goes away once its setting is on. The keyboard shows nothing while you type.

The code is in `lib/system-needs.tsx`; `tools/checks/system-needs.test.ts` checks it. Every line the user reads is an F: draft.

## The list

| Needs | Without it | How TitanKeys tells | Where Settings says so | Button opens |
|---|---|---|---|---|
| **TitanKeys Keyboard turned on in Android** | Nothing: TitanKeys can't be the keyboard | `PhoneSetup.enabled`: Android's list of enabled keyboards | Under **Setup** in Settings, and on Setup's own page | Android's keyboard list (`ACTION_INPUT_METHOD_SETTINGS`) |
| **TitanKeys chosen as the keyboard** | Nothing types; the touchpad overlay and the Scroll assistant block stay down (`updateOverlay`) | `PhoneSetup.selected`: `DEFAULT_INPUT_METHOD`, read only | Under **Setup**, and on Setup's page | Android's keyboard picker (`showInputMethodPicker`) |
| **Display over other apps** | The touchpad never reaches TitanKeys, so swiping, the cursor, Space taps and flicks do nothing; the Scroll assistant block can't hold the touchpad (`overlayUp`) | `Settings.canDrawOverlays` | Under **Swipe to type words** (Touchpad), and on Setup's page | TitanKeys' own overlay page, or Android's list where it refuses (`ACTION_MANAGE_OVERLAY_PERMISSION`) |
| **The phone's Scroll assistant on** (Titan's own setting) | The touchpad doesn't type at all | `PhoneSetup.scrollAssistant`: the firmware's switch | Under **Swipe to type words**, and on Setup's page | The phone's Scroll assistant page |
| **Each app on Sliding Mode 1** | Swiping doesn't work in that app | `PhoneChecks.outsideSlidingMode`: apps caught outside it twice | On Setup's page, by name (as before) | The phone's Scroll assistant page |
| **TitanKeys Keyboard on in Accessibility** (`BackService`) | <ul><li>**Scroll assistant off in TitanKeys (the block) doesn't hold:** apps still scroll from the touchpad (`TouchpadHold.wanted` needs the service, which hears Back's second push).</li><li>Back from the keyboard falls back to a key, which apps on Android's newer back handling ignore.</li><li>Typing in Smart Launcher's search: the launcher takes the keys first.</li><li>Keys pressed while the Sym panel is open can reach the app.</li><li>Pop-up notifications can take the touchpad over the top rows (no window above the shade).</li></ul> | `BackService.enabled` | Under **Scroll assistant** (Touchpad) while it's off in TitanKeys, and on Setup's page | TitanKeys' own Accessibility page, or Android's list (`ACTION_ACCESSIBILITY_SETTINGS`) |
| **Sym set to Programmable key** (phone's Shortcut keys) | Sym doesn't open the Sym panel; Hold Alt + Sym can't switch keyboards | `PhoneChecks.programmable(Sym)` | Under **Hold Alt + Sym for the next keyboard** while it's on, and on Setup's page | The phone's Shortcut keys page |
| **fn set to Programmable key** | fn can't work as Ctrl | `PhoneChecks.programmable(Fn)` | Under **Use fn as Ctrl** while it's on, and on Setup's page | The phone's Shortcut keys page |
| **Contacts permission** | Names from my contacts reads no names | `PhoneNameSource.contactsAllowed`; new `contactNamesBlocked` = the setting is on but Android took the permission away | Under **Names from my contacts** (Your words) | Android's own question, or the app's permission page once Android stops asking (`ContactsPermissionActivity`) |
| **Notifications** | News shows only in Settings; Record a bug has no timer in the status bar; the update notice can't show | `Announcements.notificationsAllowed` | Already on the **News** and **Record a bug** pages, each with Allow notifications (unchanged) | Android's question, then the app's notification settings (`ACTION_APP_NOTIFICATION_SETTINGS`) |
| Internet | News, Sharing and bug reports wait | Not a setting: the jobs wait for a connection | Sharing and the report form already say what waits | None |

Two TitanKeys apps (Debug and the release) each have their own keyboard entry, overlay permission and Accessibility service. Each
app's Settings reads its own.

## Not shown on purpose

- **The keyboard while typing** shows nothing, as the owner asked.
- **Android's battery limits** don't stop any feature: the daily jobs run when Android lets them.
