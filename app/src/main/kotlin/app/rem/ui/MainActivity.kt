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
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import kotlinx.coroutines.launch

class MainActivity : androidx.fragment.app.FragmentActivity() {
    private var launchAction by mutableStateOf<LaunchAction?>(null)
    /** A screen to open on launch: "morning", "life", "templates", "captured", "bills". */
    private var openRoute by mutableStateOf<String?>(null)
    /** Set by the Paste widget button; the clipboard can only be read once the window has focus. */
    private var pasteOnFocus = false
    private var openTab by mutableStateOf<app.rem.ui.spaces.Tab?>(null)
    /** App lock: unlocked for this visit once biometric/screen-lock succeeds. */
    private var unlocked by mutableStateOf(false)
    private var openId by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        container.settings.value.theme.let { mode ->
            val nightUi = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            app.rem.ui.theme.RemPalette.prime(mode == app.rem.data.ThemeMode.DARK || (mode == app.rem.data.ThemeMode.SYSTEM && nightUi))
        }
        setContent {
            val settings by container.settings.state.collectAsState()
            val dark = app.rem.ui.theme.isDark(settings.theme)
            androidx.compose.runtime.DisposableEffect(dark) {
                val style = if (dark) androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                else androidx.activity.SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            RemTheme(darkTheme = dark, accent = settings.accent) {
                androidx.compose.runtime.CompositionLocalProvider(app.rem.ui.motion.LocalAmbientMotion provides app.rem.ui.motion.systemAnimationsEnabled(this@MainActivity)) {
                    if (settings.appLock && !unlocked) {
                        app.rem.ui.lock.LockScreen(onUnlock = { app.rem.ui.lock.authenticate(this@MainActivity) { unlocked = true } })
                    } else {
                        RemNavHost(
                            launchAction = launchAction,
                            onLaunchConsumed = { launchAction = null },
                            openId = openId,
                            onOpenConsumed = { openId = null },
                            openRoute = openRoute,
                            onRouteConsumed = { openRoute = null },
                            openTab = openTab,
                            onTabConsumed = { openTab = null },
                        )
                    }
                }
            }
        }
    }

    private var backgroundedAt: Long? = null

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) backgroundedAt = System.currentTimeMillis()
    }

    override fun onStart() {
        super.onStart()
        // Pickers (photos, ringtones, files) briefly leave Rem; only a real absence re-locks it.
        if (app.rem.ui.lock.shouldRelock(backgroundedAt, System.currentTimeMillis())) unlocked = false
        backgroundedAt = null
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && pasteOnFocus) {
            pasteOnFocus = false
            val clip = getSystemService(android.content.ClipboardManager::class.java)?.primaryClip
            val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()?.trim().orEmpty()
            launchAction = LaunchAction.QuickAdd(text.take(300))
            if (text.isEmpty()) android.widget.Toast.makeText(this, "Nothing copied yet", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
                if (isProductLink(text)) {
                    // Products shared from shopping apps go to the Wishlist.
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch { container.spaces.addShared(text.take(600)) }
                    openTab = app.rem.ui.spaces.Tab.WISHLIST
                } else {
                    launchAction = LaunchAction.QuickAdd(text.take(300))
                }
            }
            ACTION_SHOPPING -> openTab = app.rem.ui.spaces.Tab.SHOPPING
            ACTION_WISHLIST -> openTab = app.rem.ui.spaces.Tab.WISHLIST
            ACTION_WORK -> openTab = app.rem.ui.spaces.Tab.WORK
            ACTION_QUICK_ADD -> launchAction = LaunchAction.QuickAdd()
            ACTION_VOICE -> launchAction = LaunchAction.Voice
            ACTION_MORNING -> openRoute = "morning"
            ACTION_LIFE -> openRoute = "life"
            ACTION_TEMPLATES -> openRoute = "templates"
            ACTION_CAPTURED -> openRoute = "captured"
            ACTION_BILLS -> openRoute = "bills"
            ACTION_PASTE -> pasteOnFocus = true
        }
        intent.getLongExtra(EXTRA_OPEN_ID, -1).takeIf { it > 0 }?.let { openId = it }
    }

    private fun isProductLink(text: String): Boolean =
        Regex("""https?://\S*(amazon\.|amzn\.|flipkart\.|fkrt\.|myntra\.|ajio\.|meesho\.|nykaa\.|tatacliq\.|croma\.|ikea\.|reliancedigital\.|snapdeal\.)""", RegexOption.IGNORE_CASE).containsMatchIn(text)

    companion object {
        const val EXTRA_OPEN_ID = "openReminderId"
        const val ACTION_QUICK_ADD = "app.rem.action.QUICK_ADD"
        const val ACTION_VOICE = "app.rem.action.VOICE"
        const val ACTION_MORNING = "app.rem.action.MORNING"
        const val ACTION_SHOPPING = "app.rem.action.SHOPPING"
        const val ACTION_WISHLIST = "app.rem.action.WISHLIST"
        const val ACTION_WORK = "app.rem.action.WORK"
        const val ACTION_LIFE = "app.rem.action.LIFE"
        const val ACTION_TEMPLATES = "app.rem.action.TEMPLATES"
        const val ACTION_CAPTURED = "app.rem.action.CAPTURED"
        const val ACTION_BILLS = "app.rem.action.BILLS"
        const val ACTION_PASTE = "app.rem.action.PASTE"
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun RemNavHost(
    launchAction: LaunchAction?,
    onLaunchConsumed: () -> Unit,
    openId: Long?,
    onOpenConsumed: () -> Unit,
    openRoute: String?,
    onRouteConsumed: () -> Unit,
    openTab: app.rem.ui.spaces.Tab? = null,
    onTabConsumed: () -> Unit = {},
) {
    var tab by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(app.rem.ui.spaces.Tab.TODAY) }
    LaunchedEffect(openTab) { openTab?.let { tab = it; onTabConsumed() } }
    val context = LocalContext.current
    val nav = rememberNavController()
    val start = if (isOnboarded(context)) "home" else "onboarding"

    LaunchedEffect(openId) {
        if (openId != null && isOnboarded(context)) {
            nav.navigate("edit?id=$openId")
            onOpenConsumed()
        }
    }
    LaunchedEffect(openRoute) {
        if (openRoute != null && isOnboarded(context)) {
            nav.navigate(openRoute)
            onRouteConsumed()
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
            val navScope = this
            val navInset = androidx.compose.foundation.layout.WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
                CompositionLocalProvider(
                    LocalNavAnimatedScope provides navScope,
                    app.rem.ui.spaces.LocalBottomInset provides app.rem.ui.spaces.TAB_BAR_HEIGHT + navInset,
                ) {
                    when (tab) {
                        app.rem.ui.spaces.Tab.TODAY -> HomeScreen(
                            viewModel = vm,
                            launchAction = launchAction,
                            onLaunchConsumed = onLaunchConsumed,
                            onOpen = { id -> nav.navigate("edit?id=$id") },
                            onMoreOptions = { text -> nav.navigate("edit?text=${Uri.encode(text)}") },
                            onSetup = { nav.navigate("settings") },
                            onSpace = { route -> nav.navigate(route) },
                        )
                        app.rem.ui.spaces.Tab.SHOPPING -> app.rem.ui.spaces.ShoppingScreen()
                        app.rem.ui.spaces.Tab.WISHLIST -> app.rem.ui.spaces.WishlistScreen()
                        app.rem.ui.spaces.Tab.WORK -> app.rem.ui.spaces.WorkScreen(onOpen = { id -> nav.navigate("edit?id=$id") })
                    }
                }
                app.rem.ui.spaces.TabBar(tab, { tab = it }, androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.BottomCenter))
            }
        }
        composable(
            "edit?id={id}&text={text}&kind={kind}",
            arguments = listOf(
                navArgument("id") { type = NavType.LongType; defaultValue = 0L },
                navArgument("text") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("kind") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: 0L
            val text = entry.arguments?.getString("text")
            val kind = entry.arguments?.getString("kind")?.let { runCatching { app.rem.core.model.Kind.valueOf(it) }.getOrNull() }
            val vm: EditViewModel = viewModel(factory = viewModelFactory { initializer { EditViewModel(context.container, id, text, kind) } })
            CompositionLocalProvider(LocalNavAnimatedScope provides this) {
                EditScreen(vm, onBack = { nav.popBackStack() }, onManagePlaces = { nav.navigate("settings") })
            }
        }
        composable("setup") { SetupScreen(onBack = { nav.popBackStack() }) }
        composable("settings") { app.rem.ui.screens.SettingsScreen(onBack = { nav.popBackStack() }, onReliability = { nav.navigate("setup") }, onRoute = { r -> nav.navigate(r) }) }
        composable("life") {
            app.rem.ui.screens.LifeScreen(
                onBack = { nav.popBackStack() },
                onOpen = { id -> nav.navigate("edit?id=$id") },
                onNew = { k -> nav.navigate("edit?kind=${k.name}") },
                onRoute = { r -> nav.navigate(r) },
            )
        }
        composable("people") { app.rem.ui.screens.PeopleScreen(onBack = { nav.popBackStack() }) }
        composable("insights") {
            app.rem.ui.screens.InsightsScreen(onBack = { nav.popBackStack() }, onFixReliability = { nav.navigate("setup") }, onYear = { nav.navigate("year") })
        }
        composable("year") { app.rem.ui.screens.YearScreen(onBack = { nav.popBackStack() }) }
        composable("morning") { app.rem.ui.screens.MorningScreen(onBack = { nav.popBackStack() }) }
        composable("templates") { app.rem.ui.screens.TemplatesScreen(onBack = { nav.popBackStack() }) }
        composable("captured") {
            app.rem.ui.screens.CapturedScreen(onBack = { nav.popBackStack() }, onUse = { text -> nav.navigate("edit?text=${Uri.encode(text)}") })
        }
        composable("bills") { app.rem.ui.screens.BillsScanScreen(onBack = { nav.popBackStack() }, onOpen = { id -> nav.navigate("edit?id=$id") }) }
    }
    }
    }
}
