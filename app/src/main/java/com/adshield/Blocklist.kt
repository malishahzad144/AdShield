package com.adshield

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object Blocklist {
    private val URLS = mapOf(
        "adguard" to "https://adguardteam.github.io/AdGuardSDNSFilter/Filters/filter.txt",
        "oisd" to "https://small.oisd.nl/domainswild2"
    )
    // Minimal built-in fallback so blocking works before the first list download.
    private val BUILTIN = listOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "adservice.google.com",
        "ads.google.com", "app-measurement.com", "adnxs.com", "taboola.com", "outbrain.com",
        "criteo.com", "scorecardresearch.com", "adcolony.com", "unityads.unity3d.com",
        "applovin.com", "moatads.com", "amazon-adsystem.com", "ads.facebook.com",
        "an.facebook.com", "ads.twitter.com", "analytics.tiktok.com", "adsrvr.org",
        "rubiconproject.com", "pubmatic.com", "openx.net", "mopub.com", "inmobi.com"
    )

    @Volatile private var set: HashSet<String> = HashSet(BUILTIN)

    private fun ids(src: String) = if (src == "both") listOf("adguard", "oisd") else listOf(src)

    fun load(ctx: Context) {
        val s = HashSet(BUILTIN)
        for (id in ids(ShieldState.source.value)) {
            val f = File(ctx.filesDir, "$id.txt")
            if (f.exists()) f.forEachLine { if (it.isNotEmpty()) s.add(it) }
        }
        set = s
        ShieldState.listSize.value = s.size
    }

    /** Downloads selected lists. Returns an error message, or null on success. */
    fun update(ctx: Context): String? {
        try {
            for (id in ids(ShieldState.source.value)) {
                val conn = URL(URLS.getValue(id)).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                try {
                    if (conn.responseCode != 200) return "Download failed for $id (HTTP ${conn.responseCode})"
                    val domains = LinkedHashSet<String>()
                    conn.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { l -> parse(l)?.let { domains.add(it) } }
                    }
                    if (domains.size < 1000) return "List $id looks empty or invalid; kept previous list"
                    val tmp = File(ctx.filesDir, "$id.tmp")
                    tmp.bufferedWriter().use { w -> domains.forEach { w.write(it); w.newLine() } }
                    val dest = File(ctx.filesDir, "$id.txt")
                    dest.delete()
                    tmp.renameTo(dest)
                } finally {
                    conn.disconnect()
                }
            }
            load(ctx)
            ShieldState.lastUpdate.value = System.currentTimeMillis()
            ShieldState.persist()
            return null
        } catch (e: Exception) {
            return "Update failed: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    /** Handles hosts files, AdGuard "||domain^" rules and plain/wildcard domain lists. */
    internal fun parse(raw: String): String? {
        val l = raw.trim()
        if (l.isEmpty() || l[0] == '#' || l[0] == '!' || l[0] == '[' || l.startsWith("@@")) return null
        val d: String
        if (l.startsWith("||")) {
            val rest = l.substring(2)
            val end = rest.indexOfFirst { it == '^' || it == '/' || it == '$' }
            if (end >= 0 && rest[end] == '/') return null
            d = if (end >= 0) rest.substring(0, end) else rest
        } else if (l.contains(' ') || l.contains('\t')) {
            d = l.split(' ', '\t').filter { it.isNotEmpty() }.getOrNull(1) ?: return null
        } else {
            d = l
        }
        val name = d.removePrefix("*.").lowercase().trimEnd('.')
        if (!name.contains('.') || name.length > 253) return null
        if (!name.all { it in 'a'..'z' || it in '0'..'9' || it == '.' || it == '-' || it == '_' }) return null
        return name
    }

    fun isBlocked(name: String): Boolean {
        var d = name.lowercase().trimEnd('.')
        val s = set
        while (true) {
            if (s.contains(d)) return true
            val i = d.indexOf('.')
            if (i < 0) return false
            d = d.substring(i + 1)
        }
    }
}
