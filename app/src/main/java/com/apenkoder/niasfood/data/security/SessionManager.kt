package com.apenkoder.niasfood.data.security

import com.apenkoder.niasfood.core.AppClock
import com.apenkoder.niasfood.core.AppError
import com.apenkoder.niasfood.core.AppException
import com.apenkoder.niasfood.domain.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** In-memory session only. Idle for 2 minutes, or 30 seconds in the background, locks the app. */
class SessionManager(private val clock: AppClock) {
    private val _current = MutableStateFlow<Session?>(null)
    val current: StateFlow<Session?> = _current

    @Volatile private var lastTouch: Long = clock.nowMillis()
    @Volatile private var backgroundedAt: Long? = null

    fun start(session: Session) {
        lastTouch = clock.nowMillis()
        _current.value = session
    }

    fun requireSession(): Session = _current.value ?: throw AppException(AppError.NOT_ALLOWED)

    fun lock() {
        _current.value = null
    }

    fun touch() {
        lastTouch = clock.nowMillis()
    }

    fun checkIdle() {
        if (_current.value != null && clock.nowMillis() - lastTouch > IDLE_LIMIT_MS) lock()
    }

    fun onBackground() {
        backgroundedAt = clock.nowMillis()
    }

    fun onForeground() {
        val since = backgroundedAt
        backgroundedAt = null
        if (since != null && _current.value != null && clock.nowMillis() - since > BACKGROUND_LIMIT_MS) lock()
    }

    companion object {
        const val IDLE_LIMIT_MS = 2L * 60L * 1000L
        const val BACKGROUND_LIMIT_MS = 30L * 1000L
    }
}
