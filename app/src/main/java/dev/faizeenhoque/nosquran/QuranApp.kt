package dev.faizeenhoque.nosquran

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private data class NavigationRequest(val path: String, val tab: Boolean = false)

private val tabs =
    listOf(
        Triple("Home", "Today", R.drawable.ic_house),
        Triple("Quran", "Quran", R.drawable.ic_book_open),
        Triple("Search", "Search", R.drawable.ic_search),
        Triple("Saved", "Saved", R.drawable.ic_bookmark),
        Triple("Settings", "Settings", R.drawable.ic_settings),
    )

@Composable
fun QuranApp(repo: QuranRepository, state: QuranState = viewModel(), startRoute: String? = null) {
    val audio = rememberPlayback(state.settings.background)
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    LaunchedEffect(Unit) { ReciterCatalog.load(appContext) }
    LaunchedEffect(startRoute) {
        if (BuildConfig.BENCHMARK && startRoute == "player" && audio.key == null)
            audio.play(repo, repo.verses.first(), state.settings)
    }
    LaunchedEffect(state.settings.reciter) {
        repo.verse(audio.key)?.let { verse ->
            if (
                audio.activeReciter.isNotEmpty() &&
                    audio.activeReciter != ReciterCatalog.resolve(state.settings.reciter)?.id
            )
                audio.play(repo, verse, state.settings)
        }
    }
    LaunchedEffect(state.settings.speed, audio.controller) {
        audio.controller?.setPlaybackSpeed(state.settings.speed)
    }
    NosTheme(state.settings) {
        Surface(
            Modifier.fillMaxSize().semantics { testTagsAsResourceId = true },
            color = MaterialTheme.colorScheme.background,
        ) {
            if (!state.onboarded) {
                OnboardingScreen(state)
                return@Surface
            }
            val nav = rememberNavController()
            val entry by nav.currentBackStackEntryAsState()
            val route = entry?.destination?.route ?: "Home"
            val isReader = route.startsWith("reader/")
            val root = tabs.any { it.first == route }
            val snackbar = remember { SnackbarHostState() }
            val scope = rememberCoroutineScope()
            fun notify(message: String) {
                scope.launch { snackbar.showSnackbar(message) }
            }
            var pendingNavigation by remember { mutableStateOf<NavigationRequest?>(null) }
            fun navigate(path: String) {
                if (
                    nav.currentBackStackEntry?.destination?.route == path ||
                        pendingNavigation?.path == path
                )
                    return
                pendingNavigation = NavigationRequest(path)
            }
            fun open(key: String) {
                if (pendingNavigation?.path == "reader/$key") return
                state.opened(key)
                navigate("reader/$key")
            }
            LaunchedEffect(pendingNavigation) {
                val request = pendingNavigation ?: return@LaunchedEffect
                val owner = nav.currentBackStackEntry
                if (owner != null) {
                    owner.lifecycle.currentStateFlow.first {
                        it == androidx.lifecycle.Lifecycle.State.RESUMED ||
                            it == androidx.lifecycle.Lifecycle.State.DESTROYED
                    }
                    if (
                        owner != nav.currentBackStackEntry ||
                            owner.lifecycle.currentState !=
                                androidx.lifecycle.Lifecycle.State.RESUMED
                    ) {
                        pendingNavigation = null
                        return@LaunchedEffect
                    }
                }
                pendingNavigation = null
                nav.navigate(request.path) {
                    if (request.tab)
                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = request.tab
                }
            }
            var actionVerse by remember { mutableStateOf<Verse?>(null) }
            val initial = remember {
                startRoute
                    ?: when (state.settings.opening) {
                        "Reader" ->
                            if (state.settings.resume) "reader/${state.last}" else "reader/1:1"
                        "Quran" -> "Quran"
                        else -> "Home"
                    }
            }
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                snackbarHost = { SnackbarHost(snackbar) },
                topBar = {
                    if (!root && !isReader)
                        Row(
                            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Glyph(R.drawable.ic_arrow_left, "Back") {
                                if (!nav.popBackStack()) navigate("Home")
                            }
                            Eyebrow(AppMetadata.Name)
                        }
                },
                bottomBar = {
                    Column {
                        AnimatedVisibility(
                            visible = route != "player" && audio.key != null,
                            enter =
                                fadeIn(tween(LocalNosMotion.current.duration())) +
                                    expandVertically(tween(LocalNosMotion.current.duration())),
                            exit =
                                fadeOut(tween(LocalNosMotion.current.duration(MotionTokens.Fast))) +
                                    shrinkVertically(tween(LocalNosMotion.current.duration())),
                        ) {
                            MiniPlayer(repo, audio) { navigate("player") }
                        }
                        if (root)
                            NosBottomNavigation(
                                pendingNavigation?.takeIf { it.tab }?.path ?: route
                            ) { path ->
                                pendingNavigation = NavigationRequest(path, true)
                            }
                    }
                },
            ) { padding ->
                val motion = LocalNosMotion.current
                Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                    Box(Modifier.widthIn(max = 840.dp).fillMaxSize().align(Alignment.TopCenter)) {
                        NavHost(
                            navController = nav,
                            startDestination = initial,
                            enterTransition = { nosScreenEnter(motion) },
                            exitTransition = { nosScreenExit(motion) },
                            popEnterTransition = { nosScreenEnter(motion, pop = true) },
                            popExitTransition = { nosScreenExit(motion, pop = true) },
                            // Phone back has its own defaults; override them to avoid the
                            // platform shrink effect and keep gesture/button returns consistent.
                            predictivePopEnterTransition = { _ ->
                                nosScreenEnter(motion, pop = true)
                            },
                            predictivePopExitTransition = { _ ->
                                nosScreenExit(motion, pop = true)
                            },
                        ) {
                            nosDestination("Home") {
                                HomeScreen(repo, state, ::open, ::navigate) {
                                    actionVerse = it
                                }
                            }
                            nosDestination("Quran") {
                                LibraryScreen(repo, state, ::open)
                            }
                            nosDestination("Search") { SearchScreen(repo, state, ::open) }
                            nosDestination("Saved") {
                                SavedScreen(repo, state, ::open) { actionVerse = it }
                            }
                            nosDestination("Settings") {
                                SettingsScreen(repo, state, ::navigate, ::notify)
                            }
                            nosDestination("reader/{key}") { e ->
                                ReaderScreen(
                                    repo,
                                    state,
                                    e.arguments?.getString("key") ?: "1:1",
                                    audio,
                                    { if (!nav.popBackStack()) navigate("Home") },
                                    ::open,
                                    { actionVerse = it },
                                    ::navigate,
                                    actionVerse?.key,
                                )
                            }
                            nosDestination("player") {
                                PlayerScreen(repo, state, audio, ::open, ::navigate)
                            }
                            nosDestination("details/{key}") { e ->
                                DetailsScreen(
                                    repo,
                                    state,
                                    e.arguments?.getString("key") ?: "1:1",
                                    ::open,
                                    { actionVerse = it },
                                    ::navigate,
                                )
                            }
                            nosDestination("history") { HistoryScreen(repo, state, ::open) }
                            nosDestination("stats") { StatisticsScreen(repo, state) }
                            nosDestination("downloads") {
                                DownloadsScreen(repo, state, ::notify, ::navigate)
                            }
                            listOf("reciters", "translations").forEach { path ->
                                nosDestination(path) { SelectionScreen(path, state) }
                            }
                            listOf("about", "licenses", "privacy").forEach { path ->
                                nosDestination(path) { InformationScreen(path) }
                            }
                        }
                    }
                }
            }
            actionVerse?.let { v ->
                VerseActions(
                    v,
                    repo,
                    state,
                    audio,
                    { actionVerse = null },
                    ::navigate,
                    ::notify,
                )
            }
        }
    }
}
