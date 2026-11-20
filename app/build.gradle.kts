plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
val releaseKeystorePath = System.getenv("RELEASE_KEYSTORE_PATH")
val releaseStorePassword = System.getenv("RELEASE_STORE_PASSWORD")
val releaseKeyAlias = System.getenv("RELEASE_KEY_ALIAS")
val releaseKeyPassword = System.getenv("RELEASE_KEY_PASSWORD")
val hasReleaseSigning = listOf(releaseKeystorePath, releaseStorePassword, releaseKeyAlias, releaseKeyPassword).all { !it.isNullOrBlank() }

android {
    namespace = "org.kuanua.hymns"
    compileSdk = 36
    defaultConfig { applicationId = "org.kuanua.hymns"; minSdk = 26; targetSdk = 36; versionCode = 3; versionName = "1.0.1" }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }
    buildTypes {
        debug {
            buildConfigField("String", "ADMOB_BANNER_AD_UNIT_ID", "\"ca-app-pub-3940256099942544/9214589741\"")
            buildConfigField("String", "ADMOB_NATIVE_AD_UNIT_ID", "\"ca-app-pub-3940256099942544/2247696110\"")
        }
        release {
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
            buildConfigField("String", "ADMOB_BANNER_AD_UNIT_ID", "\"ca-app-pub-9608341092298145/6380345478\"")
            buildConfigField("String", "ADMOB_NATIVE_AD_UNIT_ID", "\"ca-app-pub-9608341092298145/9144616320\"")
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
