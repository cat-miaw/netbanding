package com.netbanding.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.netbanding.app.R
import com.netbanding.app.di.AppContainer
import com.netbanding.app.ui.compare.CompareRoute
import com.netbanding.app.ui.compare.CompareViewModel
import com.netbanding.app.ui.favorites.FavoritesRoute
import com.netbanding.app.ui.favorites.FavoritesViewModel
import com.netbanding.app.ui.home.HomeRoute
import com.netbanding.app.ui.home.HomeViewModel
import com.netbanding.app.ui.onboarding.OnboardingScreen
import com.netbanding.app.ui.settings.PrivacyScreen
import com.netbanding.app.ui.settings.SettingsScreen
import kotlinx.coroutines.launch

private object Routes {
    const val HOME = "home"
    const val ONBOARDING = "onboarding"
    const val SETTINGS = "settings"
    const val PRIVACY = "privacy"
    const val FAVORITES = "favorites"
    const val COMPARE = "compare"
}

/** Placeholder logo until a real brand mark exists. */
@Composable
fun AppLogo(modifier: Modifier = Modifier, size: Int = 40) {
    Box(
        modifier = modifier.size(size.dp).clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "N",
            style = if (size >= 56) MaterialTheme.typography.headlineLarge
            else MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
fun TemplateNavHost(
    container: AppContainer,
    navController: NavHostController = rememberNavController(),
) {
    val onboardingDone by container.userPrefs.onboardingDone.collectAsState(initial = null)
    val region by container.userPrefs.region.collectAsState(initial = "JAVA_ALL")
    val syncState by container.userPrefs.syncState.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var pendingRegion by remember { mutableStateOf("JAVA_ALL") }
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    fun go(route: String) {
        scope.launch { drawerState.close() }
        navController.navigate(route) {
            popUpTo(Routes.HOME)
            launchSingleTop = true
        }
    }

    // Activity-scoped so Home, Favorites and Compare share one selection set.
    val compareVm = viewModel<CompareViewModel>(
        viewModelStoreOwner = LocalContext.current as ComponentActivity,
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                CompareViewModel(container.packageRepository, SavedStateHandle()) as T
        },
    )
    val compareCount by compareVm.count.collectAsStateWithLifecycle()

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            ModalDrawerSheet {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppLogo(size = 56)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                stringResource(R.string.drawer_tagline),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.drawer_home)) },
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                    selected = false,
                    onClick = { go(Routes.HOME) },
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.favorites_title)) },
                    icon = { Icon(Icons.Filled.FavoriteBorder, contentDescription = null) },
                    selected = false,
                    onClick = { go(Routes.FAVORITES) },
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.compare_title)) },
                    icon = { Icon(Icons.Filled.CompareArrows, contentDescription = null) },
                    selected = false,
                    badge = { if (compareCount > 0) Text("$compareCount") },
                    onClick = { go(Routes.COMPARE) },
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.settings)) },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    selected = false,
                    onClick = { go(Routes.SETTINGS) },
                )
                NavigationDrawerItem(
                    label = { Text(stringResource(R.string.privacy_title)) },
                    icon = { Icon(Icons.Filled.PrivacyTip, contentDescription = null) },
                    selected = false,
                    onClick = { go(Routes.PRIVACY) },
                )
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(
                        R.string.data_version,
                        syncState?.dataVersion ?: 0,
                        syncState?.generatedAt?.take(10) ?: "-",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(16.dp),
                )
            }
        },
    ) {
        NavHost(navController = navController, startDestination = Routes.HOME) {
            composable(Routes.HOME) {
                HomeRoute(
                    viewModel = viewModel<HomeViewModel>(
                        factory = object : ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                                HomeViewModel(
                                    container.packageRepository,
                                    container.userPrefs,
                                    container.syncRepository,
                                    container.priceDropMonitor,
                                    container::scheduleSync,
                                    SavedStateHandle(),
                                ) as T
                        },
                    ),
                    compareCount = compareCount,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onOpenCompare = { navController.navigate(Routes.COMPARE) },
                    onToggleCompare = compareVm::toggle,
                )
            }
            composable(Routes.FAVORITES) {
                FavoritesRoute(
                    viewModel = viewModel<FavoritesViewModel>(
                        factory = object : ViewModelProvider.Factory {
                            @Suppress("UNCHECKED_CAST")
                            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                                FavoritesViewModel(container.packageRepository) as T
                        },
                    ),
                    onBack = { navController.popBackStack() },
                    onToggleCompare = compareVm::toggle,
                )
            }
            composable(Routes.COMPARE) {
                CompareRoute(
                    viewModel = compareVm,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    selected = pendingRegion,
                    onSelect = { pendingRegion = it },
                    onDone = {
                        scope.launch {
                            container.userPrefs.setRegion(pendingRegion)
                            container.userPrefs.setOnboardingDone()
                            navController.popBackStack(Routes.HOME, inclusive = false)
                        }
                    },
                    onSkip = {
                        scope.launch {
                            container.userPrefs.setOnboardingDone()
                            navController.popBackStack(Routes.HOME, inclusive = false)
                        }
                    },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    region = region,
                    onRegion = { scope.launch { container.userPrefs.setRegion(it) } },
                    dataVersion = syncState?.dataVersion ?: 0,
                    lastUpdated = syncState?.generatedAt,
                    onBack = { navController.popBackStack() },
                    onPrivacy = { navController.navigate(Routes.PRIVACY) },
                )
            }
            composable(Routes.PRIVACY) {
                PrivacyScreen(onBack = { navController.popBackStack() })
            }
        }
    }

    LaunchedEffect(onboardingDone) {
        if (onboardingDone == false) navController.navigate(Routes.ONBOARDING)
    }
}
