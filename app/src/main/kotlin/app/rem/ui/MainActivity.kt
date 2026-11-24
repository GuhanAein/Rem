package app.rem.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.runtime.CompositionLocalProvider
import app.rem.ui.motion.LocalNavAnimatedScope
import app.rem.ui.motion.LocalSharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.rem.container
import app.rem.ui.edit.EditScreen
import app.rem.ui.edit.EditViewModel
import app.rem.ui.home.HomeScreen
import app.rem.ui.home.HomeViewModel
import app.rem.ui.home.LaunchAction
import app.rem.ui.onboarding.OnboardingScreen
import app.rem.ui.onboarding.SetupScreen
import app.rem.ui.onboarding.isOnboarded
import app.rem.ui.onboarding.readSetupStatus
import app.rem.ui.theme.RemTheme

class MainActivity : ComponentActivity() {
    private var launchAction by mutableStateOf<LaunchAction?>(null)
    private var openId by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            RemTheme {
                androidx.compose.runtime.CompositionLocalProvider(app.rem.ui.motion.LocalAmbientMotion provides app.rem.ui.motion.systemAnimationsEnabled(this@MainActivity)) {
                RemNavHost(
                    launchAction = launchAction,
                    onLaunchConsumed = { launchAction = null },
                    openId = openId,
                    onOpenConsumed = { openId = null },
                )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            Intent.ACTION_SEND -> launchAction = LaunchAction.QuickAdd(intent.getStringExtra(Intent.EXTRA_TEXT)?.take(300).orEmpty())
            ACTION_QUICK_ADD -> launchAction = LaunchAction.QuickAdd()
            ACTION_VOICE -> launchAction = LaunchAction.Voice
        }
        intent.getLongExtra(EXTRA_OPEN_ID, -1).takeIf { it > 0 }?.let { openId = it }
    }

    companion object {
        const val EXTRA_OPEN_ID = "openReminderId"
        const val ACTION_QUICK_ADD = "app.rem.action.QUICK_ADD"
        const val ACTION_VOICE = "app.rem.action.VOICE"
        const val ACTION_MORNING = "app.rem.action.MORNING"
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun RemNavHost(launchAction: LaunchAction?, onLaunchConsumed: () -> Unit, openId: Long?, onOpenConsumed: () -> Unit) {
    val context = LocalContext.current
    val nav = rememberNavController()
    val start = if (isOnboarded(context)) "home" else "onboarding"

    LaunchedEffect(openId) {
        if (openId != null && isOnboarded(context)) {
            nav.navigate("edit?id=$openId")
            onOpenConsumed()
        }
    }

    SharedTransitionLayout {
    CompositionLocalProvider(LocalSharedTransitionScope provides this) {
    NavHost(
        navController = nav,
        startDestination = start,
        enterTransition = { fadeIn(tween(260)) },
        exitTransition = { fadeOut(tween(200)) },
        popEnterTransition = { fadeIn(tween(260)) },
        popExitTransition = { fadeOut(tween(200)) },
    ) {
        composable("onboarding") {
            OnboardingScreen(onFinished = { nav.navigate("home") { popUpTo("onboarding") { inclusive = true } } })
        }
        composable("home") {
            val vm: HomeViewModel = viewModel(factory = viewModelFactory { initializer { HomeViewModel(context.container) } })
            LifecycleResumeEffect(Unit) {
                vm.setNeedsSetup(!readSetupStatus(context).essentialsOk)
                onPauseOrDispose { }
            }
            CompositionLocalProvider(LocalNavAnimatedScope provides this) {
            HomeScreen(
                viewModel = vm,
                launchAction = launchAction,
                onLaunchConsumed = onLaunchConsumed,
                onOpen = { id -> nav.navigate("edit?id=$id") },
                onMoreOptions = { text -> nav.navigate("edit?text=${Uri.encode(text)}") },
                onSetup = { nav.navigate("setup") },
            )
            }
        }
        composable(
            "edit?id={id}&text={text}",
            arguments = listOf(
                navArgument("id") { type = NavType.LongType; defaultValue = 0L },
                navArgument("text") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: 0L
            val text = entry.arguments?.getString("text")
            val vm: EditViewModel = viewModel(factory = viewModelFactory { initializer { EditViewModel(context.container, id, text) } })
            CompositionLocalProvider(LocalNavAnimatedScope provides this) {
                EditScreen(vm, onBack = { nav.popBackStack() })
            }
        }
        composable("setup") { SetupScreen(onBack = { nav.popBackStack() }) }
    }
    }
    }
}
