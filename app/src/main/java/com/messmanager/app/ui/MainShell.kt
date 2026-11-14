@file:OptIn(ExperimentalMaterial3Api::class)

package com.messmanager.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.messmanager.app.data.BazarEntry
import com.messmanager.app.data.Expense
import com.messmanager.app.data.Member
import com.messmanager.app.data.Note

private data class Tab(val route: String, val label: String, val icon: ImageVector)
private val tabs = listOf(
    Tab("home", "Home", Icons.Default.Home), Tab("bazar", "Bazar", Icons.Default.ShoppingCart),
    Tab("expenses", "Expenses", Icons.Default.List), Tab("members", "Members", Icons.Default.Person),
    Tab("reports", "Reports", Icons.Default.Info),
)

private data class ExpenseReq(val e: Expense?, val amount: Long?)
private data class BazarReq(val e: BazarEntry?)
private data class MoneyReq(val kind: String, val member: Long?, val amount: Long?)
private data class MemberReq(val m: Member?)
private data class NoteReq(val n: Note?)

@Composable
fun MainShell(vm: AppVm) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val dest = backStack?.destination
    val topLevel = tabs.any { t -> dest?.hierarchy?.any { it.route == t.route } == true }

    var expenseReq by remember { mutableStateOf<ExpenseReq?>(null) }
    var bazarReq by remember { mutableStateOf<BazarReq?>(null) }
    var moneyReq by remember { mutableStateOf<MoneyReq?>(null) }
    var memberReq by remember { mutableStateOf<MemberReq?>(null) }
    var noteReq by remember { mutableStateOf<NoteReq?>(null) }
    var dutyDay by remember { mutableStateOf<Long?>(null) }

    val host = remember {
        SheetHost(
            expense = { expenseReq = ExpenseReq(it, null) },
            bazar = { bazarReq = BazarReq(it) },
            money = { k, m, a -> moneyReq = MoneyReq(k, m, a) },
            member = { memberReq = MemberReq(it) },
            note = { noteReq = NoteReq(it) },
            duty = { dutyDay = it },
            openMember = { nav.navigate("member/$it") },
            settings = { nav.navigate("settings") },
            notes = { nav.navigate("notes") },
        )
    }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        bottomBar = {
            if (topLevel) NavigationBar(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)) {
                tabs.forEach { t ->
                    NavigationBarItem(
                        selected = dest?.hierarchy?.any { it.route == t.route } == true,
                        onClick = {
                            nav.navigate(t.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true; restoreState = true
                            }
                        },
                        icon = { Icon(t.icon, contentDescription = t.label) }, label = { Text(t.label, maxLines = 1) },
                    )
                }
            }
        },
        floatingActionButton = {
            if (topLevel) ExtendedFloatingActionButton(
                onClick = { expenseReq = ExpenseReq(null, null) },
                icon = { Icon(Icons.Default.Add, "Quick expense") }, text = { Text("Expense") },
                containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            NavHost(nav, startDestination = "home", modifier = Modifier.fillMaxSize()) {
                composable("home") { HomeScreen(vm, host) }
                composable("bazar") { BazarScreen(vm, host) }
                composable("expenses") { ExpensesScreen(vm, host) }
                composable("members") { MembersScreen(vm, host) }
                composable("reports") { ReportsScreen(vm) }
                composable("notes") { NotesScreen(vm, host) { nav.popBackStack() } }
                composable("settings") { SettingsScreen(vm) { nav.popBackStack() } }
                composable("member/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                    MemberDetail(vm, e.arguments?.getLong("id") ?: 0L, host) { nav.popBackStack() }
                }
            }
            CalculatorOverlay(
                Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 16.dp),
                onExpense = { expenseReq = ExpenseReq(null, it) },
                onPayment = { moneyReq = MoneyReq("PAYMENT", null, it) },
            )
        }
    }

    expenseReq?.let { r -> ExpenseSheet(vm, r.e, r.amount) { expenseReq = null } }
    bazarReq?.let { r -> BazarSheet(vm, r.e) { bazarReq = null } }
    moneyReq?.let { r -> MoneySheet(vm, r.kind, r.member, r.amount) { moneyReq = null } }
    memberReq?.let { r -> MemberSheet(vm, r.m) { memberReq = null } }
    noteReq?.let { r -> NoteSheet(vm, r.n) { noteReq = null } }
    dutyDay?.let { d -> DutySheet(vm, d) { dutyDay = null } }
}

@Composable
fun AppRoot(vm: AppVm) {
    val boot by vm.boot.collectAsState()
    val unlocked by vm.unlocked.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.msgs.collect { snackbar.showSnackbar(it) } }
    val bg = MaterialTheme.colorScheme.background
    val bg2 = MaterialTheme.colorScheme.surface
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(bg, bg2, bg)))) {
        when {
            !boot.loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            boot.mess == null || !boot.hasPin -> SetupScreen(vm)
            !unlocked -> LockScreen(vm)
            else -> MainShell(vm)
        }
        RecoveryDialog(vm)
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = if (unlocked) 96.dp else 16.dp))
    }
}
