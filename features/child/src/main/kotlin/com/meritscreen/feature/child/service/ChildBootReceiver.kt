package com.meritscreen.feature.child.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.meritscreen.core.security.pairing.ChildPairingStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Re-starts ChildTimeLimitService after device reboot to maintain 24/7 background screen time
 * monitoring and prevent children from bypassing limits by restarting the phone.
 */
@AndroidEntryPoint
class ChildBootReceiver : BroadcastReceiver() {

    @Inject lateinit var pairingStore: ChildPairingStore

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        CoroutineScope(Dispatchers.IO).launch {
            if (pairingStore.get() != null) {
                ChildTimeLimitService.start(context)
            }
        }
    }
}
