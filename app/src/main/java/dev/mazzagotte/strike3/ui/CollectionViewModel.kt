package dev.mazzagotte.strike3.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import dev.mazzagotte.strike3.data.Card
import dev.mazzagotte.strike3.util.CardSearch
import dev.mazzagotte.strike3.util.SortBy

/** Grid layout on the collection screen. */
enum class GridLayout(val label: String) { TWO("2 columns"), THREE("3 columns"), LIST("List") }

/**
 * Collection search, filters and view options. Lives for the whole app session, so your search
 * is still there when you come back from a card. View options (sort, layout, photos vs posts,
 * grouping) are remembered between launches.
 */
class CollectionViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("collection", Context.MODE_PRIVATE)

    var query by mutableStateOf("")

    // Quick filters (all that are on must match)
    var autos by mutableStateOf(false)
    var rookies by mutableStateOf(false)
    var numbered by mutableStateOf(false)
    var oneOfOnes by mutableStateOf(false)
    var needsReview by mutableStateOf(false)
    /** null = either, true = posted only, false = not posted only */
    var posted by mutableStateOf<Boolean?>(null)

    // Pick-list filters
    var team by mutableStateOf<String?>(null)
    var set by mutableStateOf<String?>(null)
    var year by mutableStateOf<String?>(null)

    var sort by mutableStateOf(enumPref("sort", SortBy.NEWEST))
        private set
    var layout by mutableStateOf(enumPref("layout", GridLayout.TWO))
        private set
    /** true: show the finished post when there is one; false: always the card's front photo */
    var showPosts by mutableStateOf(prefs.getBoolean("showPosts", false))
        private set
    var groupByPlayer by mutableStateOf(prefs.getBoolean("group", false))
        private set

    fun updateSort(v: SortBy) { sort = v; prefs.edit().putString("sort", v.name).apply() }
    fun updateLayout(v: GridLayout) { layout = v; prefs.edit().putString("layout", v.name).apply() }
    fun updateShowPosts(v: Boolean) { showPosts = v; prefs.edit().putBoolean("showPosts", v).apply() }
    fun updateGroup(v: Boolean) { groupByPlayer = v; prefs.edit().putBoolean("group", v).apply() }

    val anyFilter: Boolean
        get() = needsReview || autos || rookies || numbered || oneOfOnes || posted != null || team != null || set != null || year != null

    fun clearFilters() {
        needsReview = false; autos = false; rookies = false; numbered = false; oneOfOnes = false; posted = null
        team = null; set = null; year = null
    }

    /** From a card page: show every card of that player. */
    fun showPlayer(player: String) {
        clearFilters()
        query = player
    }

    fun apply(cards: List<Card>): List<Card> {
        val shown = cards.filter { c ->
            CardSearch.matches(c, query) &&
                (!needsReview || c.needsReview) &&
                (!autos || c.isAuto) &&
                (!rookies || c.isRookie) &&
                (!numbered || c.serial.isNotBlank()) &&
                (!oneOfOnes || CardSearch.isOneOfOne(c)) &&
                (posted == null || c.posted == posted) &&
                (team == null || c.team.trim().equals(team, ignoreCase = true)) &&
                (set == null || c.setName.trim().equals(set, ignoreCase = true)) &&
                (year == null || c.year.trim() == year)
        }
        return CardSearch.sort(shown, sort)
    }

    private inline fun <reified T : Enum<T>> enumPref(key: String, default: T): T =
        prefs.getString(key, null)?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: default
}
