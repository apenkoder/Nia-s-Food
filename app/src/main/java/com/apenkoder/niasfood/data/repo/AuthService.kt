package com.apenkoder.niasfood.data.repo

import androidx.room.withTransaction
import com.apenkoder.niasfood.core.AppClock
import com.apenkoder.niasfood.core.AppError
import com.apenkoder.niasfood.core.AppException
import com.apenkoder.niasfood.data.local.CategoryEntity
import com.apenkoder.niasfood.data.local.NiasDatabase
import com.apenkoder.niasfood.data.local.OutletEntity
import com.apenkoder.niasfood.data.local.StaffEntity
import com.apenkoder.niasfood.data.security.PinHasher
import com.apenkoder.niasfood.data.security.SessionManager
import com.apenkoder.niasfood.domain.LockoutPolicy
import com.apenkoder.niasfood.domain.Permission
import com.apenkoder.niasfood.domain.Permissions
import com.apenkoder.niasfood.domain.PinRules
import com.apenkoder.niasfood.domain.Role
import com.apenkoder.niasfood.domain.Session
import java.util.UUID

sealed interface LoginResult {
    data class Success(val session: Session) : LoginResult
    data class WrongPin(val attemptsLeft: Int) : LoginResult
    data class Locked(val remainingMs: Long) : LoginResult
    data object Unavailable : LoginResult
}

sealed interface ApprovalResult {
    data class Approved(val approver: StaffEntity) : ApprovalResult
    data class WrongPin(val attemptsLeft: Int) : ApprovalResult
    data class Locked(val remainingMs: Long) : ApprovalResult
    data object NotAllowed : ApprovalResult
}

class AuthService(
    private val db: NiasDatabase,
    private val clock: AppClock,
    private val audit: Audit,
    private val sessions: SessionManager
) {
    private sealed interface Check {
        data object Ok : Check
        data class Wrong(val attemptsLeft: Int) : Check
        data class Locked(val remainingMs: Long) : Check
    }

    suspend fun profiles(): List<StaffEntity> =
        db.staffDao().active().sortedWith(compareBy({ roleOrder(it.role) }, { it.name }))

    suspend fun outlets(): List<OutletEntity> = db.outletDao().active()

    suspend fun login(staffId: String, pin: String): LoginResult {
        val staff = db.staffDao().byId(staffId)
        if (staff == null || !staff.active) return LoginResult.Unavailable
        return when (val check = verify(staff, pin)) {
            is Check.Ok -> {
                val session = Session(staff.id, staff.name, staff.role, staff.outletId)
                audit.logStaff(staff, "login_success", "session", staff.id)
                sessions.start(session)
                LoginResult.Success(session)
            }
            is Check.Wrong -> LoginResult.WrongPin(check.attemptsLeft)
            is Check.Locked -> LoginResult.Locked(check.remainingMs)
        }
    }

    /** Manager/owner PIN approval for a sensitive action (discount, void). Same lockout rules as login. */
    suspend fun approvers(outletId: String): List<StaffEntity> =
        db.staffDao().approversFor(outletId).sortedWith(compareBy({ roleOrder(it.role) }, { it.name }))

    suspend fun approve(outletId: String, approverId: String, pin: String, permission: Permission): ApprovalResult {
        val staff = db.staffDao().byId(approverId)
        if (staff == null || !staff.active ||
            !Permissions.canApprove(staff.role, staff.outletId, outletId, permission)
        ) {
            return ApprovalResult.NotAllowed
        }
        return when (val check = verify(staff, pin)) {
            is Check.Ok -> ApprovalResult.Approved(staff)
            is Check.Wrong -> ApprovalResult.WrongPin(check.attemptsLeft)
            is Check.Locked -> ApprovalResult.Locked(check.remainingMs)
        }
    }

    private suspend fun verify(staff: StaffEntity, pin: String): Check {
        val now = clock.nowMillis()
        val remaining = LockoutPolicy.remainingMs(staff.lockoutUntil, now)
        if (remaining > 0L) {
            audit.logStaff(staff, "login_blocked", "staff", staff.id)
            return Check.Locked(remaining)
        }
        val ok = PinHasher.verifyAsync(pin, staff.pinHash)
        if (ok) {
            if (staff.failedAttempts != 0 || staff.lockoutUntil != null) {
                db.staffDao().upsert(staff.copy(failedAttempts = 0, lockoutUntil = null))
            }
            return Check.Ok
        }
        val failure = LockoutPolicy.onFailure(staff.failedAttempts, now)
        db.staffDao().upsert(staff.copy(failedAttempts = failure.failures, lockoutUntil = failure.lockUntil))
        val lockUntil = failure.lockUntil
        return if (lockUntil != null) {
            audit.logStaff(staff, "lockout", "staff", staff.id, "${failure.failures} wrong PINs")
            Check.Locked(lockUntil - now)
        } else {
            audit.logStaff(staff, "login_failure", "staff", staff.id)
            Check.Wrong(LockoutPolicy.attemptsLeft(failure.failures))
        }
    }

    // ---------- staff management ----------

    suspend fun staffList(): List<StaffEntity> {
        val s = sessions.requireSession()
        if (!Permissions.has(s.role, Permission.MANAGE_STAFF)) throw AppException(AppError.NOT_ALLOWED)
        val all = db.staffDao().active()
        val visible = if (s.role == Role.OWNER) all else all.filter {
            it.outletId == s.outletId && Permissions.canManageRole(s.role, it.role)
        }
        return visible.sortedWith(compareBy({ roleOrder(it.role) }, { it.name }))
    }

    suspend fun addStaff(name: String, role: Role, outletId: String?, pin: String): StaffEntity {
        val s = sessions.requireSession()
        if (!Permissions.has(s.role, Permission.MANAGE_STAFF) || !Permissions.canManageRole(s.role, role)) {
            throw AppException(AppError.NOT_ALLOWED)
        }
        if (name.isBlank()) throw AppException(AppError.NAME_REQUIRED)
        val targetOutlet = if (role == Role.OWNER) null else (if (s.role == Role.OWNER) outletId else s.outletId)
        if (role != Role.OWNER && targetOutlet == null) throw AppException(AppError.NOT_FOUND)
        if (!PinRules.isWellFormed(pin, role)) throw AppException(AppError.BAD_PIN_FORMAT)
        if (PinRules.isWeak(pin)) throw AppException(AppError.WEAK_PIN)
        val hash = PinHasher.hashAsync(pin)
        val staff = StaffEntity(
            id = UUID.randomUUID().toString(), outletId = targetOutlet, name = name.trim(),
            role = role, pinHash = hash
        )
        db.withTransaction {
            db.staffDao().upsert(staff)
            audit.log(s, "staff_added", "staff", staff.id, "role=${role.name}", outletId = targetOutlet)
        }
        return staff
    }

    suspend fun resetPin(targetId: String, newPin: String) {
        val s = sessions.requireSession()
        val target = db.staffDao().byId(targetId) ?: throw AppException(AppError.NOT_FOUND)
        if (!Permissions.has(s.role, Permission.MANAGE_STAFF) || !Permissions.canManageRole(s.role, target.role)) {
            throw AppException(AppError.NOT_ALLOWED)
        }
        if (s.role == Role.MANAGER && target.outletId != s.outletId) throw AppException(AppError.NOT_ALLOWED)
        if (!PinRules.isWellFormed(newPin, target.role)) throw AppException(AppError.BAD_PIN_FORMAT)
        if (PinRules.isWeak(newPin)) throw AppException(AppError.WEAK_PIN)
        val hash = PinHasher.hashAsync(newPin)
        db.withTransaction {
            db.staffDao().upsert(target.copy(pinHash = hash, failedAttempts = 0, lockoutUntil = null))
            audit.log(s, "pin_reset", "staff", target.id, outletId = target.outletId)
        }
    }

    suspend fun setActive(targetId: String, active: Boolean) {
        val s = sessions.requireSession()
        val target = db.staffDao().byId(targetId) ?: throw AppException(AppError.NOT_FOUND)
        if (!Permissions.has(s.role, Permission.MANAGE_STAFF) || !Permissions.canManageRole(s.role, target.role)) {
            throw AppException(AppError.NOT_ALLOWED)
        }
        if (s.role == Role.MANAGER && target.outletId != s.outletId) throw AppException(AppError.NOT_ALLOWED)
        if (target.id == s.staffId) throw AppException(AppError.NOT_ALLOWED)
        db.withTransaction {
            db.staffDao().upsert(target.copy(active = active))
            audit.log(s, if (active) "staff_activated" else "staff_deactivated", "staff", target.id, outletId = target.outletId)
        }
    }

    // ---------- first-run setup ----------

    class OutletInput(val name: String, val area: String, val openMinutes: Int, val closeMinutes: Int)

    /** Creates the owner, outlets and starter menu categories. Allowed only on an empty database. */
    suspend fun completeSetup(ownerName: String, ownerPin: String, outlets: List<OutletInput>) {
        if (db.outletDao().count() > 0) throw AppException(AppError.NOT_ALLOWED)
        if (ownerName.isBlank() || outlets.isEmpty() || outlets.any { it.name.isBlank() }) {
            throw AppException(AppError.NAME_REQUIRED)
        }
        if (!PinRules.isWellFormed(ownerPin, Role.OWNER)) throw AppException(AppError.BAD_PIN_FORMAT)
        if (PinRules.isWeak(ownerPin)) throw AppException(AppError.WEAK_PIN)
        val hash = PinHasher.hashAsync(ownerPin)
        val owner = StaffEntity(UUID.randomUUID().toString(), null, ownerName.trim(), Role.OWNER, hash)
        db.withTransaction {
            db.outletDao().upsertAll(
                outlets.map {
                    OutletEntity(UUID.randomUUID().toString(), it.name.trim(), it.area.trim(), it.openMinutes, it.closeMinutes)
                }
            )
            db.staffDao().upsert(owner)
            db.menuDao().upsertCategories(
                listOf("Rice & Mains", "Stews & Sides", "Snacks", "Drinks").mapIndexed { index, name ->
                    CategoryEntity(UUID.randomUUID().toString(), name, index + 1)
                }
            )
            audit.logStaff(owner, "setup_completed", "staff", owner.id)
        }
    }

    private fun roleOrder(role: Role): Int = when (role) {
        Role.OWNER -> 0
        Role.MANAGER -> 1
        Role.SERVER -> 2
    }
}
