plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val sharedDir = rootProject.file("../shared")

android {
    namespace = "com.gymbuddy.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.gymbuddy.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "PRO_SWITCH", "true")
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("boolean", "PRO_SWITCH", "false")
        }
        // For trying the app on a phone: not debuggable, so Compose runs at release speed
        // (debug builds scroll noticeably worse), but signed with the debug key so it installs
        // over a debug build without losing data. Keeps the "Pro without purchase" switch.
        create("staging") {
            initWith(getByName("debug"))
            isDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            buildConfigField("boolean", "PRO_SWITCH", "true")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // The built-in exercise library lives in the repo-level shared/ folder so both apps use the same data.
    sourceSets["main"].assets.srcDir(file("build/generated/sharedAssets"))

    testOptions {
        unitTests.all {
            it.systemProperty("gymbuddy.sharedDir", sharedDir.absolutePath)
        }
    }
}

val copySharedAssets by tasks.registering(Copy::class) {
    from(sharedDir) { include("exercises.json", "supabase.json") }
    into(layout.buildDirectory.dir("generated/sharedAssets"))
}
tasks.named("preBuild") { dependsOn(copySharedAssets) }

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    // Installs the baseline profiles Compose ships with, so lists scroll smoothly from the first launch.
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.activity.compose)
    implementation(libs.health.connect)
    implementation(libs.play.billing)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.google.id)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    implementation(project(":watchlink"))
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
