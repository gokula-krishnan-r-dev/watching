plugins {
    id("meritscreen.android.feature")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.meritscreen.feature.devices"
}

dependencies {
    implementation(project(":core:firebase"))
    implementation(project(":core:security"))
    implementation(project(":core:network"))
    implementation(project(":core:database"))
    implementation(project(":core:analytics"))
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    testImplementation(project(":core:testing"))
}
