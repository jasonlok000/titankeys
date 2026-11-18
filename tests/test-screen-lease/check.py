"""Check the receiver packaging and exercise its flag ownership and task bounds."""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[2]
recorder = root / 'android/debug/recorder'
source = (recorder / 'TestScreenLease.kt').read_text(encoding='utf-8')
namespace = '{http://schemas.android.com/apk/res/android}'
manifest = (recorder / 'manifest.xml').read_text(encoding='utf-8')
xml = ET.fromstring(f'<application xmlns:android="{namespace[1:-1]}">{manifest}</application>')
receiver = next(item for item in xml.findall('receiver')
                if item.get(namespace + 'name') == 'com.titankeys.keyboard.TestScreenLease')
assert receiver.get(namespace + 'permission') == 'android.permission.DUMP'
assert receiver.get(namespace + 'exported') == 'true'
assert 'TestScreenLease' not in (root / 'android/src/main/AndroidManifest.xml').read_text(encoding='utf-8')
assert all(token not in source for token in ('WAKE_LOCK', 'newWakeLock', 'Settings.', 'FLAG_TURN_SCREEN_ON', 'startActivity('))
assert all(token in source for token in ('isOrderedBroadcast', 'AppBuild.kind(context) != "debug"',
    'onActivityPrePaused', 'onActivityDestroyed', 'ACTION_SCREEN_OFF', 'test-screen-lease-v1',
    'privateImeOptions == GuideHooks.FIELD', 'FeedEditor.capture(APP)', 'isKeyguardLocked == false', 'Display.STATE_ON'))

flag = source[source.index('    private class AddedFlag'):source.index('    companion object')]
policy = source[source.index('        private fun duration'):source.index('        private fun end')]
tests = '''
fun main() {
    check(duration(0) == null && duration(-1) == null && duration(901) == null)
    check(duration(1) == 1_000L && duration(900) == 900_000L)
    check(sameTask(11, 11, true, 11))
    check(!sameTask(11, 11, false, 11))
    check(!sameTask(11, 11, true, 12))
    check(!sameTask(11, 12, true, 11))
    check(!sameTask(-1, -1, true, -1))
    class Window(var keep: Boolean = false)
    val original = Window()
    val next = Window()
    val existing = Window(true)
    val flag = AddedFlag<Window>({ it.keep }, { check(!it.keep); it.keep = true }, { check(it.keep); it.keep = false })
    flag.show(original)
    check(original.keep)
    flag.show(original)
    flag.show(existing)
    check(!original.keep && existing.keep)
    flag.show(next)
    check(next.keep && existing.keep)
    flag.show(null)
    flag.show(null)
    check(!original.keep && !next.keep && existing.keep)
    println("PASS bounded duration, live task identity, window handover and preservation of pre-existing flags")
}
'''
Path(sys.argv[1]).write_text(flag + policy + tests, encoding='utf-8')
print('PASS Debug-only protected receiver, fixed scope and expiry/focus lifecycle wiring')
