plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "org.kuanua.hymns"
    compileSdk = 36
    defaultConfig { applicationId = "org.kuanua.hymns"; minSdk = 26; targetSdk = 36; versionCode = 2; versionName = "1.0.0" }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildTypes {
        debug {
            buildConfigField("String", "ADMOB_BANNER_AD_UNIT_ID", "\"ca-app-pub-3940256099942544/9214589741\"")
            buildConfigField("String", "ADMOB_NATIVE_AD_UNIT_ID", "\"ca-app-pub-3940256099942544/2247696110\"")
        }
        release {
            buildConfigField("String", "ADMOB_BANNER_AD_UNIT_ID", "\"ca-app-pub-9608341092298145/6380345478\"")
            // Native ads stay disabled in release until a real native ad unit is created in AdMob.
            buildConfigField("String", "ADMOB_NATIVE_AD_UNIT_ID", "\"\"")
        }
    }
}
dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("com.google.android.gms:play-services-ads:24.9.0")
    implementation("com.google.android.ump:user-messaging-platform:4.0.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
