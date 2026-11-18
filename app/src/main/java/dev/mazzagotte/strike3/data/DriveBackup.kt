package dev.mazzagotte.strike3.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date

/**
 * Keeps two files in a Google Drive folder up to date:
 *  - strike3-backup.json  (full log, used to restore)
 *  - strike3-collection.csv (opens in Google Sheets to browse on a PC)
 * The files are created once through the system file picker (choose Google Drive),
 * and the app keeps permission to rewrite them after every change.
 */
object DriveBackup {
    private const val PREFS = "drive_backup"
    private const val KEY_JSON = "json_uri"
    private const val KEY_CSV = "csv_uri"
    private const val KEY_STATUS = "status"

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun jsonUri(c: Context): Uri? = prefs(c).getString(KEY_JSON, null)?.let(Uri::parse)
    fun csvUri(c: Context): Uri? = prefs(c).getString(KEY_CSV, null)?.let(Uri::parse)
    fun isSetUp(c: Context) = jsonUri(c) != null

    fun lastStatus(c: Context): String =
        prefs(c).getString(KEY_STATUS, null) ?: "Drive backup isn't set up yet."

    fun setJsonUri(c: Context, uri: Uri) = remember(c, KEY_JSON, uri)
    fun setCsvUri(c: Context, uri: Uri) = remember(c, KEY_CSV, uri)

    private fun remember(c: Context, key: String, uri: Uri) {
        try {
            c.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
            // Some providers grant access without a persistable permission; keep going.
        }
        prefs(c).edit().putString(key, uri.toString()).apply()
    }

    fun write(c: Context, cards: List<Card>): String {
        val json = jsonUri(c) ?: return lastStatus(c)
        val status = try {
            writeText(c, json, toJson(cards).toString(2))
            csvUri(c)?.let { writeText(c, it, toCsv(cards)) }
            "Backed up ${cards.size} card${if (cards.size == 1) "" else "s"} to Drive · " +
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date())
        } catch (e: Exception) {
            "Last backup failed: ${e.message ?: "unknown error"}. Open Settings to set it up again."
        }
        prefs(c).edit().putString(KEY_STATUS, status).apply()
        return status
    }

    private fun writeText(c: Context, uri: Uri, text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        // "wt" truncates; fall back to "w" for providers that don't accept it.
        val out = try {
            c.contentResolver.openOutputStream(uri, "wt")
        } catch (_: Exception) {
            c.contentResolver.openOutputStream(uri, "w")
        } ?: error("Drive file is unavailable")
        out.use { it.write(bytes) }
    }

    fun readBackup(c: Context, uri: Uri): List<Card> {
        val text = c.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: error("Couldn't open that file")
        val root = JSONObject(text)
        val arr = root.getJSONArray("cards")
        return (0 until arr.length()).map { i -> fromJson(arr.getJSONObject(i)) }
    }

    private fun toJson(cards: List<Card>) = JSONObject().apply {
        put("app", "Strike III")
        put("version", 1)
        put("exportedAt", System.currentTimeMillis())
        put("cards", JSONArray().apply {
            cards.forEach { card ->
                put(JSONObject().apply {
                    put("id", card.id)
                    put("player", card.player)
                    put("year", card.year)
                    put("setName", card.setName)
                    put("parallel", card.parallel)
                    put("serial", card.serial)
                    put("team", card.team)
                    put("isAuto", card.isAuto)
                    put("isRookie", card.isRookie)
                    put("isPatch", card.isPatch)
                    put("dateAcquired", card.dateAcquired)
                    put("pricePaid", card.pricePaid)
                    put("posted", card.posted)
                    put("notes", card.notes)
                    put("postUri", card.postUri ?: JSONObject.NULL)
                    put("originalUri", card.originalUri ?: JSONObject.NULL)
                    put("createdAt", card.createdAt)
                })
            }
        })
    }

    private fun fromJson(o: JSONObject) = Card(
        id = o.optLong("id"),
        player = o.optString("player"),
        year = o.optString("year"),
        setName = o.optString("setName"),
        parallel = o.optString("parallel"),
        serial = o.optString("serial"),
        team = o.optString("team"),
        isAuto = o.optBoolean("isAuto"),
        isRookie = o.optBoolean("isRookie"),
        isPatch = o.optBoolean("isPatch"),
        dateAcquired = o.optString("dateAcquired"),
        pricePaid = o.optString("pricePaid"),
        posted = o.optBoolean("posted"),
        notes = o.optString("notes"),
        postUri = if (o.isNull("postUri")) null else o.optString("postUri"),
        originalUri = if (o.isNull("originalUri")) null else o.optString("originalUri"),
        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
    )

    private fun toCsv(cards: List<Card>): String {
        val header = listOf(
            "Player", "Year", "Set", "Parallel", "Serial", "Team", "Auto", "Rookie", "Patch",
            "Date acquired", "Price paid", "Posted", "Notes",
        )
        val rows = cards.map { c ->
            listOf(
                c.player, c.year, c.setName, c.parallel, c.serial, c.team,
                yes(c.isAuto), yes(c.isRookie), yes(c.isPatch),
                c.dateAcquired, c.pricePaid, yes(c.posted), c.notes,
            )
        }
        return (listOf(header) + rows).joinToString("\r\n") { row -> row.joinToString(",") { csv(it) } }
    }

    private fun yes(b: Boolean) = if (b) "Yes" else ""

    private fun csv(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
}
