package com.meritscreen.core.firebase

/**
 * Points Firebase Auth / Firestore / Functions at the local Emulator Suite.
 * Host `10.0.2.2` is the Android emulator's alias for the host machine loopback.
 * Never enable in release builds.
 */
object FirebaseEmulatorConfig {
    const val HOST = "10.0.2.2"
    const val AUTH_PORT = 9099
    const val FIRESTORE_PORT = 8080
    const val FUNCTIONS_PORT = 5001
    const val STORAGE_PORT = 9199
}
