plugins {
    id("meritscreen.android.library")
    id("meritscreen.android.hilt")
}

android {
    namespace = "com.meritscreen.core.database"
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":core:common"))
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
}
