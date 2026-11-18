package dev.mazzagotte.strike3.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "cards")
data class Card(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val player: String = "",
    val year: String = "",
    val setName: String = "",
    val parallel: String = "",
    val serial: String = "",
    val team: String = "",
    val isAuto: Boolean = false,
    val isRookie: Boolean = false,
    val isPatch: Boolean = false,
    val dateAcquired: String = "",
    val pricePaid: String = "",
    val posted: Boolean = false,
    val notes: String = "",
    /** Finished Instagram post in Pictures/Strike III */
    val postUri: String? = null,
    /** Original photo of the card */
    val originalUri: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val title: String
        get() = player.ifBlank { "Untitled card" }

    val subtitle: String
        get() = listOf(year, setName, parallel).filter { it.isNotBlank() }.joinToString(" ")
}

@Dao
interface CardDao {
    @Query("SELECT * FROM cards ORDER BY createdAt DESC")
    fun all(): Flow<List<Card>>

    @Query("SELECT * FROM cards ORDER BY createdAt DESC")
    suspend fun allNow(): List<Card>

    @Query("SELECT * FROM cards WHERE id = :id")
    fun byId(id: Long): Flow<Card?>

    @Query("SELECT * FROM cards WHERE id = :id")
    suspend fun getById(id: Long): Card?

    @Insert
    suspend fun insert(card: Card): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(cards: List<Card>)

    @Update
    suspend fun update(card: Card)

    @Delete
    suspend fun delete(card: Card)
}

@Database(entities = [Card::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun cards(): CardDao

    companion object {
        fun create(context: Context): AppDb =
            Room.databaseBuilder(context, AppDb::class.java, "strike3.db").build()
    }
}
