from pathlib import Path
import os, re, subprocess, tempfile
ROOT = Path(__file__).resolve().parents[2]
source = (ROOT / "android/kotlin/com/titankeys/keyboard/ChoicesMenu.kt").read_text()
def method(name):
    match = re.search(r"    (?:private |override )fun " + name + r"\(", source)
    begin = match.start(); opening = source.index("{", begin); depth = 1; end = opening + 1
    while depth:
        if source[end] == "{": depth += 1
        if source[end] == "}": depth -= 1
        end += 1
    return source[begin:end]
methods = method("cellAt") + "\n" + method("onTouchEvent")
if "fun targetAt(" in source: methods += "\n" + method("targetAt")
if "fun observe(" in source: methods += "\n" + method("observe")
fixture = r'''
class ApplicationInfo(var flags: Int = 0) { companion object { const val FLAG_DEBUGGABLE=2 } }
class Context(var packageName: String = "com.titankeys.debug", val applicationInfo: ApplicationInfo = ApplicationInfo(), var buildKind: String = "debug")
object AppBuild { fun kind(context: Context) = context.buildKind }
object Log { val lines=mutableListOf<String>(); fun i(tag: String, value: String) { lines += value } }
object ChineseMenu { fun starts(choices: List<String>) = listOf(0) }
object KeyEvent { const val KEYCODE_DEL = 67; const val KEYCODE_ENTER = 66; const val KEYCODE_SYM = 63 }
class MotionEvent(val actionMasked: Int, val x: Float, val y: Float) {
    companion object { const val ACTION_DOWN=0; const val ACTION_UP=1; const val ACTION_CANCEL=3 }
}
open class View { open fun onTouchEvent(e: MotionEvent) = false }
data class Cell(val index: Int, val row: Int, val columns: IntRange)
data class Page(val cells: List<Cell>)
class Menu : View() {
    val width = 1000
    val height = 170
    val ROW_HEIGHT = 46
    val picks = mutableListOf<Int>()
    val controls = mutableListOf<Int>()
    val page = Page(listOf(Cell(10,0,1..2), Cell(11,2,7..7)))
    var hasPrevious = false
    var hasNext = true
    var control: (Int) -> Unit = { controls += it }
    fun tapped(index: Int) { picks += index }
    val context=Context()
    val isShown=true
    val starts=arrayListOf(0)
    val choices=arrayListOf("must-not-be-logged")
    fun getLocationOnScreen(at: IntArray) { at[0]=0; at[1]=800 }
    fun column(c: Int) = (c * 100f) to ((c + 1) * 100f)
    fun dp(n: Int) = n
__TOUCHED__
__METHODS__
    fun tap(x: Float,y: Float) { onTouchEvent(MotionEvent(0,x,y)); onTouchEvent(MotionEvent(1,x,y)) }
}
fun main() {
    val m=Menu()
    m.tap(150f,20f); check(m.picks == listOf(10))
    m.tap(950f,115f); check(m.controls == listOf(66)) { "Visible next arrow did not dispatch Enter" }
    m.tap(950f,69f); check(m.controls == listOf(66)) { "Disabled previous arrow dispatched" }
    m.hasPrevious=true; m.tap(950f,69f); check(m.controls.last()==67)
    m.hasNext=false; val count=m.controls.size; m.tap(950f,115f); check(m.controls.size==count)
    m.tap(850f,115f); check(m.controls.last()==63)
    val before=m.controls.size
    m.onTouchEvent(MotionEvent(0,850f,115f)); m.onTouchEvent(MotionEvent(1,950f,69f)); check(m.controls.size==before)
    m.onTouchEvent(MotionEvent(0,850f,115f)); m.onTouchEvent(MotionEvent(3,850f,115f)); m.onTouchEvent(MotionEvent(1,850f,115f)); check(m.controls.size==before)
    val picks=m.picks.size
    m.tap(150f,-1f); m.tap(-1f,20f); m.tap(1001f,115f); m.tap(150f,160f)
    check(m.picks.size==picks && m.controls.size==before)
    check(Log.lines.isNotEmpty() && Log.lines.none { "must-not-be-logged" in it })
    check("controls=950,869,950,915,850,915" in Log.lines.last())
    val logged=Log.lines.size
    m.context.packageName="com.titankeys"; m.tap(850f,115f); check(Log.lines.size==logged)
    m.context.packageName="com.titankeys.debug"; m.context.buildKind="release"; m.context.applicationInfo.flags=2
    m.tap(850f,115f); check(Log.lines.size==logged)
    m.context.buildKind="debug"; m.context.applicationInfo.flags=0
    m.tap(850f,115f); check(Log.lines.size==logged+1) { "Debug build metadata must work without android:debuggable" }
    println("PASS menu touch candidate, three controls, edge, slide, cancel, bounds and Debug-only numeric evidence checks")
}
'''.replace("__TOUCHED__", re.search(r"    private var touched = [^\n]+", source).group()).replace("__METHODS__", methods)
with tempfile.TemporaryDirectory() as temp:
    temp=Path(temp); kt=temp/"MenuTouch.kt"; kt.write_text(fixture)
    kotlin=Path(os.environ["KOTLIN_HOME"])
    subprocess.run([str(kotlin/"bin/kotlinc"), "-nowarn", str(kt), "-d", str(temp/"test.jar")],check=True)
    subprocess.run(["java","-cp",str(temp/"test.jar")+os.pathsep+str(kotlin/"lib/kotlin-stdlib.jar"),"MenuTouchKt"],check=True)
