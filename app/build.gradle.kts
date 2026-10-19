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
        versionCode = 1
        versionName = "0.1"
        // On-device gesture check (app/src/androidTest); a plain Instrumentation, no test libraries.
        testInstrumentationRunner = "dev.vulcanlauncher.DeviceCheck"
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
