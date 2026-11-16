# The native swipe engine calls these classes and constructors by name.
-keep class org.futo.ml.inference.** { *; }
# ONNX Runtime's native library does the same.
-keep class ai.onnxruntime.** { *; }
# Rime's JNI layer (native/rime/rime_jni.cc) registers its methods on this class by name.
-keep class com.atlas.rime.RimeNative { *; }
