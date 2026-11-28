package dev.mazzagotte.strike3.data

import dev.mazzagotte.strike3.util.CardText
import dev.mazzagotte.strike3.util.CardPrint
import dev.mazzagotte.strike3.render.EditParams
import dev.mazzagotte.strike3.render.PhotoTools
import dev.mazzagotte.strike3.render.SourceStore
import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class CardRepository(private val context: Context) {
    private val dao = AppDb.create(context).cards()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var backupJob: Job? = null

    private val _backupStatus = MutableStateFlow(DriveBackup.lastStatus(context))
    val backupStatus: StateFlow<String> = _backupStatus

    fun cards(): Flow<List<Card>> = dao.all()
    fun card(id: Long): Flow<Card?> = dao.byId(id)
    suspend fun get(id: Long): Card? = dao.getById(id)

    suspend fun add(card: Card): Long {
        val id = dao.insert(card)
        scheduleBackup()
        ensurePrints()
        return id
    }

    suspend fun update(card: Card) {
        dao.update(card)
        scheduleBackup()
        ensurePrints()
    }

    suspend fun delete(card: Card) {
        dao.delete(card)
        scheduleBackup()
    }

    /** Applies a CSV import: changed cards are replaced in place, new rows are added. */
    suspend fun applyImport(plan: CsvImport.Plan) {
        dao.insertAll(plan.updated)
        plan.added.forEach { dao.insert(it) }
        scheduleBackup()
        ensurePrints()
    }

    suspend fun restore(cards: List<Card>) {
        dao.insertAll(cards)
        scheduleBackup()
        ensurePrints()
    }

    private val printLock = Mutex()
    private val printsWanted = java.util.concurrent.atomic.AtomicBoolean(false)

    /**
     * Makes the photo fingerprints for "Do I have this?" on any card that doesn't have them yet, or
     * whose photos changed. Runs in the background; about a tenth of a second per card.
     */
    fun ensurePrints() {
        scope.launch {
            printsWanted.set(true)
            if (printLock.isLocked) return@launch // a pass is running; it goes round again
            printLock.withLock {
                var again = true
                while (again || printsWanted.getAndSet(false)) {
                    again = false
                    for (c in dao.allNow()) {
                        val key = printKeyOf(c)
                        if (c.printKey == key) continue
                        val front = runCatching { frontPrint(c) }.getOrNull()
                        val back = runCatching { photoPrint(SourceStore.loadScaled(c.backPath, 500)) }.getOrNull()
                        // Re-read just before writing, so an edit made meanwhile isn't lost
                        val fresh = dao.getById(c.id) ?: continue
                        if (printKeyOf(fresh) != key) { again = true; continue }
                        dao.update(fresh.copy(frontPrint = CardPrint.encode(front), backPrint = CardPrint.encode(back), printKey = key))
                    }
                }
            }
        }
    }

    private fun printKeyOf(c: Card) =
        listOf(c.cutoutPath, c.sourcePath, EditParams.fromJson(c.editParams).rotation.toString(), c.backPath, "v1").joinToString("|")

    private fun frontPrint(c: Card): ByteArray? {
        // The front photo turned the way it was set (framed like a "Do I have this?" photo), else the cut-out
        val turn = EditParams.fromJson(c.editParams).rotation
        SourceStore.loadScaled(c.sourcePath, 500)?.let { return photoPrint(PhotoTools.rotate(it, turn)) }
        return photoPrint(SourceStore.loadScaled(c.cutoutPath, 500))
    }

    private fun photoPrint(bmp: Bitmap?): ByteArray? {
        bmp ?: return null
        val px = IntArray(bmp.width * bmp.height)
        bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        return CardPrint.of(px, bmp.width, bmp.height)
    }

    /** Writes the Drive backup a moment after the last change, so quick edits make one write. */
    /**
     * One time after the Card # field arrives: copy card numbers that are already written in Notes
     * (a code like TOTD-22 / BCP-139, or a grading label's "#287") into the new field.
     */
    fun backfillCardNumbers() {
        val prefs = context.getSharedPreferences("migrations", Context.MODE_PRIVATE)
        if (prefs.getBoolean("cardNumbers", false)) return
        scope.launch {
            val changed = dao.allNow().filter { it.cardNumber.isBlank() }.mapNotNull { c ->
                CardText.cardNumberIn(c.notes)?.let { c.copy(cardNumber = it.uppercase()) }
            }
            if (changed.isNotEmpty()) {
                dao.insertAll(changed)
                scheduleBackup()
            }
            prefs.edit().putBoolean("cardNumbers", true).apply()
        }
    }

    /** One time: keep only the print run on numbered cards ("049/199" -> "/199"). */
    fun printRunsOnly() {
        val prefs = context.getSharedPreferences("migrations", Context.MODE_PRIVATE)
        if (prefs.getBoolean("printRuns", false)) return
        scope.launch {
            val changed = dao.allNow().mapNotNull { c ->
                CardText.printRunOnly(c.serial).takeIf { it != c.serial }?.let { c.copy(serial = it) }
            }
            if (changed.isNotEmpty()) {
                dao.insertAll(changed)
                scheduleBackup()
            }
            prefs.edit().putBoolean("printRuns", true).apply()
        }
    }

    /**
     * One time: mark the parallel to check on saved cards where it doesn't fit the print run
     * (a "Gold" /99 Bowman Chrome). Only the check mark changes, never the parallel itself.
     */
    fun flagParallelMismatches() {
        val prefs = context.getSharedPreferences("migrations", Context.MODE_PRIVATE)
        if (prefs.getBoolean("parallelMismatch", false)) return
        scope.launch {
            val changed = dao.allNow().filter { c ->
                CardText.F.PARALLEL !in c.unsure && CardText.parallelConflicts(c.setName, c.serial, c.parallel)
            }.map { it.withUnsure(it.unsure + CardText.F.PARALLEL) }
            if (changed.isNotEmpty()) {
                dao.insertAll(changed)
                scheduleBackup()
            }
            prefs.edit().putBoolean("parallelMismatch", true).apply()
        }
    }

    fun scheduleBackup() {
        backupJob?.cancel()
        backupJob = scope.launch {
            delay(1500)
            backupNow()
        }
    }

    suspend fun backupNow() {
        val status = DriveBackup.write(context, dao.allNow())
        _backupStatus.value = status
    }
}
