import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.pulk"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.pulk.keyboard"
        minSdk = 26
        targetSdk = 34
        versionCode = 14
        versionName = "0.9.7"
    }
    // the release key lives in keys/ (not in git); without it a release build is left unsigned
    val keyProps = rootProject.file("keys/keystore.properties").takeIf { it.exists() }?.let { f -> Properties().apply { f.inputStream().use { load(it) } } }
    signingConfigs {
        if (keyProps != null) create("release") {
            storeFile = file(keyProps.getProperty("storeFile"))
            storePassword = keyProps.getProperty("storePassword")
            keyAlias = keyProps.getProperty("keyAlias")
            keyPassword = keyProps.getProperty("keyPassword")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            if (keyProps != null) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests { isIncludeAndroidResources = true } }
}
// no dependencies on purpose: the keyboard uses only the Android framework

dependencies {
    // only for the checks that run on the computer (they draw the keyboard and replay touches)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.12.2")
}
