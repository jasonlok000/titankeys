# Debug own-surface capture

Only the Debug manifest adds `com.titankeys.keyboard.OwnSurfaceCapture`; every broadcast requires `android.permission.DUMP`. No display capture, shell execution, network, release behavior or settings writes.

Receiver component: `com.titankeys.debug/com.titankeys.keyboard.OwnSurfaceCapture`.

- Capture: `adb shell am broadcast -n COMPONENT --es op capture --es target settings` (or `strip`, `editor`, `guide`). Result data is JSON with `ok`, 32-lowercase-hex `id`, `target`, `kind` (`pixelcopy` or `view-draw`), `width`, `height`, screen-local `x`,`y`, PNG `bytes`, lowercase `sha256`, `expiresElapsedMs`, `capturedElapsedMs`, `guard:own-surface-v1`, `owner:com.titankeys.debug`, and exact `selectedIme`.
- Read: `adb shell am broadcast -n COMPONENT --es op read --es id ID --ei offset OFFSET`. JSON contains `ok,id,offset,total,eof,data`. Decode `data` as base64 without printing or storing the broadcast response. Offset starts at zero and increases by 32768 bytes. Last chunk may be shorter. Offset equal to total is rejected. Only the decoded PNG belongs in approved private evidence.
- Delete: `adb shell am broadcast -n COMPONENT --es op delete --es id ID`. JSON `{ok:true,deleted:true}`. Cleanup does not require foreground ownership. A wrong ID cannot delete an existing capture; an already-absent valid ID is idempotent.
- Errors: `{ok:false,error:CODE}` with fixed codes, no text, paths, candidate strings or exceptions.

The receiver accepts an ordered broadcast addressed to its exact component. It permits one outstanding capture or cached image. A timed-out copy retains its slot until PixelCopy releases the bitmap. Dimensions are 1–2048 pixels each, with at most 2,000,000 pixels and 4,194,304 PNG bytes. Each read returns up to 32768 decoded bytes. The capture deadline is 5 seconds and the capture expires 120 seconds after the pixels are copied.

The image file is app-private `cacheDir/own-surface.png`. The receiver reads it after validating ownership, compressing the pixels and recording the checksum. Timeout or failure deletes partial data. The expiry callback deletes the image when the process is running. The next receiver invocation checks expiry and removes any orphan from a previous process before handling the request.

`settings` uses PixelCopy on the focused Debug MainActivity’s primary Ink SurfaceView. The receiver finds this surface by its position in Ink’s content frame because the minified build renames the subclass. `strip` draws the visible Strip from the Debug IME window on the main thread while Debug’s SpaceGestureActivity owns the active editor.

`editor` draws the focused synthetic EditText in Debug’s SpaceGestureActivity. `guide` draws the illustrated keyboard and its gesture cues from the focused Debug GuideActivity. The guide’s lesson marker excludes the Playground and only the illustrated keyboard is drawn, so the practice field and surrounding lesson are outside the crop. Both targets report `view-draw`. The four capture paths supply component pixels. Android screen composition, separate panels and effects outside the captured component are outside this evidence.

All targets require agreement between the secure default and InputMethodManager about the exact selected Debug IME. The receiver checks package and window ownership, attachment, display, geometry and surface identity before capture and on every chunk read. It revokes the image on focus loss, detach, an observed selected-IME change, or surface recreation. Strip and editor also watch the actual focused editor.

`bash tests/own-surface/run.sh` compiles the receiver and editor ownership checks against Android 36, checks Debug packaging, and exercises ownership revocation, copy timeout and late completion, bounds, chunking, deletion and expiry. A device run is required to verify actual surface discovery, PixelCopy and view draw output, and transfer timing.
