package dev.dorfandiy.remalwack.ui

import android.app.Activity
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowRight
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Poll
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.TripOrigin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.foundation.lazy.LazyColumn as AppsColumn
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import dev.dorfandiy.remalwack.data.Mode
import dev.dorfandiy.remalwack.dns.PrivateDns
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.dorfandiy.remalwack.data.Category
import dev.dorfandiy.remalwack.data.Profile
import dev.dorfandiy.remalwack.shizuku.ShizukuStatus
import dev.dorfandiy.remalwack.vpn.DnsVpnService
import dev.dorfandiy.remalwack.vpn.VpnState
import java.text.DateFormat
import java.util.Date

private val catIcons = mapOf(
    Category.PORN to Icons.Rounded.Block, Category.GAMBLING to Icons.Rounded.Casino,
    Category.FAKENEWS to Icons.Rounded.Newspaper, Category.SOCIAL to Icons.Rounded.Group,
    Category.TRACKERS to Icons.Rounded.Pets, Category.SAFEBROWSING to Icons.Rounded.Security,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun App(vm: AppViewModel = viewModel()) {
    val ctx = LocalContext.current
    val running by VpnState.running.collectAsState()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) DnsVpnService.start(ctx)
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun askNotifications() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    fun startVpn() {
        val i = VpnService.prepare(ctx)
        if (i != null) launcher.launch(i) else DnsVpnService.start(ctx)
    }
    fun toggle() {
        if (vm.mode != Mode.VPN) return
        if (running) DnsVpnService.stop(ctx) else { askNotifications(); startVpn() }
    }
    LaunchedEffect(vm.needVpnPermission) {
        if (vm.needVpnPermission) { vm.vpnPermissionHandled(); askNotifications(); startVpn() }
    }
    val owner = LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) vm.shizukuRefresh() }
        owner.lifecycle.addObserver(o)
        onDispose { owner.lifecycle.removeObserver(o) }
    }
    var profileDialog by remember { mutableStateOf(false) }
    if (profileDialog) ProfileDialog(vm) { profileDialog = false }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        floatingActionButton = {
            if (vm.mode == Mode.VPN) FloatingActionButton(onClick = { toggle() }, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(if (running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = null)
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = 800.dp).fillMaxSize(),
                contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item { Header(vm) { profileDialog = true } }
                if (vm.lastUpdated == 0L && !vm.updating) item {
                    InfoCard(Icons.Rounded.Info, "Action Required: Protection not initialized",
                        "Hosts are not downloaded yet. Tap \"Update hosts file\" below to get started.")
                }
                if (vm.dirty) item { InfoCard(Icons.Rounded.Warning, "Lists changed", "Press \"Update hosts file\" to apply the new sources.") }
                item { Dashboard(vm) }
                item { Title("Protection mode") }
                item { ModeBox(vm, running) { toggle() } }
                item { Title("Browser & uBlock Origin") }
                item { BrowserBox(vm) }
                item { Title("Host Operation") }
                item { Group(null) {
                    Row(Icons.Rounded.CloudDownload, "Update hosts file", sub = if (vm.updating) vm.progress else vm.progress.ifEmpty { null }, onClick = { vm.update() })
                    if (vm.updating) LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp))
                    Row(Icons.Rounded.History, "Reset hosts file", onClick = { vm.reset() })
                    SwitchRow(Icons.Rounded.Update, "Daily update hosts file", vm.autoUpdate) { vm.changeAutoUpdate(it) }
                    if (vm.autoUpdate) IntervalRow(vm)
                    SwitchRow(Icons.Rounded.PowerSettingsNew, "Start protection on boot", vm.startOnBoot) { vm.changeStartOnBoot(it) }
                    DnsLogging()
                } }
                item { Title("Preset Blocking Sources") }
                item { Group(null) {
                    Category.entries.forEach { c ->
                        val n = c.sources.sumOf { vm.counts[it.id] ?: 0 }
                        SwitchRow(catIcons[c] ?: Icons.Rounded.Block, c.title, c.id in vm.categories,
                            badge = if (n > 0) "%,d".format(n) else null) { vm.toggleCategory(c, it) }
                    }
                } }
                item { Title("Query") }
                item { QueryBox(vm) }
                item { Title("Whitelist") }
                item { DomainBox(Icons.Rounded.VerifiedUser, "Domain", vm.whitelist, vm::addWhite, vm::removeWhite) }
                item { Title("Blacklist") }
                item { DomainBox(Icons.Rounded.Block, "Domain", vm.blacklist, vm::addBlack, vm::removeBlack) }
                item { Title("Sources") }
                item { DomainBox(Icons.Rounded.AddCircle, "URL", vm.customSources.sorted(), vm::addCustomSource, vm::removeCustomSource) }
                item { Title("Shizuku") }
                item { ShizukuBox(vm) }
                item { Title("Appearance") }
                item { ThemeBox(vm) }
                item { Title("Misc") }
                item { UpstreamBox(vm) }
                item { AboutBox() }
            }
        }
    }
}

@Composable
private fun Header(vm: AppViewModel, onProfile: () -> Unit) {
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.outline) {
        Column {
            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("RE-MALWACK", fontSize = 32.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("Rootless edition", fontSize = 13.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Row(Modifier.fillMaxWidth().clickable(onClick = onProfile).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.TripOrigin, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.surfaceContainer)
                Spacer(Modifier.size(6.dp))
                Text("Profile: ${vm.profile.title}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.surfaceContainer)
            }
        }
    }
}

@Composable
private fun ProfileDialog(vm: AppViewModel, onClose: () -> Unit) {
    var sel by remember { mutableStateOf(vm.profile) }
    AlertDialog(
        onDismissRequest = onClose,
        icon = { Icon(Icons.Rounded.TripOrigin, null) },
        title = { Text("Profile") },
        text = {
            Column {
                Profile.entries.forEach { p ->
                    Row(Modifier.fillMaxWidth().clickable { sel = p }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(sel == p, { sel = p })
                        Column { Text(p.title, style = MaterialTheme.typography.titleMedium); Text(p.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { vm.selectProfile(sel); onClose() }) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}

@Composable
private fun InfoCard(icon: ImageVector, title: String, text: String) {
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.size(16.dp))
            Column {
                Text(title, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
    }
}

@Composable
private fun Dashboard(vm: AppViewModel) {
    val blocked by VpnState.blocked.collectAsState()
    val last = if (vm.lastUpdated == 0L) "-" else DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(vm.lastUpdated))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile("Version", "0.3.0", Modifier.weight(1f))
            Tile("Real-time block", "%,d".format(blocked), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile("Blocked Entries", "%,d".format(vm.totalEntries), Modifier.weight(1f))
            Tile("Last Updated", last, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Tile(title: String, value: String, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun Title(t: String) =
    Text(t, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp, top = 6.dp))

@Composable
private fun Group(unused: String?, content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(vertical = 4.dp)) { content() }
    }
}

@Composable
private fun Row(icon: ImageVector, title: String, sub: String? = null, value: String? = null, onClick: () -> Unit) =
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (!sub.isNullOrEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (value != null) Text(value, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
    }

@Composable
private fun Row(value: String, icon: ImageVector, title: String, onClick: () -> Unit) = Row(icon, title, null, value, onClick)

@Composable
private fun SwitchRow(icon: ImageVector, title: String, checked: Boolean, badge: String? = null, onChange: (Boolean) -> Unit) =
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (badge != null) Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                Text(badge, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
        }
        Switch(checked, onChange)
    }

@Composable
private fun IntervalRow(vm: AppViewModel) =
    Row(Modifier.fillMaxWidth().padding(start = 56.dp, end = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(6, 12, 24, 72).forEach { h ->
            FilterChip(vm.autoInterval == h, { vm.changeInterval(h) }, label = { Text(if (h < 24) "${h}h" else "${h / 24}d") })
        }
    }

@Composable
private fun DnsLogging() {
    var open by remember { mutableStateOf(false) }
    val recent by VpnState.recent.collectAsState()
    Row(Icons.Rounded.Dns, "DNS logging", sub = "Recent queries (${recent.size})", onClick = { open = !open })
    AnimatedVisibility(open) {
        Column(Modifier.padding(start = 56.dp, end = 16.dp, bottom = 8.dp)) {
            if (recent.isEmpty()) Text("Nothing yet. Start protection and use your device.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            recent.take(30).forEach {
                Text((if (it.blocked) "✕ " else "✓ ") + it.domain, fontSize = 13.sp,
                    color = if (it.blocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun QueryBox(vm: AppViewModel) {
    var q by remember { mutableStateOf("") }
    Group(null) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(q, { q = it }, Modifier.weight(1f), singleLine = true, label = { Text("Domain or text to search") })
                FilledIconButton(onClick = { vm.query(q) }) { Icon(Icons.Rounded.Search, null) }
            }
            vm.queryResult?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun DomainBox(icon: ImageVector, label: String, items: List<String>, onAdd: (String) -> Unit, onRemove: (String) -> Unit) {
    var t by remember { mutableStateOf("") }
    Group(null) {
        Row(Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(t, { t = it }, Modifier.weight(1f), singleLine = true, label = { Text(label) })
            FilledIconButton(onClick = { onAdd(t); t = "" }) { Icon(icon, null) }
        }
        if (items.isEmpty()) Text("Empty", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        items.forEach { d ->
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(d, Modifier.weight(1f), fontSize = 14.sp, maxLines = 1)
                IconButton({ onRemove(d) }) { Icon(Icons.Rounded.Delete, null) }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun ShizukuBox(vm: AppViewModel) {
    val st by vm.shizuku.collectAsState()
    Group(null) {
        when (st) {
            ShizukuStatus.NOT_RUNNING -> Row(Icons.Rounded.Terminal, "Shizuku is not running", "Install Shizuku and start it (wireless debugging), then reopen the app.", onClick = { vm.shizukuRefresh() })
            ShizukuStatus.NO_PERMISSION -> Row(Icons.Rounded.Terminal, "Allow Shizuku access", "Tap to grant this app permission in Shizuku.", onClick = { vm.shizukuRequest() })
            ShizukuStatus.READY -> {
                Row(Icons.Rounded.Terminal, "Grant permissions", "Secure settings + VPN pre-approval (no consent dialog).", onClick = { vm.shizukuGrant() })
            }
        }
        vm.shizukuMsg?.let { Row(Icons.Rounded.Info, it, onClick = { vm.clearShizukuMsg() }) }
    }
}

@Composable
private fun UpstreamBox(vm: AppViewModel) {
    Group(null) {
        Column(Modifier.padding(16.dp)) {
            Text("Upstream DNS", style = MaterialTheme.typography.bodyLarge)
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("1.1.1.1" to "Cloudflare", "9.9.9.9" to "Quad9", "8.8.8.8" to "Google").forEach { (ip, n) ->
                    FilterChip(vm.upstream == ip, { vm.changeUpstream(ip) }, label = { Text(n) })
                }
            }
        }
    }
}

@Composable
private fun AboutBox() {
    Group(null) {
        Column(Modifier.padding(16.dp)) {
            Text("Rootless Re-Malwack", style = MaterialTheme.typography.titleMedium)
            Text("A no-root port of Re-Malwack by ZG089 (GPL-3.0). Blocks ads, trackers and malware with a local DNS VPN.\n" +
                "Limits: only one VPN at a time; apps/browsers using their own DNS-over-HTTPS bypass it; Private DNS set to a custom host bypasses it.",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ModeBox(vm: AppViewModel, running: Boolean, onToggle: () -> Unit) {
    val ctx = LocalContext.current
    Group(null) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Mode.VPN to "VPN", Mode.PRIVATE_DNS to "No VPN", Mode.OFF to "Off").forEach { (m, label) ->
                    ToggleButton(vm.mode == m, { vm.changeMode(m) }, Modifier.weight(1f), shapes = ToggleButtonDefaults.shapes()) { Text(label) }
                }
            }
            when (vm.mode) {
                Mode.VPN -> {
                    Text(if (running) "Active: the local VPN filters DNS with your hosts lists." else "Stopped. Tap the play button to start.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SwitchRow(Icons.Rounded.Shield, "Uninterrupted mode", vm.seamless) { vm.changeSeamless(it) }
                    Text("Foreground service, automatic restart after the system kills it, DNS failover so browsing never stalls, and a 15-minute watchdog. " +
                        "For the strongest guarantee also enable Android's \"Always-on VPN\" for this app.",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { runCatching { ctx.startActivity(Intent(Settings.ACTION_VPN_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }) {
                        Text("Open VPN settings")
                    }
                }
                Mode.PRIVATE_DNS -> PrivateDnsSection(vm)
                Mode.OFF -> Text("Protection is off. Private DNS is restored to automatic if this app had changed it.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PrivateDnsSection(vm: AppViewModel) {
    val ctx = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("No VPN: the system's Private DNS (DNS over TLS) points to a filtering resolver. " +
            "It does not use the on-device hosts lists, the provider filters instead, and it works in every app.",
            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        PrivateDns.presets.forEach { p ->
            Row(Modifier.fillMaxWidth().clickable { vm.changeDnsProvider(p.id) }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(vm.dnsId == p.id, { vm.changeDnsProvider(p.id) })
                Column { Text(p.title); Text("${p.host} · ${p.note}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        Row(Modifier.fillMaxWidth().clickable { vm.changeDnsProvider(PrivateDns.CUSTOM) }, verticalAlignment = Alignment.CenterVertically) {
            RadioButton(vm.dnsId == PrivateDns.CUSTOM, { vm.changeDnsProvider(PrivateDns.CUSTOM) })
            Text("Custom hostname")
        }
        if (vm.dnsId == PrivateDns.CUSTOM) OutlinedTextField(vm.dnsCustom, { vm.changeDnsCustom(it) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("dns.example.com") })
        Text(if (vm.dnsActive) "Status: active" else "Status: not active", fontWeight = FontWeight.Medium,
            color = if (vm.dnsActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.applyDnsAgain() }) { Text("Apply") }
            TextButton(onClick = { PrivateDns.openNetworkSettings(ctx) }) { Text("Open settings") }
        }
        if (!PrivateDns.canWrite(ctx)) Text("Without Shizuku or the ADB permission the hostname is copied and you paste it into Settings once. " +
            "ADB: ${PrivateDns.adbCommand}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        vm.dnsNote?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary) }
    }
}

@Composable
private fun BrowserBox(vm: AppViewModel) {
    var picker by remember { mutableStateOf(false) }
    if (picker) AppPickerDialog(vm.excluded, { vm.changeExcluded(it) }) { picker = false }
    Group(null) {
        SwitchRow(Icons.Rounded.Shield, "uBlock Origin compatibility", vm.uboCompat) { vm.changeUbo(it) }
        Text("Keeps the domains uBlock Origin needs to download its filter lists (GitHub, jsDelivr, EasyList…) reachable, so both blockers work together. " +
            "uBlock Origin filters inside the page; Re-Malwack filters DNS for everything else.",
            Modifier.padding(start = 56.dp, end = 16.dp, bottom = 8.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Icons.Rounded.Apps, "Excluded apps", sub = if (vm.excluded.isEmpty()) "None. Pick your browser to skip the VPN there." else "${vm.excluded.size} app(s) bypass the VPN", onClick = { picker = true })
        Text("IronFox: it uses its own DNS over HTTPS (Max Protection), which bypasses any DNS filter. In IronFox open Settings → Privacy and security → " +
            "DNS over HTTPS and choose \"Default Protection\" to let Re-Malwack see its queries.",
            Modifier.padding(start = 56.dp, end = 16.dp, bottom = 12.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AppPickerDialog(selected: Set<String>, onSave: (Set<String>) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val apps = remember {
        val pm = ctx.packageManager
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(i, 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .filter { it.first != ctx.packageName }
            .distinctBy { it.first }.sortedBy { it.second.lowercase() }
    }
    var sel by remember { mutableStateOf(selected) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Excluded apps") },
        text = {
            AppsColumn(Modifier.heightIn(max = 420.dp)) {
                items(apps) { (pkg, name) ->
                    Row(Modifier.fillMaxWidth().clickable { sel = if (pkg in sel) sel - pkg else sel + pkg }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(pkg in sel, { sel = if (it) sel + pkg else sel - pkg })
                        Column { Text(name, maxLines = 1); Text(pkg, fontSize = 11.sp, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(sel); onClose() }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}

@Composable
private fun ThemeBox(vm: AppViewModel) {
    Group(null) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Palette, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            listOf(0 to "Dark", 1 to "System", 2 to "Light").forEach { (v, n) ->
                FilterChip(vm.theme == v, { vm.changeTheme(v) }, label = { Text(n) })
            }
        }
    }
}
