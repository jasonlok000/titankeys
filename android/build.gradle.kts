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
