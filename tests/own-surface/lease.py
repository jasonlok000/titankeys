"""Execute receiver methods and ownership checks with deterministic Android platform doubles."""
from pathlib import Path
import os, subprocess, tempfile
root=Path(__file__).resolve().parents[2]
source=(root/'android/debug/recorder/OwnSurfaceCapture.kt').read_text()
def block(marker):
    start=source.index(marker)
    opening=source.index('{',start)
    depth=1; end=opening+1
    while depth:
        depth += (source[end]=='{')-(source[end]=='}'); end+=1
    return source[start:end]
valid=source[source.index('fun valid(): Boolean'):source.index('fun watch(')]
limited=block('private class LimitedOutput(').replace('private class','class',1)
native='\n'.join(block(marker) for marker in (
    'private fun ownedContent(', 'private fun spaceEditor(', 'private fun guideKeys(', 'private fun panelView(',
    'private fun panelState(', 'private fun descendants(',
))
fixture=r'''
package com.titankeys.keyboard
import java.lang.ref.WeakReference
import java.io.*
const val APP="com.titankeys.debug"
open class Context(var packageName:String=APP, var selected:Boolean=true)
open class Activity:Context() { val window=Window() }
class SpaceGestureActivity:Activity()
class GuideActivity:Activity()
class Window { var decorView:View?=null }
object android { object R { object id { const val content=1 } } }
object EditorInfo { const val IME_FLAG_NO_PERSONALIZED_LEARNING=1 }
object GuideHooks { const val FIELD="com.titankeys.guide" }
class Display(val displayId:Int)
class Rect(var left:Int=0,var top:Int=0,var right:Int=0,var bottom:Int=0)
open class View(val context:Context=Context()) {
 var focus=true; var isAttachedToWindow=true; var isShown=true
 var width=100; var height=100; var windowToken=Any(); var rootView:View=this
 var imeWindow=true; var display:Display?=Display(0); var settings:View?=null
 var content:ViewGroup?=null; var focused:View?=null
 var clipped=false; var alpha=1f
 var x=0; var y=0
 fun hasWindowFocus()=focus
 fun findFocus()=focused
 inline fun <reified V:View> findViewById(id:Int)=content as? V
 fun getLocationOnScreen(at:IntArray) { at[0]=x; at[1]=y }
 fun getLocalVisibleRect(rect:Rect):Boolean {
  rect.right=width; rect.bottom=if (clipped) height-1 else height
  return isShown
 }
}
open class ViewGroup(context:Context=Context()):View(context) {
 val children=mutableListOf<View>()
 val childCount get()=children.size
 fun getChildAt(index:Int)=children[index]
}
class ScrollView(context:Context):ViewGroup(context)
class FrameLayout(context:Context):ViewGroup(context)
class EditText(context:Context):View(context) {
 var imeOptions=EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
 var privateImeOptions:String?=null
}
class GuideKeyboardView(context:Context):View(context)
class ChoicesMenu:View() {
 data class Page(val cells:List<Int>)
 var page=Page(listOf(1))
 var revision=1
}
class Strip:View() {
 sealed interface Notice { data class Picker(val items:List<String>,val back:Boolean):Notice }
 var notice:Notice?=null
}
class Surface { var isValid=true }
class Holder { var surface=Surface() }
class SurfaceView:View() { val holder=Holder() }
class FeedEditor { var good=true; fun valid()=good }
fun activity(context:Context)=context as? Activity
NATIVE
class Lease(val context:Context, window:View, target:View, val kind:String, val editor:FeedEditor?) {
 val root=WeakReference(window); val view=WeakReference(target)
 val token=WeakReference(window.windowToken); val targetRoot=WeakReference(target.rootView)
 val targetToken=WeakReference(target.windowToken)
 val width=target.width; val height=target.height
 val location=IntArray(2).also(target::getLocationOnScreen)
 val surface=(target as? SurfaceView)?.holder?.surface?.let { WeakReference(it) }
 val panelAtCapture=if (kind=="panel") panelState(target) else null
 var lost=false
 fun selected(context:Context)=context.selected
 fun isImeWindow(root:View)=root.imeWindow
 fun settingsSurface(root:View)=root.settings
 VALID
}
LIMITED
fun main(args: Array<String>) {
 fun window(owner:Activity, body:ViewGroup):View = View(owner).apply {
  owner.window.decorView=this
  content=ViewGroup(owner).apply { children.add(body) }
 }
 fun fixture():Pair<View,EditText> {
  val owner=SpaceGestureActivity()
  val body=ScrollView(owner)
  val field=EditText(owner)
  body.children.add(field)
  val root=window(owner,body)
  root.focused=field
  field.rootView=root
  return root to field
 }
 fun trial(change:(Context,View,View,FeedEditor,Lease)->Unit) {
  val c=Context(); val (w,_)=fixture(); val v=View(); val e=FeedEditor(); val l=Lease(c,w,v,"strip",e)
  check(l.valid()); change(c,w,v,e,l); check(!l.valid())
 }
 trial { c,_,_,_,_->c.selected=false }
 trial { _,w,_,_,_->w.focus=false }
 trial { _,w,_,_,_->w.windowToken=Any() }
 trial { _,_,v,_,_->v.context.packageName="com.titankeys" }
 trial { _,_,v,_,_->v.isShown=false }
 trial { _,_,v,_,_->v.isAttachedToWindow=false }
 trial { _,_,v,_,_->v.width++ }
 trial { _,_,v,_,_->v.x++ }
 trial { _,_,v,_,_->v.windowToken=Any() }
 trial { _,_,v,_,_->v.rootView=View() }
 trial { _,_,v,_,_->v.imeWindow=false }
 trial { _,_,v,_,_->v.display=Display(1) }
 trial { _,_,_,e,_->e.good=false }
 trial { _,w,_,_,l->w.focus=false; l.lost=true; w.focus=true }
 val v=SurfaceView(); val w=View(); v.rootView=w; w.settings=v; val l=Lease(Context(),w,v,"settings",null)
 check(l.valid()); v.holder.surface=Surface(); check(!l.valid())
 val (editorWindow,field)=fixture()
 val editor=FeedEditor()
 val editorLease=Lease(Context(),editorWindow,field,"editor",editor)
 check(editorLease.valid() && !Lease(Context(),editorWindow,field,"editor",null).valid())
 editorWindow.focused=View()
 check(!editorLease.valid())
 editorWindow.focused=field; field.imeOptions=0
 check(!editorLease.valid())
 field.imeOptions=EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
 editor.good=false; check(!editorLease.valid())
 val guide=GuideActivity()
 val guideContent=FrameLayout(guide)
 val guideWindow=window(guide,guideContent)
 val guideField=EditText(guide).apply { privateImeOptions=GuideHooks.FIELD }
 val keys=GuideKeyboardView(guide).apply { rootView=guideWindow }
 guideContent.children.addAll(listOf(guideField,keys))
 val guideLease=Lease(Context(),guideWindow,keys,"guide",null)
 check(guideLease.valid())
 check(!Lease(Context(),guideWindow,guideField,"guide",null).valid())
 guideField.privateImeOptions="com.titankeys.playground"
 check(!guideLease.valid())
 guideField.privateImeOptions=GuideHooks.FIELD
 guideContent.children.add(GuideKeyboardView(guide)); check(!guideLease.valid())
 guideContent.children.removeAt(guideContent.children.lastIndex)
 guide.window.decorView=View(); check(!guideLease.valid())
 guide.window.decorView=guideWindow; guide.packageName="com.titankeys"; check(!guideLease.valid())
 val (panelWindow,_)=fixture()
 val imeWindow=ViewGroup()
 val choices=ChoicesMenu().apply { rootView=imeWindow }
 val strip=Strip().apply { rootView=imeWindow; isShown=false }
 imeWindow.children.addAll(listOf(strip,choices))
 val panelEditor=FeedEditor()
 val menu=Lease(Context(),panelWindow,choices,"panel",panelEditor)
 check(panelView(imeWindow)===choices && menu.valid())
 choices.clipped=true; check(!menu.valid())
 choices.clipped=false; check(menu.valid())
 choices.alpha=0f; check(panelView(imeWindow)==null)
 choices.alpha=1f; check(menu.valid())
 choices.revision++; check(!menu.valid())
 choices.revision--; panelEditor.good=false; check(!menu.valid())
 panelEditor.good=true; imeWindow.imeWindow=false; check(!menu.valid())
 imeWindow.imeWindow=true; choices.page=ChoicesMenu.Page(emptyList()); check(panelView(imeWindow)==null)
 choices.page=ChoicesMenu.Page(listOf(1)); choices.isShown=false; check(panelView(imeWindow)==null)
 strip.isShown=true; strip.notice=Strip.Notice.Picker(listOf("a"),false); check(panelView(imeWindow)==null)
 strip.notice=Strip.Notice.Picker(emptyList(),true); check(panelView(imeWindow)==null)
 strip.notice=Strip.Notice.Picker(listOf("English"),true)
 val switcher=Lease(Context(),panelWindow,strip,"panel",panelEditor)
 check(panelView(imeWindow)===strip && switcher.valid())
 strip.notice=Strip.Notice.Picker(listOf("Japanese"),true); check(!switcher.valid())
 choices.isShown=true; check(panelView(imeWindow)==null)
 val bytes=ByteArrayOutputStream(); val capped=LimitedOutput(bytes)
 capped.write(ByteArray(OwnCapturePolicy.MAX_BYTES))
 try { capped.write(1); error("Limit not enforced") } catch (_:IOException) {}
 check(bytes.size()==OwnCapturePolicy.MAX_BYTES)
 println("PASS capture owner and window identity, fixture editor, guide keys, occupied panels, selected IME and exact PNG limit")
 com.titankeys.keyboard.lifecycle.lifecycle(File(args.single()))
}
'''.replace('VALID',valid).replace('LIMITED',limited).replace('NATIVE',native)
def line(marker):
    return next(line.strip() for line in source.splitlines() if marker in line)
lifecycle = (root/'tests/own-surface/LifecycleChecks.kt').read_text().replace('// RECEIVER', '\n'.join([
    block('override fun onReceive('), block('private fun capture('), block('private fun read('),
    block('private fun answer('), line('private data class Record('), block('private class LimitedOutput('),
    line('private fun removeFile('), block('private fun discard('), line('private fun error('),
]))
with tempfile.TemporaryDirectory() as temporary:
    out=Path(temporary); (out/'Lease.kt').write_text(fixture,newline='')
    (out/'LifecycleChecks.kt').write_text(lifecycle,newline='')
    kotlin=Path(os.environ['KOTLIN_HOME'])
    subprocess.run([str(kotlin/'bin/kotlinc'),'-nowarn',str(out/'Lease.kt'),str(out/'LifecycleChecks.kt'),
                    str(root/'android/debug/recorder/OwnCapturePolicy.kt'),'-d',str(out/'check.jar')],check=True)
    subprocess.run(['java','-cp',str(out/'check.jar')+':'+str(kotlin/'lib/kotlin-stdlib.jar'),
                    'com.titankeys.keyboard.LeaseKt',str(out)],check=True)
