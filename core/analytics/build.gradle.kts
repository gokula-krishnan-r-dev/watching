plugins {
    id("meritscreen.android.library")
    id("meritscreen.android.hilt")
}

android {
    namespace = "com.meritscreen.core.analytics"
}

dependencies {
    implementation(project(":core:common"))
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.analytics)
    implementation(libs.timber)
}
