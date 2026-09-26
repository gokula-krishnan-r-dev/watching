plugins {
    id("meritscreen.android.feature")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.meritscreen.feature.applications"
}

dependencies {
    implementation(project(":core:database"))
    implementation(project(":core:firebase"))
    implementation(project(":core:security"))
    implementation(project(":core:network"))
    implementation(project(":core:analytics"))
    testImplementation(project(":core:testing"))
}
