package com.adshield

import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.isSystemInDarkTheme
import java.text.DateFormat
import java.util.Date

private val DarkS = darkColorScheme(
    primary = Color(0xFF4ADE80), onPrimary = Color(0xFF05210F), background = Color(0xFF0B0F14),
    surface = Color(0xFF131A22), surfaceVariant = Color(0xFF1B2530), onSurface = Color(0xFFE6EDF3),
    onBackground = Color(0xFFE6EDF3), onSurfaceVariant = Color(0xFF9AA7B4)
)
private val LightS = lightColorScheme(
    primary = Color(0xFF15803D), onPrimary = Color.White, background = Color(0xFFFAFAFA),
    surface = Color.White, surfaceVariant = Color(0xFFEEF2F6)
)

@Composable
fun AdShieldTheme(content: @Composable () -> Unit) {
    val t by Store.theme.collectAsState()
    val dark = when (t) { 1 -> true; 2 -> false; else -> isSystemInDarkTheme() }
    MaterialTheme(colorScheme = if (dark) DarkS else LightS, content = content)
}

@Composable
fun App(act: MainActivity) {
    var tab by remember { mutableIntStateOf(0) }
    BackHandler {
        if (tab == 1) tab = 0 else if (act.web.canGoBack()) act.web.goBack() else act.finish()
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Text("🌐") }, label = { Text("Browser") })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Text("🛡") }, label = { Text("Shield") })
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (tab == 0) BrowserTab(act) else ShieldTab(act)
        }
    }
}

@Composable
fun BrowserTab(act: MainActivity) {
    val url by Store.currentUrl.collectAsState()
    val progress by Store.progress.collectAsState()
    val on by Store.filterOn.collectAsState()
    val ads by Store.ads.collectAsState()
    val trackers by Store.trackers.collectAsState()
    val popups by Store.popups.collectAsState()
    val wl by Store.whitelist.collectAsState()
    var input by remember(url) { mutableStateOf(url) }
    val host = remember(url) { Uri.parse(url).host?.lowercase() }
    val listed = host != null && wl.contains(host)

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input, onValueChange = { input = it }, singleLine = true,
                modifier = Modifier.weight(1f), placeholder = { Text("Search or enter address") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go, keyboardType = KeyboardType.Uri),
                keyboardActions = KeyboardActions(onGo = { act.go(input) })
            )
            TextButton(onClick = { act.web.reload() }) { Text("⟳", fontSize = 20.sp) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = on, onCheckedChange = { Store.setFilter(it); act.applyCookiePolicy(); act.web.reload() })
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(if (on) "Filtering on" else "Filtering off", fontWeight = FontWeight.Medium)
                Text("$ads ads · $trackers trackers · $popups popups", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(enabled = host != null, onClick = { Store.toggleWhitelist(host); act.web.reload() }) {
                Text(if (listed) "Remove from whitelist" else "Whitelist site", fontSize = 12.sp)
            }
        }
        if (progress < 100) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { _ ->
                (act.web.parent as? ViewGroup)?.removeView(act.web)
                act.web
            }
        )
    }
}

@Composable
private fun Section(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
private fun SwitchRow(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Stat(label: String, value: Long, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp)) {
            Text("%,d".format(value), fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ShieldTab(act: MainActivity) {
    val ads by Store.ads.collectAsState()
    val trackers by Store.trackers.collectAsState()
    val popups by Store.popups.collectAsState()
    val on by Store.filterOn.collectAsState()
    val df by Store.distractionFree.collectAsState()
    val cookies by Store.blockCookies.collectAsState()
    val theme by Store.theme.collectAsState()
    val rules by Store.customRules.collectAsState()
    val wl by Store.whitelist.collectAsState()
    val count by Store.ruleCount.collectAsState()
    val last by Store.lastUpdate.collectAsState()
    val updating by Store.updating.collectAsState()
    val msg by Store.message.collectAsState()
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    var rule by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("AdShield", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat("Ads blocked", ads, Modifier.weight(1f))
            Stat("Trackers", trackers, Modifier.weight(1f))
            Stat("Popups", popups, Modifier.weight(1f))
        }
        TextButton(onClick = { Store.resetStats() }) { Text("Reset statistics") }

        Section {
            SwitchRow("Ad & tracker filtering", "Block ad/tracker requests in the AdShield browser", on) { Store.setFilter(it); act.applyCookiePolicy() }
            SwitchRow("Distraction-free mode", "Also hide floating banners, cookie/newsletter pop-ups, and block automatic redirects", df) { Store.setDistractionFree(it) }
            SwitchRow("Block third-party cookies", "Stops cross-site tracking cookies while filtering is on", cookies) { Store.setBlockCookies(it); act.applyCookiePolicy() }
            Text("Theme", fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "System", 1 to "Dark", 2 to "Light").forEach { (id, label) ->
                    FilterChip(selected = theme == id, onClick = { Store.setTheme(id) }, label = { Text(label) })
                }
            }
        }

        Section {
            Text("Filter rules", fontWeight = FontWeight.SemiBold)
            val stamp = if (last > 0) " · updated " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(last)) else " · built-in rules"
            Text("%,d rules".format(count) + stamp, fontSize = 13.sp, color = dim)
            Button(onClick = { act.updateLists() }, enabled = !updating) {
                if (updating) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Text("Download AdGuard rules")
            }
            msg?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary) }
            Text("The list is fetched by the app over normal HTTPS. No system or network settings are touched.", fontSize = 12.sp, color = dim)
        }

        Section {
            Text("Custom blocklist", fontWeight = FontWeight.SemiBold)
            Text("Examples: ads.example.com · ||tracker.example.com^ · /banner/ads/ · @@||allowed.com^ (exception)", fontSize = 12.sp, color = dim)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = rule, onValueChange = { rule = it }, singleLine = true, modifier = Modifier.weight(1f), placeholder = { Text("Domain or rule") })
                Spacer(Modifier.width(8.dp))
                Button(onClick = { if (rule.isNotBlank()) { Store.addRule(rule.trim()); rule = ""; act.rebuildRules() } }) { Text("Add") }
            }
            rules.sorted().forEach { r ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(r, Modifier.weight(1f), fontSize = 14.sp)
                    TextButton(onClick = { Store.removeRule(r); act.rebuildRules() }) { Text("✕") }
                }
            }
        }

        Section {
            Text("Whitelisted sites", fontWeight = FontWeight.SemiBold)
            if (wl.isEmpty()) Text("None. Use “Whitelist site” in the Browser tab.", fontSize = 12.sp, color = dim)
            wl.sorted().forEach { h ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(h, Modifier.weight(1f), fontSize = 14.sp)
                    TextButton(onClick = { Store.toggleWhitelist(h) }) { Text("✕") }
                }
            }
        }

        Section {
            Text("Privacy", fontWeight = FontWeight.SemiBold)
            OutlinedButton(onClick = { act.clearBrowsingData() }) { Text("Clear cache & cookies") }
        }

        Section {
            Text("What AdShield can and can't do", fontWeight = FontWeight.SemiBold)
            Text(
                "AdShield filters ads, trackers and pop-ups inside its own built-in browser only. It does not use a VPN, Private DNS, a proxy, root, or any network setting, so your internet works exactly as usual.\n\n" +
                    "It does NOT block ads in other apps (YouTube app, games, social apps, other browsers). Android does not allow that without privileged, system-wide network access, which this app deliberately avoids.\n\n" +
                    "Some ads are served from the same address as the content (for example video ads on YouTube) and cannot be separated by URL filtering. Sites may occasionally break; use the whitelist if so. The app only loads HTTPS pages by default.",
                fontSize = 13.sp, lineHeight = 19.sp, color = dim
            )
        }
    }
}
