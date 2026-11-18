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
fixture=r'''
package com.titankeys.keyboard
import java.lang.ref.WeakReference
import java.io.*
const val APP="com.titankeys.debug"
class Context(var packageName:String=APP, var selected:Boolean=true)
class Display(val displayId:Int)
open class View(val context:Context=Context()) {
 var focus=true; var isAttachedToWindow=true; var isShown=true
 var width=100; var height=100; var windowToken=Any(); var rootView:View=this
 var imeWindow=true; var display:Display?=Display(0); var settings:View?=null
 var x=0; var y=0
 fun hasWindowFocus()=focus
 fun getLocationOnScreen(at:IntArray) { at[0]=x; at[1]=y }
}
class Surface { var isValid=true }
class Holder { var surface=Surface() }
class SurfaceView:View() { val holder=Holder() }
class FeedEditor { var good=true; fun valid()=good }
class Lease(val context:Context, window:View, target:View, val editor:FeedEditor?) {
 val root=WeakReference(window); val view=WeakReference(target)
 val token=WeakReference(window.windowToken); val targetRoot=WeakReference(target.rootView)
 val targetToken=WeakReference(target.windowToken)
 val width=target.width; val height=target.height
 val location=IntArray(2).also(target::getLocationOnScreen)
 val surface=(target as? SurfaceView)?.holder?.surface?.let { WeakReference(it) }
 var lost=false
 fun selected(context:Context)=context.selected
 fun isImeWindow(root:View)=root.imeWindow
 fun settingsSurface(root:View)=root.settings
 VALID
}
LIMITED
fun main(args: Array<String>) {
 fun trial(change:(Context,View,View,FeedEditor,Lease)->Unit) {
  val c=Context(); val w=View(); val v=View(); val e=FeedEditor(); val l=Lease(c,w,v,e)
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
 val v=SurfaceView(); val w=View(); v.rootView=w; w.settings=v; val l=Lease(Context(),w,v,null)
 check(l.valid()); v.holder.surface=Surface(); check(!l.valid())
 val bytes=ByteArrayOutputStream(); val capped=LimitedOutput(bytes)
 capped.write(ByteArray(OwnCapturePolicy.MAX_BYTES))
 try { capped.write(1); error("Limit not enforced") } catch (_:IOException) {}
 check(bytes.size()==OwnCapturePolicy.MAX_BYTES)
 println("PASS capture owner and window identity, selected IME, editor, focus, layout, surface identity and exact PNG output limit")
 com.titankeys.keyboard.lifecycle.lifecycle(File(args.single()))
}
'''.replace('VALID',valid).replace('LIMITED',limited)
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
