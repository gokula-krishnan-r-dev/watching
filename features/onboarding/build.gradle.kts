plugins {
    id("meritscreen.android.feature")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.meritscreen.feature.onboarding"
}

dependencies {
    implementation(project(":core:security"))
    implementation(project(":core:firebase"))
    implementation(project(":core:network"))
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.androidx.datastore)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    testImplementation(project(":core:testing"))
}
