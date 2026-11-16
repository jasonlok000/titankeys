# The native swipe engine calls these classes and constructors by name.
-keep class org.futo.ml.inference.** { *; }
# ONNX Runtime's native library does the same.
-keep class ai.onnxruntime.** { *; }
# Rime's JNI layer (native/rime/rime_jni.cc) registers its methods on RimeNative by name.
-keep class com.titankeys.rime.RimeNative { *; }
# The keyboard makes every input method's engine by its class name (EngineClasses in InputEngine.kt), which R8 can't see:
# without this, Korean's and Vietnamese's engines were left out of the APK. tests/languages/apk.sh checks the built APK.
-keep class * implements com.titankeys.keyboard.InputEngine { public <init>(android.content.Context); }
