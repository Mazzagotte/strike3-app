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
    data object Capture : Screen
    data object Editor : Screen
    /** editId == null: new card from the post just made. */
    data class Details(val editId: Long?) : Screen
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
                onNewPost = { nav.go(Screen.Capture) },
                onOpen = { nav.go(Screen.CardView(it)) },
                onSettings = { nav.go(Screen.Settings) },
            )
            Screen.Capture -> CaptureScreen(
                onPhoto = { uri ->
                    editor.load(uri)
                    nav.go(Screen.Editor)
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
