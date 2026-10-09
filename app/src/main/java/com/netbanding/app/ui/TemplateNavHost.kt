package com.netbanding.app.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.netbanding.app.R
import com.netbanding.app.di.AppContainer
import com.netbanding.app.ui.compare.CompareRoute
import com.netbanding.app.ui.compare.CompareViewModel
import com.netbanding.app.ui.components.NetBottomBar
import com.netbanding.app.ui.components.NetTopBar
import com.netbanding.app.ui.favorites.FavoritesRoute
import com.netbanding.app.ui.favorites.FavoritesViewModel
import com.netbanding.app.ui.home.HomeRoute
import com.netbanding.app.ui.home.HomeViewModel
import com.netbanding.app.ui.home.Types
import com.netbanding.app.ui.menu.MenuDrawerContent
import com.netbanding.app.ui.onboarding.OnboardingScreen
import com.netbanding.app.ui.settings.PrivacyScreen
import com.netbanding.app.ui.settings.SettingsScreen
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private object Routes {
    const val MAIN = "main"
    const val ONBOARDING = "onboarding"
    const val SETTINGS = "settings"
    const val PRIVACY = "privacy"
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

    // Activity-scoped so tab state survives swipes, drawer jumps and rotation.
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
    val compareState by compareVm.uiState.collectAsStateWithLifecycle()
    val compareIds by compareVm.selectedIds.collectAsStateWithLifecycle()
    val homeUiState by homeVm.uiState.collectAsStateWithLifecycle()

    // One pager + one chrome for the three tabs: swipes and taps only
    // change the page, never the bars around it.
    val pagerState = rememberPagerState(pageCount = { 3 })

    // Custom drawer (follows the finger both ways). The stock
    // ModalNavigationDrawer can't be driven mid-gesture, and it always
    // loses the gesture race to the pager — so the open-drag is detected
    // on the pager (see modifier below) and applied straight to this
    // offset, with velocity settle on release. 0 = closed, width = open.
    val density = LocalDensity.current
    val drawerWidthPx = remember(density) { with(density) { 320.dp.toPx() } }
    val drawerPx = remember { Animatable(0f) }
    val drawerShown = drawerPx.value > 0f
    val homeListState = rememberLazyListState()
    val homeFocus = remember { FocusRequester() }
    var filtersVisible by rememberSaveable { mutableStateOf(true) }
    // Scroll-linked chrome (YouTube-style): both bars ride 1:1 with the
    // finger — down-travel pushes them out pixel-for-pixel, up-travel
    // pulls them back. No snap, no fade pop. On release they gently
    // settle to the nearer end. Home list only; tabs 1-2 pin at 0.
    // Lists keep CONSTANT insets (measured once) — insets never resize
    // mid-scroll, so hiding bars reveals calm space with zero layout snap
    // (the scroll-jump saga rule). Bars-only motion, draw-phase only.
    val topHide = remember { Animatable(0f) }
    val bottomHide = remember { Animatable(0f) }
    var topBarHpx by remember { mutableIntStateOf(0) }
    var navBarHpx by remember { mutableIntStateOf(0) }
    // Chrome travel == the bar's OWN measured height, so a bar starts
    // sliding out the instant the list moves and is exactly gone when the
    // travel is used up. Frame-measured 2026-10-09: fixed 200/120dp travel
    // left the (94dp) top bar a ~290px dead zone before it budged, so on
    // an up-scroll it lagged the finger and then popped in late, while the
    // (84dp) bottom bar tracked 1:1. Fallbacks only apply before the first
    // onGloballyPositioned lands.
    fun topMax(): Float =
        if (topBarHpx > 0) topBarHpx.toFloat() else with(density) { 96.dp.toPx() }
    fun bottomMax(): Float =
        if (navBarHpx > 0) navBarHpx.toFloat() else with(density) { 88.dp.toPx() }
    LaunchedEffect(homeListState) {
        var prev = 0 to 0
        var settleJob: kotlinx.coroutines.Job? = null
        snapshotFlow {
            homeListState.firstVisibleItemIndex to homeListState.firstVisibleItemScrollOffset
        }.collect { (index, offset) ->
            val (prevIndex, prevOffset) = prev
            val dy = if (index == prevIndex) {
                (offset - prevOffset).toFloat()
            } else {
                (index - prevIndex) * 10_000f
            }
            prev = index to offset
            if (index == 0 && offset < 120) {
                // At the very top both bars belong on screen. Ease them back
                // in rather than snapping: a snap here is a visible pop on
                // fast up-scrolls (both bars jump in a single frame).
                if ((topHide.value != 0f || bottomHide.value != 0f) &&
                    settleJob?.isActive != true
                ) {
                    settleJob = launch {
                        launch {
                            if (topHide.value != 0f) topHide.animateTo(0f, tween(200))
                        }
                        if (bottomHide.value != 0f) bottomHide.animateTo(0f, tween(200))
                    }
                }
                return@collect
            }
            if (dy != 0f) {
                settleJob?.cancel()
                runCatching { topHide.snapTo((topHide.value + dy).coerceIn(0f, topMax())) }
                runCatching { bottomHide.snapTo((bottomHide.value + dy).coerceIn(0f, bottomMax())) }
                // Settle after the finger/fling stops: nearer end wins.
                settleJob = launch {
                    kotlinx.coroutines.delay(250)
                    val tm = topMax()
                    val bm = bottomMax()
                    // "Nearer end wins" is now half of each bar's OWN travel,
                    // so a short bar and a tall bar settle at the same point
                    // in their reveal.
                    val topTarget = if (topHide.value > tm * 0.5f) tm else 0f
                    val bottomTarget = if (bottomHide.value > bm * 0.5f) bm else 0f
                    if (topHide.value != topTarget) {
                        runCatching { topHide.animateTo(topTarget, tween(220)) }
                    }
                    if (bottomHide.value != bottomTarget) {
                        runCatching { bottomHide.animateTo(bottomTarget, tween(220)) }
                    }
                }
            }
        }
    }
    val keyboard = LocalSoftwareKeyboardController.current
    val atTop by remember {
        derivedStateOf {
            homeListState.firstVisibleItemIndex == 0 &&
                homeListState.firstVisibleItemScrollOffset < 120
        }
    }

    fun goTab(page: Int) {
        scope.launch { runCatching { pagerState.animateScrollToPage(page) } }
    }
    fun revealSearch() {
        scope.launch {
            runCatching { homeListState.animateScrollToItem(0) }
            homeFocus.requestFocus()
            keyboard?.show()
        }
    }
    fun openDrawer() {
        scope.launch { drawerPx.animateTo(drawerWidthPx) }
    }
    fun closeDrawer() {
        scope.launch { drawerPx.animateTo(0f) }
    }
    fun drawerGo(page: Int) {
        closeDrawer()
        goTab(page)
    }

    // Back closes the drawer first, never the app from under it.
    if (drawerShown) {
        BackHandler { closeDrawer() }
    }
    // Rightward drag on Beranda opens the drawer (see pager modifier).

    Box(Modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = Routes.MAIN) {
            composable(Routes.MAIN) {
                val tab = pagerState.currentPage
                // Overlay chrome: both bars float OVER the full-bleed list.
                // Show/hide is pure draw-phase translation driven 1:1 by
                // scroll (see topHide/bottomHide). Lists keep CONSTANT
                // insets measured once — hiding bars leaves calm space
                // instead of resizing the list (the scroll-jump saga rule:
                // nothing ever resizes, zero remeasure, zero snap).
                val listBottomPad = remember(navBarHpx, density) {
                    with(density) { navBarHpx.toDp() } + 16.dp
                }
                val listTopPad = remember(topBarHpx, density) {
                    with(density) { topBarHpx.toDp() } + 16.dp
                }
                Scaffold(
                    // Overlay chrome owns all insets itself (top bar has
                    // statusBarsPadding, bottom bar has navigationBarsPadding).
                    // The default Scaffold insets would add the status bar
                    // height a second time -> the big blank gap above.
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    containerColor = MaterialTheme.colorScheme.background,
                ) {
                    Box(
                        Modifier.fillMaxSize(),
                    ) {
                        HorizontalPager(
                        state = pagerState,
                        beyondViewportPageCount = 1,
                        modifier = Modifier.fillMaxSize()
                            // Rightward drag on Beranda opens the drawer. Passive
                            // detectors always lose the slop race to the pager,
                            // so this consumes the slop-crossing event itself
                            // (parent-first) before the pager ever sees movement.
                            // Leftward/vertical drags are never touched, and
                            // touches starting at the system edge are left alone
                            // so the back gesture keeps working.
                            .pointerInput(Unit) {
                                val slop = viewConfiguration.touchSlop
                                val edge = 24.dp.toPx()
                                awaitEachGesture {
                                    // Cards eat the press for ripple, so accept
                                    // already-consumed downs (drag detectors do).
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    if (down.position.x < edge) return@awaitEachGesture
                                    if (drawerPx.value > 0f) {
                                        return@awaitEachGesture
                                    }
                                    var accX = 0f
                                    var accY = 0f
                                    var vel = 0f
                                    var lastX = down.position.x
                                    var lastT = down.uptimeMillis
                                    var steal = false
                                    var tracking = true
                                    while (tracking) {
                                        val event = awaitPointerEvent()
                                        val c = event.changes
                                            .firstOrNull { it.id == down.id }
                                            ?: break
                                        if (!c.pressed) break
                                        val dt = (c.uptimeMillis - lastT).toFloat()
                                        if (dt > 0) {
                                            vel = 0.75f * vel + 0.25f *
                                                ((c.position.x - lastX) / dt * 1000f)
                                        }
                                        lastX = c.position.x
                                        lastT = c.uptimeMillis
                                        val dx = c.position.x - c.previousPosition.x
                                        val dy = c.position.y - c.previousPosition.y
                                        if (!steal) {
                                            accX += dx
                                            accY += dy
                                            when {
                                                pagerState.currentPage == 0 &&
                                                    accX > slop &&
                                                    abs(accX) > abs(accY) -> steal = true
                                                abs(accX) > slop || abs(accY) > slop ->
                                                    tracking = false
                                            }
                                        }
                                        if (steal) {
                                            val target = (drawerPx.value + dx)
                                                .coerceIn(0f, drawerWidthPx)
                                            scope.launch { drawerPx.snapTo(target) }
                                            c.consume()
                                        }
                                    }
                                    if (steal) {
                                        scope.launch {
                                            drawerPx.animateTo(
                                                if (drawerPx.value > drawerWidthPx * 0.35f ||
                                                    vel > 700f
                                                ) {
                                                    drawerWidthPx
                                                } else {
                                                    0f
                                                },
                                            )
                                        }
                                    }
                                }
                            },
                    ) { page ->
                        when (page) {
                            0 -> HomeRoute(
                                viewModel = homeVm,
                                listState = homeListState,
                                focusRequester = homeFocus,
                                filtersVisible = filtersVisible,
                                compareIds = compareIds,
                                topInset = listTopPad,
                                bottomInset = listBottomPad,
                                onToggleCompare = compareVm::toggle,
                            )
                            1 -> FavoritesRoute(
                                viewModel = favoritesVm,
                                compareIds = compareIds,
                                topInset = listTopPad,
                                bottomInset = listBottomPad,
                                onToggleCompare = compareVm::toggle,
                                onBrowse = { goTab(0) },
                            )
                            else -> CompareRoute(
                                viewModel = compareVm,
                                topInset = listTopPad,
                                bottomInset = listBottomPad,
                                onBrowse = { goTab(0) },
                            )
                        }
                    }
                    // Bottom nav floats over the full-bleed pager, riding the
                    // finger 1:1 (translationY only, no fade). Tabs 1-2 pin
                    // at 0; tab 0 follows bottomHide.
                    Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        val bottomOff = if (tab == 0) bottomHide.value else 0f
                        Box(
                            Modifier.onGloballyPositioned {
                                navBarHpx = it.size.height
                            }
                                .graphicsLayer {
                                    translationY = bottomOff
                                    clip = true
                                },
                        ) {
                            NetBottomBar(
                                onHome = { goTab(0) },
                                onFavorites = { goTab(1) },
                                onCompare = { goTab(2) },
                                selected = when (tab) {
                                    1 -> "favorites"
                                    2 -> "compare"
                                    else -> "home"
                                },
                                compareCount = compareCount,
                            )
                        }
                    }
                    // Top bar floats too: same 1:1 linkage, slides up and out.
                    Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        val topOff = if (tab == 0) topHide.value else 0f
                        Box(
                            // graphicsLayer MUST come first: a `background`
                            // placed before it draws OUTSIDE the layer and
                            // stays parked at y=0 while the bar slides away
                            // (the leftover Paper strip over the list).
                            Modifier.onGloballyPositioned {
                                topBarHpx = it.size.height
                            }
                                .graphicsLayer {
                                    translationY = -topOff
                                    clip = true
                                }
                                .background(MaterialTheme.colorScheme.background),
                        ) {
                            when (tab) {
                                1 -> NetTopBar(
                                    title = stringResource(R.string.favorites_title),
                                    onMenu = ::openDrawer,
                                )
                                2 -> NetTopBar(
                                    title = stringResource(R.string.compare_title),
                                    onMenu = ::openDrawer,
                                    actionText = if (compareState.items.isNotEmpty()) {
                                        stringResource(R.string.compare_clear)
                                    } else {
                                        null
                                    },
                                    onAction = if (compareState.items.isNotEmpty()) {
                                        compareVm::clear
                                    } else {
                                        null
                                    },
                                )
                                else -> NetTopBar(
                                    onMenu = ::openDrawer,
                                    onSearch = if (!atTop) ::revealSearch else null,
                                    onToggleFilters = { filtersVisible = !filtersVisible },
                                    filtersVisible = filtersVisible,
                                )
                                }
                            }
                    }
                }
            }
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
        // Scrim + panel ride above the content. The panel tracks drawerPx,
        // which both the open-drag (pager modifier) and this close-drag
        // drive directly, so it follows the finger both ways.
        if (drawerShown) {
            Box(
                Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = (drawerPx.value / drawerWidthPx).coerceIn(0f, 1f) * 0.32f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { closeDrawer() },
            )
        }
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
            modifier = Modifier.fillMaxHeight().width(320.dp)
                .offset { IntOffset((drawerPx.value - drawerWidthPx).roundToInt(), 0) }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var accX = 0f
                        var accY = 0f
                        var vel = 0f
                        var lastX = down.position.x
                        var lastT = down.uptimeMillis
                        var steal = false
                        var tracking = true
                        while (tracking) {
                            val event = awaitPointerEvent()
                            val c = event.changes
                                .firstOrNull { it.id == down.id }
                                ?: break
                            if (!c.pressed) break
                            val dt = (c.uptimeMillis - lastT).toFloat()
                            if (dt > 0) {
                                vel = 0.75f * vel + 0.25f *
                                    ((c.position.x - lastX) / dt * 1000f)
                            }
                            lastX = c.position.x
                            lastT = c.uptimeMillis
                            val dx = c.position.x - c.previousPosition.x
                            val dy = c.position.y - c.previousPosition.y
                            if (!steal) {
                                accX += dx
                                accY += dy
                                when {
                                    accX < -viewConfiguration.touchSlop &&
                                        abs(accX) > abs(accY) -> steal = true
                                    abs(accX) > viewConfiguration.touchSlop ||
                                        abs(accY) > viewConfiguration.touchSlop ->
                                        tracking = false
                                }
                            }
                            if (steal) {
                                val target = (drawerPx.value + dx)
                                    .coerceIn(0f, drawerWidthPx)
                                scope.launch { drawerPx.snapTo(target) }
                                c.consume()
                            }
                        }
                        if (steal) {
                            scope.launch {
                                drawerPx.animateTo(
                                    if (drawerPx.value < drawerWidthPx * 0.65f ||
                                        vel < -700f
                                    ) {
                                        0f
                                    } else {
                                        drawerWidthPx
                                    },
                                )
                            }
                        }
                    }
                },
        ) {
            MenuDrawerContent(
                dataVersion = syncState?.dataVersion ?: 0,
                lastUpdated = syncState?.generatedAt,
                selectedTab = pagerState.currentPage,
                homeType = homeUiState.type,
                onClose = { closeDrawer() },
                onCellular = { homeVm.setType(Types.CELLULAR); drawerGo(0) },
                onBroadband = { homeVm.setType(Types.BROADBAND); drawerGo(0) },
                onFavorites = { drawerGo(1) },
                onCompare = { drawerGo(2) },
                onSettings = {
                    closeDrawer()
                    navController.navigate(Routes.SETTINGS)
                },
                onPrivacy = {
                    closeDrawer()
                    navController.navigate(Routes.PRIVACY)
                },
            )
        }
    }

    LaunchedEffect(onboardingDone) {
        if (onboardingDone == false) navController.navigate(Routes.ONBOARDING)
    }
}
