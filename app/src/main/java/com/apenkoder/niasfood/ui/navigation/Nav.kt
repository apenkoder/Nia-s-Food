package com.apenkoder.niasfood.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.apenkoder.niasfood.BuildConfig
import com.apenkoder.niasfood.R
import com.apenkoder.niasfood.di.AppContainer
import com.apenkoder.niasfood.domain.Destination
import com.apenkoder.niasfood.domain.Permissions
import com.apenkoder.niasfood.domain.Role
import com.apenkoder.niasfood.domain.Session
import com.apenkoder.niasfood.ui.app.containerViewModel
import com.apenkoder.niasfood.ui.components.DemoBanner
import com.apenkoder.niasfood.ui.components.StatusChip
import com.apenkoder.niasfood.ui.dashboard.DashboardScreen
import com.apenkoder.niasfood.ui.expenses.ExpensesScreen
import com.apenkoder.niasfood.ui.orders.OrdersScreen
import com.apenkoder.niasfood.ui.pos.PosScreen
import com.apenkoder.niasfood.ui.reports.ReportsScreen
import com.apenkoder.niasfood.ui.settings.SettingsScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ShellViewModel(private val c: AppContainer, session: Session) : ViewModel() {
    private val _outletName = MutableStateFlow<String?>(null)
    val outletName: StateFlow<String?> = _outletName
    private val _shiftOpen = MutableStateFlow<Boolean?>(null)
    val shiftOpen: StateFlow<Boolean?> = _shiftOpen

    init {
        val outletId = session.outletId
        if (outletId != null) {
            viewModelScope.launch { _outletName.value = c.db.outletDao().byId(outletId)?.name }
            viewModelScope.launch { c.sales.observeOpenShift(outletId).collect { _shiftOpen.value = it != null } }
        }
    }
}

private fun Destination.icon(): ImageVector = when (this) {
    Destination.POS -> Icons.Default.ShoppingCart
    Destination.ORDERS -> Icons.Default.List
    Destination.EXPENSES -> Icons.Default.AddCircle
    Destination.REPORTS -> Icons.Default.DateRange
    Destination.DASHBOARD -> Icons.Default.Home
    Destination.SETTINGS -> Icons.Default.Settings
}

private fun Destination.labelRes(): Int = when (this) {
    Destination.POS -> R.string.nav_pos
    Destination.ORDERS -> R.string.nav_orders
    Destination.EXPENSES -> R.string.nav_expenses
    Destination.REPORTS -> R.string.nav_reports
    Destination.DASHBOARD -> R.string.nav_dashboard
    Destination.SETTINGS -> R.string.nav_settings
}

@Composable
private fun roleLabel(role: Role): String = stringResource(
    when (role) {
        Role.SERVER -> R.string.role_server
        Role.MANAGER -> R.string.role_manager
        Role.OWNER -> R.string.role_owner
    }
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainShell(session: Session, onLock: () -> Unit) {
    val shell = containerViewModel<ShellViewModel>("shell-${session.staffId}") { c, _ -> ShellViewModel(c, session) }
    val outletName by shell.outletName.collectAsStateWithLifecycle()
    val shiftOpen by shell.shiftOpen.collectAsStateWithLifecycle()

    val nav = rememberNavController()
    val tabs = Permissions.tabsFor(session.role)
    val start = Permissions.startFor(session.role)
    val backEntry by nav.currentBackStackEntryAsState()
    val currentRoute = backEntry?.destination?.route

    fun go(route: String) {
        nav.navigate(route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // System back from a non-start tab returns to the start tab instead of leaving the app.
    BackHandler(enabled = currentRoute != null && currentRoute != start.route) { go(start.route) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = stringResource(
                                    R.string.signed_in_as,
                                    session.name,
                                    roleLabel(session.role),
                                    outletName ?: stringResource(R.string.all_outlets)
                                ),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    },
                    actions = {
                        val open = shiftOpen
                        if (open != null) {
                            StatusChip(
                                text = stringResource(if (open) R.string.shift_open else R.string.shift_closed),
                                icon = if (open) Icons.Default.CheckCircle else Icons.Default.Warning,
                                positive = open
                            )
                        }
                        IconButton(onClick = onLock) {
                            Icon(Icons.Default.Lock, contentDescription = stringResource(R.string.lock))
                        }
                    }
                )
                if (BuildConfig.DEMO_SEED) DemoBanner()
            }
        },
        bottomBar = {
            NavigationBar {
                tabs.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = { go(destination.route) },
                        icon = { Icon(destination.icon(), contentDescription = null) },
                        label = { Text(stringResource(destination.labelRes())) },
                        alwaysShowLabel = true
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = start.route,
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
        ) {
            // Only destinations the role may open are registered, so a route outside the role cannot be reached.
            tabs.forEach { destination ->
                composable(destination.route) {
                    when (destination) {
                        Destination.POS -> PosScreen(session)
                        Destination.ORDERS -> OrdersScreen(session, onResume = { go(Destination.POS.route) })
                        Destination.EXPENSES -> ExpensesScreen(session)
                        Destination.REPORTS -> ReportsScreen(session)
                        Destination.DASHBOARD -> DashboardScreen(session)
                        Destination.SETTINGS -> SettingsScreen(session)
                    }
                }
            }
        }
    }
}
