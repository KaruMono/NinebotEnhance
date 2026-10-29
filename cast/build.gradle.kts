plugins { id("com.android.application") }

android {
    namespace = "dev.ichinomiya.ninebotcast"
    compileSdk = 34
    defaultConfig {
        applicationId = "dev.ichinomiya.ninebotcast"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
