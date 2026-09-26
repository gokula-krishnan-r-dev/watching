plugins {
    id("meritscreen.android.library")
}

android {
    namespace = "com.meritscreen.core.testing"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:network"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.test)
    implementation(libs.junit)
}
