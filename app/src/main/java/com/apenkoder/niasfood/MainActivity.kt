package com.apenkoder.niasfood

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.apenkoder.niasfood.ui.app.AppRoot
import com.apenkoder.niasfood.ui.app.LocalContainer
import com.apenkoder.niasfood.ui.splash.SplashScreen
import com.apenkoder.niasfood.ui.theme.NiasFoodTheme

class MainActivity : ComponentActivity() {

    private val container get() = (application as NiasFoodApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Release builds block screenshots and the recents thumbnail. Debug builds stay capturable for review.
        if (!BuildConfig.DEBUG) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }
        enableEdgeToEdge()
        setContent {
            var splashFinished by rememberSaveable { mutableStateOf(false) }
            NiasFoodTheme {
                CompositionLocalProvider(LocalContainer provides container) {
                    Box(Modifier.fillMaxSize()) {
                        AppRoot()
                        if (!splashFinished) {
                            SplashScreen(onFinished = { splashFinished = true })
                        }
                    }
                }
            }
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        container.session.touch()
    }

    override fun onStart() {
        super.onStart()
        container.session.onForeground()
    }

    override fun onStop() {
        container.session.onBackground()
        super.onStop()
    }
}
