package dev.mazzagotte.strike3.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

sealed interface Screen {
    data object Collection : Screen
    /** catalog: shoot the front for the collection only, no post yet. */
    data class Capture(val catalog: Boolean = false) : Screen
    data object Editor : Screen
    /** editId == null: new card, from the post just made or (catalog) from the front photo only. */
    data class Details(val editId: Long?, val catalog: Boolean = false) : Screen
    /** [ids]: the collection results it was opened from, for swiping between cards. */
    data class CardView(val id: Long, val ids: List<Long> = listOf(id)) : Screen
    data object Settings : Screen
}

class NavViewModel : ViewModel() {
    private val stack = mutableStateListOf<Screen>(Screen.Collection)
    val current: Screen get() = stack.last()
    val canGoBack: Boolean get() = stack.size > 1

    fun go(s: Screen) { stack.add(s) }
    /** Swaps the screen on top without adding to the back stack (e.g. which card is showing). */
    fun replaceTop(s: Screen) { stack[stack.lastIndex] = s }
    fun back() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    fun resetTo(s: Screen) {
        stack.clear()
        stack.add(Screen.Collection)
        if (s != Screen.Collection) stack.add(s)
    }
}

@Composable
fun AppRoot(
    nav: NavViewModel = viewModel(),
    editor: EditorViewModel = viewModel(),
    collection: CollectionViewModel = viewModel(),
) {
    BackHandler(enabled = nav.canGoBack) { nav.back() }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (val s = nav.current) {
            Screen.Collection -> CollectionScreen(
                onNewPost = { nav.go(Screen.Capture()) },
                onAddCard = { nav.go(Screen.Capture(catalog = true)) },
                vm = collection,
                onOpen = { id, ids -> nav.go(Screen.CardView(id, ids)) },
                onSettings = { nav.go(Screen.Settings) },
            )
            is Screen.Capture -> CaptureScreen(
                onPhoto = { uri, frame ->
                    editor.load(uri, frame)
                    nav.go(if (s.catalog) Screen.Details(null, catalog = true) else Screen.Editor)
                },
                onBack = nav::back,
            )
            Screen.Editor -> EditorScreen(
                vm = editor,
                onNext = { nav.go(Screen.Details(null)) },
                onSavedEdit = nav::back,
                onBack = nav::back,
            )
            is Screen.Details -> DetailsScreen(
                editId = s.editId,
                catalog = s.catalog,
                editor = editor,
                onDone = { id -> nav.resetTo(Screen.CardView(id)) },
                onBack = nav::back,
            )
            is Screen.CardView -> CardPagerScreen(
                ids = s.ids,
                startId = s.id,
                // Remember which card is showing, so coming back from editing lands on it
                onPageChange = { id -> if (id != s.id && nav.current == s) nav.replaceTop(s.copy(id = id)) },
                onEdit = { id -> nav.go(Screen.Details(id)) },
                onEditPost = { card ->
                    editor.loadForCard(card)
                    nav.go(Screen.Editor)
                },
                onBack = nav::back,
                onShowPlayer = { player ->
                    collection.showPlayer(player)
                    nav.resetTo(Screen.Collection)
                },
            )
            Screen.Settings -> SettingsScreen(onBack = nav::back)
        }
    }
}
