package dev.mazzagotte.strike3.ui

import android.app.Application
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.mazzagotte.strike3.Strike3App
import dev.mazzagotte.strike3.data.Card
import dev.mazzagotte.strike3.render.FrameCrop
import dev.mazzagotte.strike3.render.SourceStore
import dev.mazzagotte.strike3.util.CardReader
import dev.mazzagotte.strike3.util.Media
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Batch add: shoot front, back, next card, without stopping. Each card is read and saved in the
 * background (marked "Needs details"), so the camera is ready again right away.
 */
class BatchViewModel(app: Application) : AndroidViewModel(app) {
    var onBack by mutableStateOf(false)
        private set
    /** Cards finished (saved) this batch. */
    var saved by mutableIntStateOf(0)
        private set
    /** Cards still being read and saved. */
    var working by mutableIntStateOf(0)
        private set
    var problem by mutableStateOf<String?>(null)
        private set

    private var front: Pair<Uri, FrameCrop?>? = null
    private val one = Mutex()

    /** Which card the camera is on (1-based). */
    val cardNumber: Int get() = saved + working + 1

    fun start() {
        onBack = false
        front = null
        saved = 0
        working = 0
        problem = null
    }

    fun photo(uri: Uri, frame: FrameCrop?) {
        if (!onBack) {
            front = uri to frame
            onBack = true
        } else {
            finishCard(uri to frame)
        }
    }

    /** No back photo for this card (or you'd rather add it later). */
    fun skipBack() {
        if (onBack) finishCard(null)
    }

    private fun finishCard(back: Pair<Uri, FrameCrop?>?) {
        val f = front ?: return
        front = null
        onBack = false
        val n = saved + working + 1
        working++
        viewModelScope.launch {
            one.withLock {
                try {
                    save(f, back)
                    saved++
                } catch (e: Exception) {
                    problem = "Card $n couldn't be saved: ${e.message}"
                } finally {
                    working--
                }
            }
        }
    }

    private suspend fun save(f: Pair<Uri, FrameCrop?>, back: Pair<Uri, FrameCrop?>?) {
        val app = getApplication<Application>()
        val repo = (app as Strike3App).repo
        val frontBmp = loadCardPhoto(app, f.first, f.second)
        val backBmp = back?.let { loadCardPhoto(app, it.first, it.second) }
        val guess = CardReader.readBoth(frontBmp, backBmp, repo.cards().first().map { it.player })
        val name = "strike3-" + (guess.player.filter { it.isLetterOrDigit() }.ifBlank { "card" }) + "-" + Media.stamp()
        val (frontPath, backPath) = withContext(Dispatchers.IO) {
            SourceStore.save(app, frontBmp, name) to backBmp?.let { SourceStore.save(app, it, "back-" + Media.stamp()) }
        }
        repo.add(
            Card(
                player = guess.player,
                year = guess.year,
                setName = guess.setName,
                serial = guess.serial,
                team = guess.team,
                isAuto = guess.isAuto,
                isRookie = guess.isRookie,
                dateAcquired = LocalDate.now().toString(),
                originalUri = f.first.toString(),
                // Photos are already turned upright, so no editor settings are needed yet
                sourcePath = frontPath,
                backPath = backPath,
                needsReview = true,
            ),
        )
    }
}

@Composable
fun BatchScreen(vm: BatchViewModel, onDone: (added: Int) -> Unit) {
    val finish = {
        // A front without its back yet is still saved
        vm.skipBack()
        onDone(vm.saved + vm.working)
    }
    BackHandler { finish() }
    Box(Modifier.fillMaxSize()) {
        CaptureScreen(
            onPhoto = { uri, frame -> vm.photo(uri, frame) },
            onBack = finish,
            hint = if (vm.onBack) "Card ${vm.cardNumber}: now the BACK. Tap to focus."
            else "Card ${vm.cardNumber}: line up the FRONT. Tap to focus.",
        )
        Column(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().systemBarsPadding().padding(top = 64.dp, start = 16.dp, end = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val status = buildString {
                append(if (vm.onBack) "BACK of card ${vm.cardNumber}" else "FRONT of card ${vm.cardNumber}")
                append(" · ${vm.saved} saved")
                if (vm.working > 0) append(" · reading ${vm.working}")
            }
            Text(
                status,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.background(Color.Black.copy(alpha = .6f), RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 6.dp),
            )
            vm.problem?.let {
                Text(it, color = Color(0xFFFFB4AB), textAlign = TextAlign.Center)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (vm.onBack) {
                    OutlinedButton(onClick = { vm.skipBack() }) { Text("No back, next card", color = Color.White) }
                }
                Button(onClick = finish) { Text("Done") }
            }
        }
    }
}
