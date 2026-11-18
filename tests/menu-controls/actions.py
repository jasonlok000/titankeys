"""Execute real menu actions, physical-key path and picker release block offline."""
from pathlib import Path
import os, subprocess, tempfile
ROOT=Path(__file__).resolve().parents[2]
source=(ROOT/"android/kotlin/com/titankeys/keyboard/TitanKeysIme.kt").read_text()
def block(start):
    opening=source.index("{",start); end=opening+1; depth=1
    while depth:
        if source[end]=="{": depth+=1
        if source[end]=="}": depth-=1
        end+=1
    return source[start:end]
def method(name):
    return block(source.index("    private fun "+name+"("))
release=block(source.index("        if (keyCode == pickerKey) {", source.index("// The release of a key that picked from the picker")))
callback='control = { key -> enqueue { choicesMenuControl(key) } }'
assert callback in source
fixture=r'''
class KeyEvent(val keyCode: Int, val repeatCount: Int=0, val scanCode: Int=0, val flags: Int=0) {
    companion object { const val KEYCODE_A=29; const val KEYCODE_Z=54; const val KEYCODE_ENTER=66
        const val KEYCODE_NUMPAD_ENTER=160; const val KEYCODE_DEL=67; const val KEYCODE_SYM=63; const val KEYCODE_SPACE=62 }
}
enum class Haptic { PanelPage }
class Haptics { var count=0; fun play(h: Haptic) { count++ } }
class Menu { var page=0; var open=true
    fun next(): Boolean { if(page==2)return false; page++; return true }
    fun previous(): Boolean { if(page==0)return false; page--; return true }
    fun choice(key: String)=0
}
class Engine { fun space()=1 }
class Languages { val engine: Engine?=Engine() }
class Fixture {
    var pickerKey=-1
    val choicesMenu: Menu?=Menu()
    val choicesMenuOpen get()=choicesMenu?.open==true
    val haptics=Haptics()
    val languages=Languages()
    var control: (Int)->Unit={}
    init { __CALLBACK__ }
    fun enqueue(work: ()->Unit) = work()
    fun closeChoicesMenu() { choicesMenu!!.open=false }
    fun choicesLog(line: String)=Unit
    fun tallyChoices(line: String)=Unit
    fun pickFromMenu(index: Int)=Unit
    fun engineState(state: Int)=Unit
__CONTROL__
__KEY__
    fun down(key: Int, repeat: Int=0)=choicesMenuKey(KeyEvent(key,repeat))
    fun release(keyCode: Int): Boolean { __RELEASE__; return false }
}
fun main() {
    val f=Fixture(); val menu=f.choicesMenu!!
    f.control(66); check(menu.page==1 && f.haptics.count==1 && f.pickerKey == -1)
    check(!f.release(66)) { "A screen tap stole a later real Enter release" }
    f.down(66); check(menu.page==2 && f.pickerKey==66 && f.haptics.count==2)
    f.down(66,1); check(menu.page==2 && f.haptics.count==2)
    check(!f.release(29) && f.pickerKey==66)
    check(f.release(66) && f.pickerKey == -1 && !f.release(66))
    f.control(66); check(menu.page==2 && f.haptics.count==2) { "Last-page tap added a haptic" }
    f.control(67); check(menu.page==1 && f.haptics.count==3)
    f.down(67); check(menu.page==0 && f.pickerKey==67 && f.haptics.count==4)
    f.control(63); check(!menu.open && f.pickerKey==67 && f.haptics.count==4)
    check(!f.release(63) && f.release(67)) { "Touch close overwrote the held physical key" }
    f.control(66); check(menu.page==0 && f.haptics.count==4) { "Closed menu accepted a queued tap" }
    menu.open=true; f.down(67); check(menu.page==0 && f.haptics.count==4); check(f.release(67))
    f.down(160); check(menu.page==1 && f.haptics.count==5); check(f.release(160))
    f.down(63); check(!menu.open && f.pickerKey==63 && f.haptics.count==5); check(f.release(63))
    println("PASS shared menu actions, edge haptics, physical repeat/release and touch independence")
}
'''.replace("__CALLBACK__",callback).replace("__CONTROL__",method("choicesMenuControl")).replace("__KEY__",method("choicesMenuKey")).replace("__RELEASE__",release)
with tempfile.TemporaryDirectory() as tmp:
    tmp=Path(tmp); kt=tmp/"Actions.kt"; kt.write_text(fixture)
    kotlin=Path(os.environ["KOTLIN_HOME"])
    subprocess.run([str(kotlin/"bin/kotlinc"),"-nowarn",str(kt),"-d",str(tmp/"test.jar")],check=True)
    subprocess.run(["java","-cp",str(tmp/"test.jar")+os.pathsep+str(kotlin/"lib/kotlin-stdlib.jar"),"ActionsKt"],check=True)
