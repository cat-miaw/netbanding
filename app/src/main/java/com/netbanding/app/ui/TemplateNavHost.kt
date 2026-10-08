package com.netbanding.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.netbanding.app.di.AppContainer
import com.netbanding.app.ui.compare.CompareRoute
import com.netbanding.app.ui.compare.CompareViewModel
import com.netbanding.app.ui.favorites.FavoritesRoute
import com.netbanding.app.ui.favorites.FavoritesViewModel
import com.netbanding.app.ui.home.HomeRoute
import com.netbanding.app.ui.home.HomeViewModel
import com.netbanding.app.ui.home.Types
import com.netbanding.app.ui.menu.MenuScreen
import com.netbanding.app.ui.onboarding.OnboardingScreen
import com.netbanding.app.ui.settings.PrivacyScreen
import com.netbanding.app.ui.settings.SettingsScreen
import kotlinx.coroutines.launch

private object Routes {
    const val MAIN = "main"
    const val ONBOARDING = "onboarding"
    const val SETTINGS = "settings"
    const val PRIVACY = "privacy"
    const val MENU = "menu"
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
    val activity = LocalContext.current as ComponentActivity
    @Composable
    fun <T : androidx.lifecycle.ViewModel> activityVm(
        modelClass: Class<T>,
        create: () -> T,
    ): T = viewModel(
        viewModelStoreOwner = activity,
        modelClass = modelClass,
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                create() as T
        },
    )

    // Activity-scoped so tab state survives swipes, menu jumps and rotation.
    val homeVm = activityVm(HomeViewModel::class.java) {
        HomeViewModel(
            container.packageRepository,
            container.userPrefs,
            container.syncRepository,
            container.priceDropMonitor,
            container::scheduleSync,
            SavedStateHandle(),
        )
    }
    val favoritesVm = activityVm(FavoritesViewModel::class.java) {
        FavoritesViewModel(container.packageRepository)
    }
    val compareVm = activityVm(CompareViewModel::class.java) {
        CompareViewModel(container.packageRepository, SavedStateHandle())
    }
    val compareCount by compareVm.count.collectAsState(initial = 0)

    // One pager for the three tabs: bottom taps and swipes stay in sync.
    val pagerState = rememberPagerState(pageCount = { 3 })
    fun goTab(page: Int) {
        scope.launch { runCatching { pagerState.animateScrollToPage(page) } }
        navController.navigate(Routes.MAIN) {
            popUpTo(Routes.MAIN)
            launchSingleTop = true
        }
    }

    NavHost(navController = navController, startDestination = Routes.MAIN) {
        composable(Routes.MAIN) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                when (page) {
                    0 -> HomeRoute(
                        viewModel = homeVm,
                        compareCount = compareCount,
                        onOpenMenu = { navController.navigate(Routes.MENU) },
                        onOpenFavorites = { goTab(1) },
                        onOpenCompare = { goTab(2) },
                        onToggleCompare = compareVm::toggle,
                    )
                    1 -> FavoritesRoute(
                        viewModel = favoritesVm,
                        onOpenMenu = { navController.navigate(Routes.MENU) },
                        onHome = { goTab(0) },
                        onOpenCompare = { goTab(2) },
                        compareCount = compareCount,
                        onToggleCompare = compareVm::toggle,
                    )
                    else -> CompareRoute(
                        viewModel = compareVm,
                        onOpenMenu = { navController.navigate(Routes.MENU) },
                        onHome = { goTab(0) },
                        onOpenFavorites = { goTab(1) },
                        compareCount = compareCount,
                    )
                }
            }
        }
        composable(Routes.MENU) {
            MenuScreen(
                dataVersion = syncState?.dataVersion ?: 0,
                lastUpdated = syncState?.generatedAt,
                onClose = { navController.popBackStack() },
                onCellular = { homeVm.setType(Types.CELLULAR); goTab(0) },
                onBroadband = { homeVm.setType(Types.BROADBAND); goTab(0) },
                onFavorites = { goTab(1) },
                onCompare = { goTab(2) },
                onSettings = { navController.navigate(Routes.SETTINGS) },
                onPrivacy = { navController.navigate(Routes.PRIVACY) },
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
                        navController.popBackStack(Routes.MAIN, inclusive = false)
                    }
                },
                onSkip = {
                    scope.launch {
                        container.userPrefs.setOnboardingDone()
                        navController.popBackStack(Routes.MAIN, inclusive = false)
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

    LaunchedEffect(onboardingDone) {
        if (onboardingDone == false) navController.navigate(Routes.ONBOARDING)
    }
}
