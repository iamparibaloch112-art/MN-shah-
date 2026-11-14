package com.example.navigation

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CandlestickChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.data.ai.MnShahAiAnalyst
import com.example.data.market.LiveMarketRepository
import com.example.data.repository.TradingRepository
import com.example.firebase.authStateFlow
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.BrokerComplianceScreen
import com.example.ui.screens.MarketChartScreen
import com.example.ui.screens.RobotDashboardScreen
import com.example.ui.screens.SignalHistoryScreen
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.NeonYellow
import com.example.ui.theme.NeonYellowSoft
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.TextMuted
import com.example.ui.viewmodel.MnShahViewModel
import com.example.voice.MnShahVoiceEngine
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

object NavRoutes {
    const val COMMAND = "command"
    const val MARKET = "market"
    const val SIGNALS = "signals"
    const val BROKER = "broker"
}

private data class NavTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val testTag: String
)

private val TopTabs = listOf(
    NavTab(NavRoutes.COMMAND, "Command", Icons.Default.SmartToy, "nav_command"),
    NavTab(NavRoutes.MARKET, "Market", Icons.Default.CandlestickChart, "nav_market"),
    NavTab(NavRoutes.SIGNALS, "Signals", Icons.Default.History, "nav_signals"),
    NavTab(NavRoutes.BROKER, "Broker", Icons.Default.Security, "nav_broker")
)

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            AuthenticatedBotShell(
                currentUserId = user.uid,
                userDisplayName = user.displayName ?: "MN Shah Trader",
                userEmail = user.email
            )
        }
    }
}

@Composable
private fun AuthenticatedBotShell(
    currentUserId: String,
    userDisplayName: String,
    userEmail: String?
) {
    val context = LocalContext.current
    val voiceEngine = remember(context) { MnShahVoiceEngine(context.applicationContext) }
    LaunchedEffect(voiceEngine) {
        voiceEngine.initializeAsync()
    }
    DisposableEffect(voiceEngine) {
        onDispose { voiceEngine.release() }
    }

    val viewModel: MnShahViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) as Application
                val dbId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(dbId)
                MnShahViewModel(
                    tradingRepository = TradingRepository(db),
                    marketRepository = LiveMarketRepository(),
                    aiAnalyst = MnShahAiAnalyst(app),
                    currentUserId = currentUserId,
                    displayName = userDisplayName
                )
            }
        }
    )

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianVoid)
    ) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            containerColor = ObsidianVoid,
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        containerColor = CarbonSurface,
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        TopTabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = NeonYellow,
                                    selectedTextColor = NeonYellow,
                                    indicatorColor = NeonYellowSoft,
                                    unselectedIconColor = TextMuted,
                                    unselectedTextColor = TextMuted
                                ),
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(containerColor = CarbonSurface) {
                        TopTabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = NeonYellow,
                                    selectedTextColor = NeonYellow,
                                    indicatorColor = NeonYellowSoft,
                                    unselectedIconColor = TextMuted,
                                    unselectedTextColor = TextMuted
                                ),
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = NavRoutes.COMMAND,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(NavRoutes.COMMAND) {
                        RobotDashboardScreen(viewModel = viewModel, voiceEngine = voiceEngine)
                    }
                    composable(NavRoutes.MARKET) {
                        MarketChartScreen(viewModel = viewModel)
                    }
                    composable(NavRoutes.SIGNALS) {
                        SignalHistoryScreen(viewModel = viewModel, voiceEngine = voiceEngine)
                    }
                    composable(NavRoutes.BROKER) {
                        BrokerComplianceScreen(
                            viewModel = viewModel,
                            userDisplayName = userDisplayName,
                            userEmail = userEmail,
                            onSignedOut = {}
                        )
                    }
                }
            }
        }
    }
}
