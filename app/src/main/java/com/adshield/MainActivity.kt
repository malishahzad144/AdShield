package com.adshield

import android.Manifest
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

private val Green = Color(0xFF4ADE80)
private val Scheme = darkColorScheme(
    primary = Green, onPrimary = Color(0xFF05210F), background = Color(0xFF0B0F14),
    surface = Color(0xFF131A22), onSurface = Color(0xFFE6EDF3), onBackground = Color(0xFFE6EDF3),
    surfaceVariant = Color(0xFF1B2530), onSurfaceVariant = Color(0xFF9AA7B4), error = Color(0xFFF87171)
)

class MainActivity : ComponentActivity() {
    private val vpnPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) startShield()
        else ShieldState.error.value = "VPN permission denied. AdShield needs it to filter DNS locally on this device."
    }
    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ShieldState.init(this)
        lifecycleScope.launch(Dispatchers.IO) { Blocklist.load(this@MainActivity) }
        if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent { MaterialTheme(colorScheme = Scheme) { Screen() } }
    }

    private fun startShield() {
        ContextCompat.startForegroundService(
            this, Intent(this, AdShieldVpnService::class.java).setAction(AdShieldVpnService.ACTION_START)
        )
    }

    private fun toggle() {
        if (ShieldState.running.value) {
            startService(Intent(this, AdShieldVpnService::class.java).setAction(AdShieldVpnService.ACTION_STOP))
        } else {
            ShieldState.error.value = null
            val i = VpnService.prepare(this)
            if (i != null) vpnPermission.launch(i) else startShield()
        }
    }

    private fun refresh() {
        if (ShieldState.updating.value) return
        ShieldState.updating.value = true
        lifecycleScope.launch(Dispatchers.IO) {
            ShieldState.error.value = Blocklist.update(this@MainActivity)
            ShieldState.updating.value = false
        }
    }

    private fun pick(src: String) {
        ShieldState.source.value = src
        ShieldState.persist()
        lifecycleScope.launch(Dispatchers.IO) { Blocklist.load(this@MainActivity) }
    }

    @Composable
    private fun Screen() {
        val running by ShieldState.running.collectAsState()
        val blocked by ShieldState.blocked.collectAsState()
        val total by ShieldState.total.collectAsState()
        val size by ShieldState.listSize.collectAsState()
        val err by ShieldState.error.collectAsState()
        val updating by ShieldState.updating.collectAsState()
        val src by ShieldState.source.collectAsState()
        val last by ShieldState.lastUpdate.collectAsState()
        val dim = MaterialTheme.colorScheme.onSurfaceVariant

        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("AdShield", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.Start))
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier.size(170.dp).clip(CircleShape)
                        .background(if (running) Green else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { toggle() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (running) "STOP" else "START", fontSize = 26.sp, fontWeight = FontWeight.Bold,
                        color = if (running) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    if (running) "Protected" else "Not protected", fontSize = 20.sp, fontWeight = FontWeight.Medium,
                    color = if (running) Green else dim
                )
                err?.let { Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Stat("Blocked", "%,d".format(blocked), Modifier.weight(1f))
                    Stat("DNS requests", "%,d".format(total), Modifier.weight(1f))
                    Stat("Blocked %", if (total > 0) "${blocked * 100 / total}%" else "0%", Modifier.weight(1f))
                }
                TextButton(onClick = { ShieldState.resetStats() }) { Text("Reset statistics") }

                CardBox {
                    Text("Blocklists", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("adguard" to "AdGuard", "oisd" to "OISD", "both" to "Both").forEach { (id, label) ->
                            FilterChip(selected = src == id, onClick = { pick(id) }, label = { Text(label) })
                        }
                    }
                    val stamp = if (last > 0)
                        " · updated " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(last))
                    else " · built-in list only, tap Update"
                    Text("%,d domains".format(size) + stamp, color = dim, fontSize = 13.sp)
                    Button(onClick = { refresh() }, enabled = !updating) {
                        if (updating) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        else Text("Update lists")
                    }
                    Text(
                        "Unmatched queries go to AdGuard DNS (94.140.14.14), which also filters ads server-side.",
                        color = dim, fontSize = 12.sp
                    )
                }

                CardBox {
                    Text("How it works & limitations", fontWeight = FontWeight.SemiBold)
                    Text(
                        "AdShield runs a local VPN that only captures DNS lookups. Domains on the blocklist get an empty answer, so the ad never loads. No traffic is inspected, logged or sent to any server of ours.\n\n" +
                            "• YouTube ads are NOT blocked: they are served from the same domains as the videos, so DNS filtering can't separate them.\n" +
                            "• In-app ads served from first-party domains, and ads embedded in page content, can pass through.\n" +
                            "• Apps/browsers using DNS-over-HTTPS or Private DNS bypass the filter: turn off \"Private DNS\" (Settings > Network) and in-browser secure DNS.\n" +
                            "• Another active VPN will replace AdShield (Android allows one VPN at a time).\n" +
                            "• Some sites may break if a needed domain is listed.",
                        color = dim, fontSize = 13.sp, lineHeight = 19.sp
                    )
                }
            }
        }
    }

    @Composable
    private fun Stat(label: String, value: String, modifier: Modifier) {
        Column(modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(14.dp)) {
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    @Composable
    private fun CardBox(content: @Composable ColumnScope.() -> Unit) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp), content = content
        )
    }
}
