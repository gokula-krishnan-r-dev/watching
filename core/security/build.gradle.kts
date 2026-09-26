plugins {
    id("meritscreen.android.library")
    id("meritscreen.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.meritscreen.core.security"
}

dependencies {
    implementation(project(":core:common"))
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
}
