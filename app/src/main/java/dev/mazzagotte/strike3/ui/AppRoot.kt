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
    data class CardView(val id: Long) : Screen
    data object Settings : Screen
}

class NavViewModel : ViewModel() {
    private val stack = mutableStateListOf<Screen>(Screen.Collection)
    val current: Screen get() = stack.last()
    val canGoBack: Boolean get() = stack.size > 1

    fun go(s: Screen) { stack.add(s) }
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
) {
    BackHandler(enabled = nav.canGoBack) { nav.back() }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (val s = nav.current) {
            Screen.Collection -> CollectionScreen(
                onNewPost = { nav.go(Screen.Capture()) },
                onAddCard = { nav.go(Screen.Capture(catalog = true)) },
                onOpen = { nav.go(Screen.CardView(it)) },
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
            is Screen.CardView -> CardScreen(
                id = s.id,
                onEdit = { nav.go(Screen.Details(s.id)) },
                onEditPost = { card ->
                    editor.loadForCard(card)
                    nav.go(Screen.Editor)
                },
                onBack = nav::back,
            )
            Screen.Settings -> SettingsScreen(onBack = nav::back)
        }
    }
}
