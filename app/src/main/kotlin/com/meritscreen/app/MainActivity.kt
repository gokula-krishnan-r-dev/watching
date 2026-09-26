package com.meritscreen.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.meritscreen.app.navigation.MeritScreenApp
import com.meritscreen.core.common.session.DeviceRole
import com.meritscreen.core.ui.theme.MeritScreenTheme
import com.meritscreen.feature.child.service.ChildTimeLimitService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition { viewModel.keepSplash.value }
        enableEdgeToEdge()

        lifecycleScope.launch {
            viewModel.role.collect { role ->
                if (role == DeviceRole.Child) {
                    ChildTimeLimitService.start(this@MainActivity)
                }
            }
        }

        setContent {
            MeritScreenTheme {
                MeritScreenApp()
            }
        }
    }
}
