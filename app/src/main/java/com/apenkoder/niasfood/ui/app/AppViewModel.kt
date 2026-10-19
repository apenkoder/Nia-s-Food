package com.apenkoder.niasfood.ui.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apenkoder.niasfood.data.demo.DemoSeeder
import com.apenkoder.niasfood.di.AppContainer
import com.apenkoder.niasfood.domain.Session
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class Boot { LOADING, NEEDS_SETUP, READY }

class AppViewModel(
  private val c: AppContainer,
) : ViewModel() {
  private val _boot = MutableStateFlow(Boot.LOADING)
  val boot: StateFlow<Boot> = _boot
  val session: StateFlow<Session?> = c.session.current

  init {
    viewModelScope.launch {
      // Debug builds seed demo data on first run. The release DemoSeeder does nothing.
      DemoSeeder.seedIfNeeded(c)
      _boot.value = if (c.db.outletDao().count() == 0) Boot.NEEDS_SETUP else Boot.READY
    }
    viewModelScope.launch {
      while (true) {
        delay(10_000L)
        c.session.checkIdle()
      }
    }
  }

  fun setupDone() {
    _boot.value = Boot.READY
  }

  fun lock() = c.session.lock()
}
