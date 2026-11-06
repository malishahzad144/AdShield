package com.adshield

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Persisted settings + statistics. Local only. */
object Store {
    private var sp: SharedPreferences? = null
    val filterOn = MutableStateFlow(true)
    val distractionFree = MutableStateFlow(false)
    val blockCookies = MutableStateFlow(true)
    val theme = MutableStateFlow(0) // 0 system, 1 dark, 2 light
    val ads = MutableStateFlow(0L)
    val trackers = MutableStateFlow(0L)
    val popups = MutableStateFlow(0L)
    val whitelist = MutableStateFlow<Set<String>>(emptySet())
    val customRules = MutableStateFlow<Set<String>>(emptySet())
    val ruleCount = MutableStateFlow(0)
    val lastUpdate = MutableStateFlow(0L)
    val updating = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    val currentUrl = MutableStateFlow("")
    val progress = MutableStateFlow(100)

    @Synchronized
    fun init(ctx: Context) {
        if (sp != null) return
        val p = ctx.applicationContext.getSharedPreferences("adshield_web", Context.MODE_PRIVATE)
        sp = p
        filterOn.value = p.getBoolean("filter", true)
        distractionFree.value = p.getBoolean("df", false)
        blockCookies.value = p.getBoolean("cookies", true)
        theme.value = p.getInt("theme", 0)
        ads.value = p.getLong("ads", 0)
        trackers.value = p.getLong("trackers", 0)
        popups.value = p.getLong("popups", 0)
        lastUpdate.value = p.getLong("lastUpdate", 0)
        whitelist.value = HashSet(p.getStringSet("whitelist", emptySet()) ?: emptySet())
        customRules.value = HashSet(p.getStringSet("rules", emptySet()) ?: emptySet())
    }

    private fun edit(f: SharedPreferences.Editor.() -> Unit) { sp?.edit()?.apply(f)?.apply() }

    fun setFilter(v: Boolean) { filterOn.value = v; edit { putBoolean("filter", v) } }
    fun setDistractionFree(v: Boolean) { distractionFree.value = v; edit { putBoolean("df", v) } }
    fun setBlockCookies(v: Boolean) { blockCookies.value = v; edit { putBoolean("cookies", v) } }
    fun setTheme(v: Int) { theme.value = v; edit { putInt("theme", v) } }
    fun setLastUpdate(t: Long) { lastUpdate.value = t; edit { putLong("lastUpdate", t) } }

    fun toggleWhitelist(host: String?) {
        if (host.isNullOrEmpty()) return
        val h = host.lowercase()
        whitelist.value = if (h in whitelist.value) whitelist.value - h else whitelist.value + h
        edit { putStringSet("whitelist", HashSet(whitelist.value)) }
    }

    fun addRule(r: String) { customRules.value = customRules.value + r; edit { putStringSet("rules", HashSet(customRules.value)) } }
    fun removeRule(r: String) { customRules.value = customRules.value - r; edit { putStringSet("rules", HashSet(customRules.value)) } }

    /** kind: 0 ad, 1 tracker, 2 popup */
    fun hit(kind: Int) {
        when (kind) {
            0 -> ads.update { it + 1 }
            1 -> trackers.update { it + 1 }
            else -> popups.update { it + 1 }
        }
        if ((ads.value + trackers.value + popups.value) % 20L == 0L) saveStats()
    }

    fun saveStats() { edit { putLong("ads", ads.value); putLong("trackers", trackers.value); putLong("popups", popups.value) } }
    fun resetStats() { ads.value = 0; trackers.value = 0; popups.value = 0; saveStats() }
}
