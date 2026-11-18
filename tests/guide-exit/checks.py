#!/usr/bin/env python3
"""Execute production guide exit methods against a synthetic Android task stack.

No device calls: only the extracted methods are compiled, with a tiny Activity
boundary. Existing front-choice/settings-navigation suites cover the JS router.
"""
from pathlib import Path
import os
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'android/kotlin/com/titankeys/keyboard/GuideActivity.kt'


def method(source, name):
    start = source.index('    private fun ' + name + '(')
    brace = source.index('{', start)
    depth = 1
    end = brace + 1
    while depth:
        if source[end] == '{': depth += 1
        if source[end] == '}': depth -= 1
        end += 1
    return source[start:end].replace('private fun ', 'fun ', 1)


def main():
    source = SOURCE.read_text(encoding='utf-8')
    # Close-confirm and hardware Back must still reach the tested entry point.
    assert 'private fun leave()' in source and 'override fun onBackPressed()' in source
    back = source[source.index('override fun onBackPressed()'):source.index('override fun onBackPressed()') + 1500]
    assert 'leave()' in back
    connected = (ROOT / 'android/kotlin/com/titankeys/connected/Connected.kt').read_text()
    assert 'FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP' in connected
    fixture = r'''
data class Page(val path: String)
object Connected { fun pageIntent(context: Fixture, path: String) = Page(path) }
class Fixture(val playground: Boolean, val settingUp: Boolean, val setupDone: Boolean,
              val stack: MutableList<String>) {
    val PLAYGROUND_SETUP = "setup"
    var route = "/settings"
    var callback = ""
    var finished = false
    fun startActivity(page: Page) {
        // Android singleTask MainActivity is brought forward and its overlying
        // setup activities are removed, even if Accessibility reordered Guide.
        val main = stack.indexOf("MainActivity")
        check(main >= 0)
        while (stack.size > main + 1) stack.removeAt(stack.lastIndex)
        route = page.path
    }
    fun finish() {
        finished = true
        stack.remove("GuideActivity")
    }
    fun leavePlayground(action: String) { callback = action; finish() }
    fun surface() = if (stack.last() != "MainActivity") stack.last()
        else if (route == "/") { if (setupDone) "Playground" else "SetupStart" } else route
__LEAVE__
__END__
}
fun main() {
    var count = 0
    // The former finish-only exit reproduces the reported system Settings landing.
    val previous = Fixture(false, false, true, mutableListOf("MainActivity", "AccessibilitySettings", "SubSettings", "GuideActivity"))
    previous.finish()
    check(previous.surface() == "SubSettings")
    val histories = listOf(
        listOf("MainActivity", "GuideActivity"),
        listOf("MainActivity", "AccessibilitySettings", "AccessibilitySettings", "SubSettings", "GuideActivity"))
    for (history in histories) for (done in listOf(false, true)) for (mode in listOf("dismissed", "finished", "playground-setup")) {
        val f = Fixture(mode == "playground-setup", !done || mode == "playground-setup", done, history.toMutableList())
        f.leave()
        check(f.finished)
        check(f.surface() == if (done) "Playground" else "SetupStart") { "Exit uncovered ${f.surface()}" }
        check(f.stack == listOf("MainActivity"))
        if (mode == "playground-setup") check(f.callback == "setup")
        count++
    }
    val ordinary = Fixture(true, false, true, mutableListOf("MainActivity", "GuideActivity"))
    ordinary.leave()
    check(ordinary.finished && ordinary.route == "/settings") { "Ordinary Playground Back must not reopen Playground" }
    count++
    for (page in listOf("/end/guide-boring", "/end/play", "/end/settings")) {
        val f = Fixture(false, false, true, mutableListOf("MainActivity", "GuideActivity"))
        f.openFromEnd(page)
        check(f.route == page && f.finished) { "End link lost its own destination" }
        count++
    }
    println("PASS $count guide exit stack fixtures")
}
'''.replace('__LEAVE__', method(source, 'leave')).replace('__END__', method(source, 'openFromEnd'))
    kotlin = Path(os.environ['KOTLIN_HOME'])
    with tempfile.TemporaryDirectory() as tmp:
        tmp = Path(tmp)
        file = tmp / 'GuideExitChecks.kt'
        file.write_text(fixture, encoding='utf-8', newline='')
        subprocess.run([str(kotlin / 'bin/kotlinc'), '-nowarn', str(file), '-d', str(tmp / 'checks.jar')], check=True)
        subprocess.run(['java', '-cp', str(tmp / 'checks.jar') + os.pathsep + str(kotlin / 'lib/kotlin-stdlib.jar'), 'GuideExitChecksKt'], check=True)


if __name__ == '__main__':
    main()
