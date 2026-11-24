package app.rem.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import app.rem.ui.onboarding.OnboardingScreen
import app.rem.ui.onboarding.SetupScreen
import app.rem.ui.onboarding.isOnboarded
import app.rem.ui.onboarding.readSetupStatus
import app.rem.ui.theme.RemTheme

class MainActivity : ComponentActivity() {
    private var sharedText by mutableStateOf<String?>(null)
    private var openId by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            RemTheme {
                RemNavHost(
                    sharedText = sharedText,
                    onSharedConsumed = { sharedText = null },
                    openId = openId,
                    onOpenConsumed = { openId = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        if (intent.action == Intent.ACTION_SEND) {
            sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)?.take(300)
        }
        intent.getLongExtra(EXTRA_OPEN_ID, -1).takeIf { it > 0 }?.let { openId = it }
    }

    companion object {
        const val EXTRA_OPEN_ID = "openReminderId"
    }
}

@Composable
private fun RemNavHost(sharedText: String?, onSharedConsumed: () -> Unit, openId: Long?, onOpenConsumed: () -> Unit) {
    val context = LocalContext.current
    val nav = rememberNavController()
    val start = if (isOnboarded(context)) "home" else "onboarding"

    LaunchedEffect(openId) {
        if (openId != null && isOnboarded(context)) {
            nav.navigate("edit?id=$openId")
            onOpenConsumed()
        }
    }

    NavHost(
        navController = nav,
        startDestination = start,
        enterTransition = { slideInHorizontally { it / 3 } + fadeIn() },
        exitTransition = { fadeOut() },
        popEnterTransition = { fadeIn() },
        popExitTransition = { slideOutHorizontally { it / 3 } + fadeOut() },
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
            HomeScreen(
                viewModel = vm,
                quickAddPrefill = sharedText,
                onQuickAddConsumed = onSharedConsumed,
                onOpen = { id -> nav.navigate("edit?id=$id") },
                onMoreOptions = { text -> nav.navigate("edit?text=${Uri.encode(text)}") },
                onSetup = { nav.navigate("setup") },
            )
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
            EditScreen(vm, onBack = { nav.popBackStack() })
        }
        composable("setup") { SetupScreen(onBack = { nav.popBackStack() }) }
    }
}
