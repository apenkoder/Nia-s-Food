package com.apenkoder.niasfood.data.repo

import com.apenkoder.niasfood.core.AppClock
import com.apenkoder.niasfood.data.local.AuditDao
import com.apenkoder.niasfood.data.local.AuditEntity
import com.apenkoder.niasfood.data.local.StaffEntity
import com.apenkoder.niasfood.domain.Session

/** Append-only audit trail. Never pass PINs, hashes or full payment codes as [reason]. */
class Audit(
  private val dao: AuditDao,
  private val clock: AppClock,
) {
  suspend fun log(
    session: Session,
    action: String,
    entityType: String,
    entityId: String,
    reason: String? = null,
    outletId: String? = session.outletId,
  ) {
    dao.insert(
      AuditEntity(
        outletId = outletId,
        actorId = session.staffId,
        actorName = session.name,
        action = action,
        entityType = entityType,
        entityId = entityId,
        reason = reason,
        at = clock.nowMillis(),
      ),
    )
  }

  suspend fun logStaff(
    staff: StaffEntity,
    action: String,
    entityType: String,
    entityId: String,
    reason: String? = null,
  ) {
    dao.insert(
      AuditEntity(
        outletId = staff.outletId,
        actorId = staff.id,
        actorName = staff.name,
        action = action,
        entityType = entityType,
        entityId = entityId,
        reason = reason,
        at = clock.nowMillis(),
      ),
    )
  }
}
