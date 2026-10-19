package com.apenkoder.niasfood.data.repo

import com.apenkoder.niasfood.core.AppClock
import com.apenkoder.niasfood.core.AppError
import com.apenkoder.niasfood.core.AppException
import com.apenkoder.niasfood.data.local.NiasDatabase
import com.apenkoder.niasfood.data.local.OutletEntity
import com.apenkoder.niasfood.data.local.ShiftEntity
import com.apenkoder.niasfood.data.security.SessionManager
import com.apenkoder.niasfood.domain.Permission
import com.apenkoder.niasfood.domain.Permissions
import com.apenkoder.niasfood.domain.Role
import com.apenkoder.niasfood.domain.Session
import com.apenkoder.niasfood.domain.Verification

class ZReportData(
  val shift: ShiftEntity,
  val outletName: String,
  val summary: Summary,
  val expectedCashCents: Long,
  val unverifiedCount: Int,
  val flaggedCount: Int,
)

class ReportService(
  private val db: NiasDatabase,
  private val clock: AppClock,
  private val audit: Audit,
  private val sessions: SessionManager,
) {
  private fun requireView(
    s: Session,
    outletId: String?,
  ) {
    if (!Permissions.has(s.role, Permission.VIEW_FINANCIALS)) throw AppException(AppError.NOT_ALLOWED)
    if (outletId == null) {
      if (s.role != Role.OWNER) throw AppException(AppError.NOT_ALLOWED)
    } else if (!Permissions.canActInOutlet(s, outletId)) {
      throw AppException(AppError.NOT_ALLOWED)
    }
  }

  suspend fun outlets(): List<OutletEntity> = db.outletDao().active()

  /** Totals for one outlet (or all outlets when [outletId] is null and the user is the owner). */
  suspend fun summary(
    outletId: String?,
    from: String,
    to: String,
  ): Summary {
    requireView(sessions.requireSession(), outletId)
    return Aggregator.summarize(
      orders = db.orderDao().inRange(from, to, outletId),
      payments = db.paymentDao().inRange(from, to, outletId),
      lines = db.orderDao().linesInRange(from, to, outletId),
      expenses = db.expenseDao().inRange(from, to, outletId),
    )
  }

  suspend fun daily(
    outletId: String?,
    from: String,
    to: String,
  ): Map<String, Summary> {
    requireView(sessions.requireSession(), outletId)
    return Aggregator.summarizeByDate(
      orders = db.orderDao().inRange(from, to, outletId),
      payments = db.paymentDao().inRange(from, to, outletId),
      lines = db.orderDao().linesInRange(from, to, outletId),
      expenses = db.expenseDao().inRange(from, to, outletId),
    )
  }

  suspend fun closedShifts(outletId: String?): List<ShiftEntity> {
    requireView(sessions.requireSession(), outletId)
    return db.shiftDao().closed(outletId)
  }

  suspend fun zReport(shiftId: String): ZReportData {
    val s = sessions.requireSession()
    val shift = db.shiftDao().byId(shiftId) ?: throw AppException(AppError.NOT_FOUND)
    requireView(s, shift.outletId)
    val outlet = db.outletDao().byId(shift.outletId)
    val summary =
      Aggregator.summarize(
        orders = db.orderDao().forShift(shiftId),
        payments = db.paymentDao().forShift(shiftId),
        lines = db.orderDao().linesForShift(shiftId),
        expenses = db.expenseDao().forShift(shiftId),
      )
    val mm = db.paymentDao().mobileMoneyForShift(shiftId)
    val expected =
      shift.expectedCashCents
        ?: (shift.openingFloatCents + db.paymentDao().cashTotalForShift(shiftId) - db.expenseDao().cashFromTillForShift(shiftId))
    return ZReportData(
      shift = shift,
      outletName = outlet?.name.orEmpty(),
      summary = summary,
      expectedCashCents = expected,
      unverifiedCount = mm.count { it.verification == Verification.UNVERIFIED },
      flaggedCount = mm.count { it.verification == Verification.FLAGGED },
    )
  }

  suspend fun recordExport(shiftId: String) {
    val s = sessions.requireSession()
    val shift = db.shiftDao().byId(shiftId) ?: throw AppException(AppError.NOT_FOUND)
    requireView(s, shift.outletId)
    audit.log(s, "report_exported", "shift", shiftId, null, shift.outletId)
  }
}
