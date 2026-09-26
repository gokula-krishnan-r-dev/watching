plugins {
    id("meritscreen.android.library")
    id("meritscreen.android.hilt")
}

android {
    namespace = "com.meritscreen.core.network"
}

dependencies {
    implementation(project(":core:common"))
    implementation(libs.kotlinx.coroutines.android)
}
