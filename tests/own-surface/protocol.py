"""Packaging and scope constraints complement the compiled policy/receiver checks."""
from pathlib import Path
import xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[2]
manifest=(root/'android/debug/recorder/manifest.xml').read_text()
xml=ET.fromstring('<application xmlns:android="http://schemas.android.com/apk/res/android">'+manifest+'</application>')
ns='{http://schemas.android.com/apk/res/android}'
receiver=next(n for n in xml.findall('receiver') if n.get(ns+'name')=='com.titankeys.keyboard.OwnSurfaceCapture')
assert receiver.get(ns+'permission')=='android.permission.DUMP' and receiver.get(ns+'exported')=='true'
source=(root/'android/debug/recorder/OwnSurfaceCapture.kt').read_text()
assert 'PixelCopy.request(view as SurfaceView, bitmap' in source and 'view.draw(Canvas(bitmap))' in source
assert 'android.R.id.content' in source and 'owner.window.decorView !== window' in source
assert 'isOrderedBroadcast' in source and 'intent.component?.packageName != APP' in source
assert 'WindowInspector.getGlobalWindowViews()' in source
assert 'SpaceGestureActivity' in source and 'FeedEditor.capture(APP)' in source
assert 'GuideActivity' in source and 'field.privateImeOptions != GuideHooks.FIELD' in source
assert 'filterIsInstance<GuideKeyboardView>().singleOrNull()' in source
panel = source[source.index('private fun panelState('):source.index('private fun descendants(')]
assert 'is ChoicesMenu' in panel and 'view.page.cells.isNotEmpty()' in panel
assert 'it.back && it.items.isNotEmpty()' in panel and 'getLocalVisibleRect' in panel
assert all(token not in panel for token in ('javaClass', 'MenuEffects', 'SymPanel'))
pickers = (root/'android/kotlin/com/titankeys/keyboard/Languages.kt').read_text().split('sealed interface LanguagePicker', 1)[1]
assert 'back = true' not in pickers.split('class Switch(', 1)[0]
assert 'back = true' in pickers.split('class Switch(', 1)[1]
assert 'AppBuild.kind(context) != "debug"' in source
assert 'Settings.Secure.put' not in source and 'screencap' not in source
assert 'getExternalFilesDir' not in source and 'Log.' not in source
assert 'Base64.NO_WRAP' in source and 'cacheDir, FILE' in source
print('PASS DUMP packaging, fixed own render routes, private bounded readback and no display/log fallback')
