plugins { id("com.android.library") }

android {
    namespace = "com.atlas.keyboard"
    compileSdk = 36
    defaultConfig {
        minSdk = 34
        consumerProguardFiles("proguard-rules.pro")
    }
    sourceSets.getByName("main") {
        assets.srcDir("assets")
        jniLibs.srcDir("jniLibs")
    }
}

dependencies {
    // ONNX Runtime 1.20.0 runs swipe engine 2.0's models. Only its Java classes are here; its arm64 libraries are in
    // jniLibs, so the app does not carry the three other processor types of the full package. It is "api" because the
    // app compiles the kotlin folder itself and needs the classes too.
    api(files("libs/onnxruntime-android-1.20.0.jar"))
}
