package io.towerscope.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CellTower
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.towerscope.AppController
import io.towerscope.calc.MetricKind
import io.towerscope.model.DatabaseTower
import io.towerscope.ui.components.PulsingDot
import io.towerscope.ui.screens.CellDetailScreen
import io.towerscope.ui.screens.DatabaseTowerScreen
import io.towerscope.ui.screens.DeviceScreen
import io.towerscope.ui.screens.MetricDetailScreen
import io.towerscope.ui.screens.OverviewScreen
import io.towerscope.ui.screens.SettingsScreen
import io.towerscope.ui.screens.SignalScreen
import io.towerscope.ui.screens.SpeedScreen
import io.towerscope.ui.screens.TowersScreen
import io.towerscope.ui.theme.Palette
import io.towerscope.ui.theme.TowerScopeTheme

enum class Tab(val label: String, val icon: ImageVector) {
    OVERVIEW("Overview", Icons.Rounded.Radar),
    SIGNAL("Signal", Icons.Rounded.GraphicEq),
    TOWERS("Towers", Icons.Rounded.CellTower),
    SPEED("Speed", Icons.Rounded.Speed),
}

sealed interface Route {
    data class Home(val tab: Tab) : Route
    data class Metric(val kind: MetricKind) : Route
    data class Cell(val key: String) : Route
    data class DbTower(val tower: DatabaseTower) : Route
    data object Settings : Route
    data object Device : Route
    data object SpotFinder : Route
    data object History : Route
    data class Guide(val topic: io.towerscope.calc.GuideTopic?) : Route
}

/** Minimal back-stack navigator; the host's system back button calls [pop]. */
class Navigator {
    val stack = mutableStateListOf<Route>(Route.Home(Tab.OVERVIEW))
    var lastWasPush by mutableStateOf(true)
        private set

    val current: Route get() = stack.last()
    val tab: Tab get() = stack.filterIsInstance<Route.Home>().lastOrNull()?.tab ?: Tab.OVERVIEW
    val canGoBack: Boolean get() = stack.size > 1 || tab != Tab.OVERVIEW

    fun push(r: Route) {
        lastWasPush = true
        stack.add(r)
    }

    fun pop(): Boolean {
        lastWasPush = false
        return when {
            stack.size > 1 -> { stack.removeAt(stack.lastIndex); true }
            tab != Tab.OVERVIEW -> { stack[0] = Route.Home(Tab.OVERVIEW); true }
            else -> false
        }
    }

    fun select(tab: Tab) {
        lastWasPush = false
        stack.clear()
        stack.add(Route.Home(tab))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TowerScopeApp(controller: AppController, navigator: Navigator) {
    DisposableEffect(controller) {
        controller.start()
        onDispose { }
    }
    TowerScopeTheme {
        val route = navigator.current
        val isHome = route is Route.Home
        val snapshot by controller.snapshot.collectAsState()
        Scaffold(
            containerColor = Palette.Bg,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Palette.Bg.copy(alpha = 0.0f)),
                    navigationIcon = {
                        if (!isHome) IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Palette.Text)
                        }
                    },
                    title = {
                        if (isHome && navigator.tab == Tab.OVERVIEW) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Tower", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Palette.Text)
                                Text("Scope", fontWeight = FontWeight.Light, fontSize = 22.sp, color = Palette.Cyan)
                                Spacer(Modifier.width(10.dp))
                                if (snapshot != null) {
                                    PulsingDot(Palette.Green, size = 5.dp)
                                    Text("LIVE", fontSize = 10.sp, color = Palette.Green, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                                }
                            }
                        } else {
                            Text(titleFor(route, navigator.tab), fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = Palette.Text)
                        }
                    },
                    actions = {
                        if (isHome) IconButton(onClick = { navigator.push(Route.Settings) }) {
                            Icon(Icons.Rounded.Settings, "Settings", tint = Palette.TextDim)
                        }
                    },
                )
            },
            bottomBar = {
                NavigationBar(containerColor = Palette.Surface, tonalElevation = 0.dp) {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = navigator.tab == t,
                            onClick = { navigator.select(t) },
                            icon = { Icon(t.icon, t.label) },
                            label = { Text(t.label, fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Palette.Cyan,
                                selectedTextColor = Palette.Cyan,
                                indicatorColor = Palette.Cyan.copy(alpha = 0.14f),
                                unselectedIconColor = Palette.TextFaint,
                                unselectedTextColor = Palette.TextFaint,
                            ),
                        )
                    }
                }
            },
        ) { padding ->
            Box(
                Modifier.fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Palette.BgTop, Palette.Bg, Palette.Bg)))
                    .padding(padding),
            ) {
                AnimatedContent(
                    targetState = route,
                    transitionSpec = {
                        if (navigator.lastWasPush && targetState !is Route.Home) {
                            (slideInHorizontally(tween(280)) { it / 3 } + fadeIn(tween(280))) togetherWith fadeOut(tween(180))
                        } else {
                            fadeIn(tween(220)) togetherWith (slideOutHorizontally(tween(220)) { it / 4 } + fadeOut(tween(200)))
                        }
                    },
                ) { r ->
                    when (r) {
                        is Route.Home -> when (r.tab) {
                            Tab.OVERVIEW -> OverviewScreen(controller, navigator)
                            Tab.SIGNAL -> SignalScreen(controller, navigator)
                            Tab.TOWERS -> TowersScreen(controller, navigator)
                            Tab.SPEED -> SpeedScreen(controller)
                        }
                        is Route.Metric -> MetricDetailScreen(controller, r.kind)
                        is Route.Cell -> CellDetailScreen(controller, navigator, r.key)
                        is Route.DbTower -> DatabaseTowerScreen(controller, r.tower)
                        Route.Settings -> SettingsScreen(controller, navigator)
                        Route.Device -> DeviceScreen(controller)
                        Route.SpotFinder -> io.towerscope.ui.screens.SpotFinderScreen(controller, navigator)
                        Route.History -> io.towerscope.ui.screens.HistoryScreen(controller)
                        is Route.Guide -> io.towerscope.ui.screens.GuideScreen(controller, navigator, r.topic)
                    }
                }
            }
        }
    }
}

private fun titleFor(r: Route, tab: Tab): String = when (r) {
    is Route.Home -> tab.label
    is Route.Metric -> r.kind.shortTitle
    is Route.Cell -> "Cell detail"
    is Route.DbTower -> "Database tower"
    Route.Settings -> "Settings"
    Route.Device -> "Device & network"
    Route.SpotFinder -> "Find best spot"
    Route.History -> "History & patterns"
    is Route.Guide -> "Better signal guide"
}

/** Standard scrolling screen body with consistent gutters. */
@Composable
fun ScreenColumn(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp)),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
fun FullWidth(content: @Composable () -> Unit) = Box(Modifier.fillMaxWidth()) { content() }
