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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    /** Editor settings (JSON) so the post can be re-edited later */
    val editParams: String? = null,
    /** App-private copies used for re-editing and exports */
    val sourcePath: String? = null,
    val cutoutPath: String? = null,
    /** Carousel slide 2 photos (app-private) */
    val backPath: String? = null,
    val closeUpPath: String? = null,
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

@Database(entities = [Card::class], version = 2, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun cards(): CardDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf("editParams", "sourcePath", "cutoutPath", "backPath", "closeUpPath").forEach {
                    db.execSQL("ALTER TABLE cards ADD COLUMN $it TEXT")
                }
            }
        }

        fun create(context: Context): AppDb =
            Room.databaseBuilder(context, AppDb::class.java, "strike3.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
