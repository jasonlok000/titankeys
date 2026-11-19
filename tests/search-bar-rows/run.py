#!/usr/bin/env python3
"""Reuse the real Composer suite's fixture/compiler in a temporary source file."""
from pathlib import Path
import subprocess
import tempfile
root = Path(__file__).resolve().parents[2]
base = (root / 'tests/autocorrect/AutocorrectChecks.kt').read_text()
assert base.count('fun main() {') == 1
# Test-only editor filter hook: the real Composer is unchanged in a red run.
base = base.replace('private class FakeEditor : Editor {', 'private class FakeEditor : Editor {\n    var afterReplace: ((FakeEditor) -> Unit)? = null\n    var holdState = false\n    val pendingStates = ArrayDeque<(Editor.State?) -> Unit>()', 1)
needle = '        report()\n        return true\n'
assert base.count(needle) == 1
base = base.replace(needle, '        afterReplace?.invoke(this)\n' + needle, 1)
old_state = '    override fun state(result: (Editor.State?) -> Unit) = result(if (unreadable) null else Editor.State(before()!!, after()!!, start, end))'
assert old_state in base
base = base.replace(old_state, '    override fun state(result: (Editor.State?) -> Unit) { if (holdState) pendingStates += result else result(if (unreadable) null else Editor.State(before()!!, after()!!, start, end)) }', 1)
base = base.replace('    var bar = emptyList<String>()', '    var bar = emptyList<String>()\n    val rows = ArrayList<Pair<String, List<String>>>()', 1)
# The Typist's bar callback records every row: a needle that no longer matches must fail here, not leave rows empty.
callback = '{ bar = it; bars += it })'
assert base.count(callback) == 1
base = base.replace(callback, '{ bar = it; bars += it; rows += editor.text to it.toList() })', 1)
source = base.replace('fun main() {', 'fun originalAutocorrectMain() {', 1) + '\n' + (Path(__file__).with_name('SearchRows.kt')).read_text()
with tempfile.TemporaryDirectory() as temporary:
    target = Path(temporary) / 'AutocorrectChecks.kt'
    target.write_text(source, encoding='utf-8', newline='')
    script = (root / 'tests/autocorrect/run.sh').read_text()
    script = script.replace('cd "$(dirname "$0")/../.."', 'cd "' + str(root) + '"', 1)
    script = script.replace('tests/autocorrect/AutocorrectChecks.kt', '"' + str(target) + '"')
    run = Path(temporary) / 'run.sh'
    run.write_text(script, encoding='utf-8', newline='')
    subprocess.run(['bash', str(run)], cwd=root, check=True)
