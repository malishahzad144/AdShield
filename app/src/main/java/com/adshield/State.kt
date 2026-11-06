package com.adshield

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Process-wide state + persisted settings/statistics. Local only; nothing leaves the device. */
object ShieldState {
    private var sp: SharedPreferences? = null
    val running = MutableStateFlow(false)
    val blocked = MutableStateFlow(0L)
    val total = MutableStateFlow(0L)
    val listSize = MutableStateFlow(0)
    val error = MutableStateFlow<String?>(null)
    val updating = MutableStateFlow(false)
    val source = MutableStateFlow("both") // adguard | oisd | both
    val lastUpdate = MutableStateFlow(0L)

    @Synchronized
    fun init(ctx: Context) {
        if (sp != null) return
        val p = ctx.applicationContext.getSharedPreferences("adshield", Context.MODE_PRIVATE)
        sp = p
        blocked.value = p.getLong("blocked", 0)
        total.value = p.getLong("total", 0)
        source.value = p.getString("source", "both") ?: "both"
        lastUpdate.value = p.getLong("lastUpdate", 0)
    }

    fun persist() {
        sp?.edit()?.putLong("blocked", blocked.value)?.putLong("total", total.value)
            ?.putString("source", source.value)?.putLong("lastUpdate", lastUpdate.value)?.apply()
    }

    fun count(wasBlocked: Boolean) {
        total.update { it + 1 }
        if (wasBlocked) blocked.update { it + 1 }
        if (total.value % 25L == 0L) persist()
    }

    fun resetStats() { blocked.value = 0; total.value = 0; persist() }
}
