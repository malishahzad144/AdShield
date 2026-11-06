package com.adshield

import android.content.Context
import android.net.Uri
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * URL filter for the built-in browser. Supported AdGuard-style syntax: "||domain^", "||domain/path",
 * "@@||domain^" (exception), plain domains, plain URL substrings. Modifiers after '$' are ignored.
 */
object Engine {
    private const val LIST_URL = "https://adguardteam.github.io/AdGuardSDNSFilter/Filters/filter.txt"

    private val ADS = listOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "adservice.google.com",
        "ads.google.com", "adnxs.com", "taboola.com", "outbrain.com", "criteo.com", "criteo.net",
        "adcolony.com", "applovin.com", "moatads.com", "amazon-adsystem.com", "ads.facebook.com",
        "ads.twitter.com", "adsrvr.org", "rubiconproject.com", "pubmatic.com", "openx.net",
        "mopub.com", "inmobi.com", "popads.net", "popcash.net", "propellerads.com", "exoclick.com",
        "juicyads.com", "adsterra.com", "revcontent.com", "mgid.com", "smartadserver.com",
        "casalemedia.com", "yieldmo.com", "teads.tv", "adform.net", "bidswitch.net", "serving-sys.com"
    )
    private val TRACKERS = listOf(
        "google-analytics.com", "googletagmanager.com", "googletagservices.com", "app-measurement.com",
        "scorecardresearch.com", "quantserve.com", "hotjar.com", "mixpanel.com", "segment.io",
        "segment.com", "amplitude.com", "branch.io", "adjust.com", "appsflyer.com", "newrelic.com",
        "bugsnag.com", "fullstory.com", "mouseflow.com", "crazyegg.com", "chartbeat.com",
        "analytics.tiktok.com", "connect.facebook.net", "pixel.facebook.com", "bat.bing.com",
        "clarity.ms", "yandex.ru/metrika", "stats.wp.com", "omtrdc.net", "demdex.net", "krxd.net"
    ).filter { !it.contains('/') }
    private val PATTERNS = listOf(
        "/ads/", "/adserver", "/pagead/", "googleads", "adsbygoogle.js", "/banners/ad", "/doubleclick/"
    )

    @Volatile private var ads = HashSet<String>()
    @Volatile private var trackers = HashSet<String>()
    @Volatile private var exceptions = HashSet<String>()
    @Volatile private var patterns: List<String> = PATTERNS

    fun rebuild(ctx: Context) {
        val a = HashSet(ADS)
        val t = HashSet(TRACKERS)
        val e = HashSet<String>()
        val p = ArrayList(PATTERNS)
        File(ctx.filesDir, "adguard.txt").takeIf { it.exists() }?.forEachLine { if (it.isNotEmpty()) a.add(it) }
        for (r in Store.customRules.value) applyRule(r, a, e, p)
        ads = a; trackers = t; exceptions = e; patterns = p
        Store.ruleCount.value = a.size + t.size + p.size
    }

    private fun applyRule(line: String, a: HashSet<String>, e: HashSet<String>, p: ArrayList<String>) {
        var l = line.trim()
        if (l.isEmpty() || l[0] == '!' || l[0] == '#') return
        val exc = l.startsWith("@@")
        if (exc) l = l.substring(2)
        val d = l.indexOf('$')
        if (d >= 0) l = l.substring(0, d)
        l = l.removePrefix("||").removePrefix("|").removePrefix("https://").removePrefix("http://").trimEnd('^', '|').lowercase()
        if (l.isEmpty() || l.contains('*')) return
        val hostOnly = !l.contains('/') && l.contains('.')
        if (exc) { if (hostOnly) e.add(l); return }
        if (hostOnly) a.add(l) else p.add(l)
    }

    private fun match(set: Set<String>, host: String): Boolean {
        var d = host
        while (true) {
            if (set.contains(d)) return true
            val i = d.indexOf('.')
            if (i < 0) return false
            d = d.substring(i + 1)
        }
    }

    private fun registrable(h: String): String {
        val parts = h.split('.')
        if (parts.size <= 2) return h
        val n = if (parts[parts.size - 1].length == 2 && parts[parts.size - 2].length <= 3) 3 else 2
        return parts.takeLast(n).joinToString(".")
    }

    fun isWhitelisted(host: String?): Boolean = host != null && match(Store.whitelist.value, host.lowercase())

    /** Returns 0 = ad, 1 = tracker, null = allow. */
    fun check(url: Uri, pageHost: String?): Int? {
        val host = url.host?.lowercase() ?: return null
        if (match(exceptions, host)) return null
        if (match(ads, host)) return 0
        if (match(trackers, host) && (pageHost == null || registrable(host) != registrable(pageHost))) return 1
        val u = url.toString().lowercase()
        for (p in patterns) if (u.contains(p)) return 0
        return null
    }

    /** Plain HTTPS download made by the app itself; touches no system network settings. */
    fun update(ctx: Context) {
        try {
            val conn = URL(LIST_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            try {
                if (conn.responseCode != 200) { Store.message.value = "Download failed (HTTP ${conn.responseCode})"; return }
                val out = LinkedHashSet<String>()
                conn.inputStream.bufferedReader().useLines { lines ->
                    for (raw in lines) {
                        val l = raw.trim()
                        if (!l.startsWith("||")) continue
                        val end = l.indexOf('^')
                        if (end < 0) continue
                        val d = l.substring(2, end).lowercase()
                        if (d.contains('.') && d.all { it in 'a'..'z' || it in '0'..'9' || it == '.' || it == '-' || it == '_' }) out.add(d)
                    }
                }
                if (out.size < 1000) { Store.message.value = "List looks invalid; kept previous rules"; return }
                val tmp = File(ctx.filesDir, "adguard.tmp")
                tmp.bufferedWriter().use { w -> out.forEach { w.write(it); w.newLine() } }
                val dest = File(ctx.filesDir, "adguard.txt")
                dest.delete()
                tmp.renameTo(dest)
                rebuild(ctx)
                Store.setLastUpdate(System.currentTimeMillis())
                Store.message.value = "Filter list updated"
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Store.message.value = "Update failed: ${e.message ?: e.javaClass.simpleName}"
        }
    }
}
