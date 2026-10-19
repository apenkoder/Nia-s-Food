package com.apenkoder.niasfood.data.security

import com.apenkoder.niasfood.core.AppClock
import com.apenkoder.niasfood.core.AppError
import com.apenkoder.niasfood.core.AppException
import com.apenkoder.niasfood.data.local.StaffEntity
import com.apenkoder.niasfood.domain.Permission
import com.apenkoder.niasfood.domain.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** In-memory session only. Idle for 2 minutes, or 30 seconds in the background, locks the app. */
class SessionManager(
  private val clock: AppClock,
) {
  private val _current = MutableStateFlow<Session?>(null)
  val current: StateFlow<Session?> = _current

  @Volatile private var lastTouch: Long = clock.nowMillis()

  @Volatile private var backgroundedAt: Long? = null

  private data class ApprovalRecord(
    val approverId: String,
    val sessionId: String,
    val outletId: String,
    val permission: Permission,
    val expiresAt: Long,
  ) {
    fun matches(
      now: Long,
      activeSessionId: String?,
      expectedOutletId: String,
      expectedPermission: Permission,
    ): Boolean =
      expiresAt > now &&
        sessionId == activeSessionId &&
        outletId == expectedOutletId &&
        permission == expectedPermission
  }

  private val approvals = ConcurrentHashMap<String, ApprovalRecord>()

  @Volatile private var sessionId: String? = null

  fun start(session: Session) {
    approvals.clear()
    sessionId = UUID.randomUUID().toString()
    lastTouch = clock.nowMillis()
    _current.value = session
  }

  fun requireSession(): Session = _current.value ?: throw AppException(AppError.NOT_ALLOWED)

  fun lock() {
    approvals.clear()
    sessionId = null
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

  internal fun issueApproval(
    requester: Session,
    approver: StaffEntity,
    outletId: String,
    permission: Permission,
  ): ApprovalGrant {
    if (_current.value != requester) throw AppException(AppError.NOT_ALLOWED)
    approvals.entries.removeIf { it.value.expiresAt <= clock.nowMillis() }
    val token = UUID.randomUUID().toString()
    approvals[token] =
      ApprovalRecord(
        approverId = approver.id,
        sessionId = sessionId ?: throw AppException(AppError.NOT_ALLOWED),
        outletId = outletId,
        permission = permission,
        expiresAt = clock.nowMillis() + APPROVAL_TTL_MS,
      )
    return ApprovalGrant(token)
  }

  /** Atomically consumes a PIN approval tied to its requester, outlet, and action. */
  internal fun consumeApproval(
    grant: ApprovalGrant?,
    outletId: String,
    permission: Permission,
  ): String? {
    val record = grant?.let { approvals.remove(it.token) }
    val activeSessionId = if (_current.value == null) null else sessionId
    return record
      ?.takeIf { it.matches(clock.nowMillis(), activeSessionId, outletId, permission) }
      ?.approverId
  }

  companion object {
    const val IDLE_LIMIT_MS = 2L * 60L * 1000L
    const val BACKGROUND_LIMIT_MS = 30L * 1000L
    const val APPROVAL_TTL_MS = 2L * 60L * 1000L
  }
}

/** Opaque, in-memory proof returned only after a manager PIN has been verified. */
class ApprovalGrant internal constructor(
  internal val token: String,
)
