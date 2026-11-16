package com.danyt.meerkat.app

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.danyt.meerkat.core.design.AppTheme
import com.danyt.meerkat.core.design.ComingSoonScreen
import com.danyt.meerkat.feature.home.FeaturesScreen
import com.danyt.meerkat.feature.home.FeaturesViewModel
import com.danyt.meerkat.feature.home.HomeItem
import com.danyt.meerkat.feature.home.HomeScreen
import com.danyt.meerkat.feature.home.HomeViewModel
import com.danyt.meerkat.feature.settings.CreditsScreen
import com.danyt.meerkat.feature.settings.LicenseScreen
import com.danyt.meerkat.feature.settings.SettingsScreen
import com.danyt.meerkat.feature.settings.SettingsViewModel

private const val SLIDE_MILLIS = 320
private const val GESTURE_FINISH_MILLIS = 140

private val homeItems = Features.map { HomeItem(it.route, it.title, it.description, it.icon, it.built) }

/** The app's single navigation graph. */
@Composable
fun AppNavHost(
    container: AppContainer,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val animate = AppTheme.animationsEnabled
    val open: (String) -> Unit = { route -> navController.navigate(route) { launchSingleTop = true } }
    val back: () -> Unit = { if (navController.previousBackStackEntry != null) navController.popBackStack() }
    val slide = tween<IntOffset>(SLIDE_MILLIS)
    val gestureSlide = tween<IntOffset>(GESTURE_FINISH_MILLIS, easing = LinearEasing)
    val gesture = remember { BackGesture() }

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier,
        // Forward: the new screen comes in from the right. Back, by any means and from either screen edge: the
        // current screen leaves to the right. Once a back gesture has started between two screens, every
        // transition below answers with the back direction for that pair (see BackGesture), so the slide always
        // finishes the way it started.
        enterTransition = {
            when {
                !animate -> EnterTransition.None
                gesture.isFor(this) -> slideInHorizontally(gestureSlide) { -it / 4 }
                else -> slideInHorizontally(slide) { it }
            }
        },
        exitTransition = {
            when {
                !animate -> ExitTransition.None
                gesture.isFor(this) -> slideOutHorizontally(gestureSlide) { it }
                else -> slideOutHorizontally(slide) { -it / 4 }
            }
        },
        popEnterTransition = {
            val spec = if (gesture.isFor(this)) gestureSlide else slide
            if (animate) slideInHorizontally(spec) { -it / 4 } else EnterTransition.None
        },
        popExitTransition = {
            val spec = if (gesture.isFor(this)) gestureSlide else slide
            if (animate) slideOutHorizontally(spec) { it } else ExitTransition.None
        },
        // While the finger is down the screen follows it and previews the previous screen. These are linear
        // because the finger drives them. They are also short, because after release the rest plays at this pace:
        // at the normal 320 ms a fast swipe would visibly slow down for the last stretch.
        predictivePopEnterTransition = {
            gesture.start(this)
            if (animate) slideInHorizontally(gestureSlide) { -it / 4 } else EnterTransition.None
        },
        predictivePopExitTransition = {
            gesture.start(this)
            if (animate) slideOutHorizontally(gestureSlide) { it } else ExitTransition.None
        },
    ) {
        screen(Routes.HOME) {
            val viewModel: HomeViewModel = viewModel(
                factory = viewModelFactory { initializer { HomeViewModel(homeItems, container.settingsRepository) } },
            )
            HomeScreen(
                viewModel = viewModel,
                onOpenFeature = open,
                onOpenAllFeatures = { open(Routes.FEATURES) },
                onOpenSettings = { open(Routes.SETTINGS) },
            )
        }
        screen(Routes.FEATURES) {
            val viewModel: FeaturesViewModel = viewModel(
                factory = viewModelFactory { initializer { FeaturesViewModel(homeItems) } },
            )
            FeaturesScreen(
                viewModel = viewModel,
                onOpenFeature = open,
                onOpenDashboard = back,
                onOpenSettings = { open(Routes.SETTINGS) },
            )
        }

        Features.filterNot { it.built }.forEach { feature ->
            screen(feature.route) {
                ComingSoonScreen(
                    title = stringResource(feature.title),
                    description = stringResource(feature.description),
                    onBack = back,
                )
            }
        }

        screen(Routes.SETTINGS) {
            val viewModel: SettingsViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { SettingsViewModel(container.settingsRepository, container.cacheManager) }
                },
            )
            SettingsScreen(
                viewModel = viewModel,
                onBack = back,
                onOpenCredits = { open(Routes.CREDITS) },
                onOpenLicense = { open(Routes.LICENSE) },
            )
        }
        screen(Routes.CREDITS) { CreditsScreen(onBack = back) }
        screen(Routes.LICENSE) { LicenseScreen(onBack = back) }
    }
}

/**
 * Adds a destination whose content is shown blurred while it is being revealed by going back.
 *
 * The blur follows the same transition as the slide, so during a back gesture the previous screen sharpens as
 * the finger moves, and it is fully sharp once the screen above has gone. Opening a screen does not blur it.
 * Blur needs Android 12 or newer; on older versions the previous screen is simply shown as it is.
 */
private fun NavGraphBuilder.screen(route: String, content: @Composable () -> Unit) {
    composable(route) {
        // False on the first visit, when the screen is being opened. True on every later appearance, which
        // can only be a return to it. Saved with the back stack entry, so it survives being covered.
        var visited by rememberSaveable { mutableStateOf(false) }
        val revealed = remember { visited }
        LaunchedEffect(Unit) { visited = true }

        val enabled = AppTheme.animationsEnabled && AppTheme.blurEnabled && revealed
        val blur by transition.animateDp(
            transitionSpec = { tween(GESTURE_FINISH_MILLIS, easing = LinearEasing) },
            label = "revealBlur",
        ) { state ->
            if (enabled && state == EnterExitState.PreEnter) RevealBlur else 0.dp
        }
        Box(Modifier.fillMaxSize().blur(blur)) { content() }
    }
}

private val RevealBlur = 20.dp

/**
 * Remembers which pair of screens the current back gesture is moving between.
 *
 * When the finger lifts, the navigation library can briefly ask for the transition again before it has
 * registered the pop. Without this it would get the forward direction for that moment and the screen would
 * swing to the opposite side. Entry ids are unique per visit, so a stale pair never matches a later navigation.
 */
private class BackGesture {
    private var fromId: String? = null
    private var toId: String? = null

    fun start(scope: AnimatedContentTransitionScope<NavBackStackEntry>) {
        fromId = scope.initialState.id
        toId = scope.targetState.id
    }

    fun isFor(scope: AnimatedContentTransitionScope<NavBackStackEntry>): Boolean =
        scope.initialState.id == fromId && scope.targetState.id == toId
}
