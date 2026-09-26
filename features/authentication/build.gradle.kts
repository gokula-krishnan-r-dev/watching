plugins {
    id("meritscreen.android.feature")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.meritscreen.feature.authentication"
}

dependencies {
    implementation(project(":core:firebase"))
    implementation(project(":core:security"))
    implementation(project(":core:network"))
    implementation(project(":core:analytics"))
    implementation(project(":features:onboarding"))
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)
    implementation(libs.compose.material.icons.extended)
    testImplementation(project(":core:testing"))
}
