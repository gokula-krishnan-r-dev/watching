plugins {
    id("meritscreen.android.feature")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.meritscreen.feature.child"
}

dependencies {
    implementation(project(":core:firebase"))
    implementation(project(":core:security"))
    implementation(project(":core:database"))
    implementation(project(":core:network"))
    implementation(project(":core:analytics"))
    implementation(project(":features:launcher"))
    implementation(project(":features:applications"))
    implementation(project(":features:devices"))
    // Shared-tablet: pair an additional sibling profile from Parent menu (PIN-gated).
    implementation(project(":features:authentication"))
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    testImplementation(project(":core:testing"))
}
