plugins {
    id("meritscreen.android.feature")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.meritscreen.feature.launcher"
}

dependencies {
    implementation(libs.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    testImplementation(project(":core:testing"))
}
