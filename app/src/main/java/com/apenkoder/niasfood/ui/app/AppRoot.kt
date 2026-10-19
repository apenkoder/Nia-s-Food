package com.apenkoder.niasfood.ui.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apenkoder.niasfood.R
import com.apenkoder.niasfood.ui.components.LoadingState
import com.apenkoder.niasfood.ui.login.LoginScreen
import com.apenkoder.niasfood.ui.navigation.MainShell
import com.apenkoder.niasfood.ui.setup.SetupScreen

@Composable
fun AppRoot() {
    val vm = containerViewModel<AppViewModel>("app") { c, _ -> AppViewModel(c) }
    val boot by vm.boot.collectAsStateWithLifecycle()
    val session by vm.session.collectAsStateWithLifecycle()
    val current = session

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when {
            boot == Boot.LOADING -> LoadingState(stringResource(R.string.preparing))
            boot == Boot.NEEDS_SETUP -> SetupScreen(onDone = vm::setupDone)
            current == null -> LoginScreen()
            else -> MainShell(session = current, onLock = vm::lock)
        }
    }
}
