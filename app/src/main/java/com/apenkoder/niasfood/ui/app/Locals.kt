package com.apenkoder.niasfood.ui.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.apenkoder.niasfood.di.AppContainer

val LocalContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer was not provided")
}

/** Creates a ViewModel that receives the [AppContainer] and a [SavedStateHandle]. [key] must be unique per screen and user. */
@Composable
inline fun <reified VM : ViewModel> containerViewModel(
    key: String,
    crossinline create: (AppContainer, SavedStateHandle) -> VM
): VM {
    val container = LocalContainer.current
    return viewModel<VM>(
        key = key,
        factory = viewModelFactory {
            initializer<VM> { create(container, createSavedStateHandle()) }
        }
    )
}
