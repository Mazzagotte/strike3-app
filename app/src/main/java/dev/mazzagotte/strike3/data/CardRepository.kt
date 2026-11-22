package dev.mazzagotte.strike3.data

import android.content.Context
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
        return id
    }

    suspend fun update(card: Card) {
        dao.update(card)
        scheduleBackup()
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
    }

    suspend fun restore(cards: List<Card>) {
        dao.insertAll(cards)
        scheduleBackup()
    }

    /** Writes the Drive backup a moment after the last change, so quick edits make one write. */
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
