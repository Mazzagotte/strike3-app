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
    /** What the card is worth now, as typed ("45", "$1,200"). Replaces price paid in the app. */
    val currentValue: String = "",
    /** Added in a batch and the details haven't been checked yet. */
    val needsReview: Boolean = false,
    /** The card's number/code as printed, e.g. "TOTD-22", "BCP-139", "US325", "287". */
    val cardNumber: String = "",
    /** Text the reader saw on the photos (front, then back after a "---BACK---" line), kept so
     *  reader fixes can be re-run on saved cards without new photos. Not shown in the app. */
    val scanText: String = "",
    /** Fields the card reader guessed and you haven't checked yet ("year,serial"). */
    val unsureFields: String = "",
) {
    val unsure: Set<String>
        get() = unsureFields.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    fun withUnsure(fields: Set<String>) = copy(unsureFields = fields.sorted().joinToString(","))

    val title: String
        get() = player.ifBlank { "Untitled card" }

    /** [currentValue] as a number, or null when blank or unreadable. */
    val valueNumber: Double?
        get() = currentValue.filter { it.isDigit() || it == '.' }.toDoubleOrNull()

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

@Database(entities = [Card::class], version = 6, exportSchema = false)
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

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cards ADD COLUMN currentValue TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE cards ADD COLUMN needsReview INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cards ADD COLUMN unsureFields TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cards ADD COLUMN cardNumber TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cards ADD COLUMN scanText TEXT NOT NULL DEFAULT ''")
            }
        }

        fun create(context: Context): AppDb =
            Room.databaseBuilder(context, AppDb::class.java, "strike3.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .build()
    }
}
