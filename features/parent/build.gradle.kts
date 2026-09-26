plugins {
    id("meritscreen.android.feature")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.meritscreen.feature.parent"
}

dependencies {
    implementation(project(":core:firebase"))
    implementation(project(":core:security"))
    implementation(project(":core:network"))
    implementation(project(":core:analytics"))
    implementation(project(":features:authentication"))
    implementation(project(":features:onboarding"))
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    testImplementation(project(":core:testing"))
}
