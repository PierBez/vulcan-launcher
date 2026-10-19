import java.util.Properties


plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.vulcanlauncher"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.vulcanlauncher"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.11"
        // On-device gesture check (app/src/androidTest); a plain Instrumentation, no test libraries.
        testInstrumentationRunner = "dev.vulcanlauncher.DeviceCheck"
    }

    // Release signing comes from keystore.properties (git-ignored, outside the repo's history). Without it,
    // assembleRelease still builds, unsigned.
    val keys = rootProject.file("keystore.properties").takeIf { it.exists() }
        ?.let { f -> Properties().apply { f.inputStream().use { load(it) } } }
    signingConfigs {
        if (keys != null) create("release") {
            storeFile = file(keys.getProperty("storeFile"))
            storePassword = keys.getProperty("storePassword")
            keyAlias = keys.getProperty("keyAlias")
            keyPassword = keys.getProperty("keyPassword")
        }
    }
    buildTypes {
        release { if (keys != null) signingConfig = signingConfigs.getByName("release") }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303") // real org.json for JVM tests; Android supplies its own at runtime
}
