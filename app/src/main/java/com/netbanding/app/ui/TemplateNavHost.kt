package com.netbanding.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.netbanding.app.di.AppContainer
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
}

@Composable
fun TemplateNavHost(
    container: AppContainer,
    navController: NavHostController = rememberNavController(),
) {
    val onboardingDone by container.userPrefs.onboardingDone.collectAsState(initial = null)
    val region by container.userPrefs.region.collectAsState(initial = "JAVA_ALL")
    val scope = rememberCoroutineScope()
    var pendingRegion by remember { mutableStateOf("JAVA_ALL") }

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
                                container::scheduleSync,
                                SavedStateHandle(),
                            ) as T
                    },
                ),
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
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
            val syncState by container.userPrefs.syncState.collectAsState(initial = null)
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
        }    }

    LaunchedEffect(onboardingDone) {
        if (onboardingDone == false) navController.navigate(Routes.ONBOARDING)
    }
}
