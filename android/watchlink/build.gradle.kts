// Phone ↔ watch protocol (shared/watch-protocol.md), used by the phone app and the Wear OS app.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    api(libs.kotlinx.serialization.json)
}
