package com.apenkoder.niasfood.di

import android.content.Context
import com.apenkoder.niasfood.core.AppClock
import com.apenkoder.niasfood.core.SystemAppClock
import com.apenkoder.niasfood.data.local.DatabaseFactory
import com.apenkoder.niasfood.data.local.NiasDatabase
import com.apenkoder.niasfood.data.repo.Audit
import com.apenkoder.niasfood.data.repo.AuthService
import com.apenkoder.niasfood.data.repo.MenuService
import com.apenkoder.niasfood.data.repo.ReportService
import com.apenkoder.niasfood.data.repo.SalesService
import com.apenkoder.niasfood.data.security.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow

/** Manual dependency container (no Hilt/Dagger, to keep builds light). */
class AppContainer(
  context: Context,
) {
  val appContext: Context = context.applicationContext
  val clock: AppClock = SystemAppClock
  val db: NiasDatabase = DatabaseFactory.create(appContext)
  val session = SessionManager(clock)
  val audit = Audit(db.auditDao(), clock)
  val auth = AuthService(db, clock, audit, session)
  val sales = SalesService(db, clock, audit, session)
  val menu = MenuService(db, clock, audit, session)
  val reports = ReportService(db, clock, audit, session)

  /** A held order picked on the Orders screen, waiting to be loaded by the POS screen. */
  val pendingHeldOrderId = MutableStateFlow<String?>(null)
}
