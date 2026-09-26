plugins {
    id("meritscreen.android.library")
    id("meritscreen.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.meritscreen.core.common"
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.timber)
}
