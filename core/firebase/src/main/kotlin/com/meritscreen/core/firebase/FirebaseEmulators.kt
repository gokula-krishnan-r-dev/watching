package com.meritscreen.core.firebase

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions

/**
 * Routes Firebase clients to the local Emulator Suite.
 * Call once after [com.google.firebase.FirebaseApp.initializeApp], before any SDK use.
 */
object FirebaseEmulators {
    fun connect(host: String = FirebaseEmulatorConfig.HOST) {
        FirebaseAuth.getInstance().useEmulator(host, FirebaseEmulatorConfig.AUTH_PORT)
        FirebaseFirestore.getInstance().useEmulator(host, FirebaseEmulatorConfig.FIRESTORE_PORT)
        FirebaseFunctions.getInstance("us-central1")
            .useEmulator(host, FirebaseEmulatorConfig.FUNCTIONS_PORT)
        Log.i("FirebaseEmulators", "Connected auth/firestore/functions @ $host")
    }
}
