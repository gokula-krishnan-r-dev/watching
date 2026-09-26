plugins {
    id("meritscreen.android.library")
    id("meritscreen.android.hilt")
}

android {
    namespace = "com.meritscreen.core.firebase"
}

dependencies {
    implementation(project(":core:common"))
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.functions)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.config)
    implementation(libs.firebase.appcheck)
    implementation(libs.firebase.appcheck.playintegrity)
    implementation(libs.firebase.ai)
    implementation(libs.kotlinx.coroutines.play.services)
}
