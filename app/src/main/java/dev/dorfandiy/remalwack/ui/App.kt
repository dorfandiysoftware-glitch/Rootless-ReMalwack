package dev.dorfandiy.remalwack.ui

import android.app.Activity
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Rule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.dorfandiy.remalwack.data.Category
import dev.dorfandiy.remalwack.data.Profile
import dev.dorfandiy.remalwack.vpn.DnsVpnService
import dev.dorfandiy.remalwack.vpn.VpnState
import java.text.DateFormat
import java.util.Date

private val TABS = listOf("Home", "Lists", "Rules", "Settings")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(vm: AppViewModel = viewModel()) {
    var tab by remember { mutableIntStateOf(0) }
    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("Re-Malwack", fontWeight = FontWeight.SemiBold) }) },
        bottomBar = {
            NavigationBar {
                val icons = listOf(Icons.Rounded.Home, Icons.Rounded.Checklist, Icons.Rounded.Rule, Icons.Rounded.Settings)
                TABS.forEachIndexed { i, t ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(icons[i], null) }, label = { Text(t) })
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> HomeScreen(vm)
                1 -> ListsScreen(vm)
                2 -> RulesScreen(vm)
                else -> SettingsScreen(vm)
            }
        }
    }
}

private val PAD = PaddingValues(16.dp)

@Composable
private fun HomeScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val running by VpnState.running.collectAsState()
    val blocked by VpnState.blocked.collectAsState()
    val queries by VpnState.queries.collectAsState()
    val recent by VpnState.recent.collectAsState()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) DnsVpnService.start(ctx)
    }
    fun toggle() {
        if (running) DnsVpnService.stop(ctx) else {
            val i = VpnService.prepare(ctx)
            if (i != null) launcher.launch(i) else DnsVpnService.start(ctx)
        }
    }
    LazyColumn(contentPadding = PAD, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (running) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
                ),
            ) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = CircleShape, onClick = { toggle() },
                        color = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(112.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.PowerSettingsNew, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.surface)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(if (running) "Protection active" else "Protection paused", style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (running) "DNS filtering is on" else "Tap the button to start",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Stat("Blocked entries", "%,d".format(vm.totalEntries), Modifier.weight(1f))
                Stat("Blocked now", "%,d".format(blocked), Modifier.weight(1f))
                Stat("Queries", "%,d".format(queries), Modifier.weight(1f))
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Hosts lists", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (vm.lastUpdated == 0L) "Not downloaded yet"
                                else "Updated " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(vm.lastUpdated)),
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        FilledTonalButton(onClick = { vm.update() }, enabled = !vm.updating) {
                            Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Update")
                        }
                    }
                    if (vm.updating) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (vm.progress.isNotEmpty()) Text(vm.progress, style = MaterialTheme.typography.bodySmall)
                    if (vm.dirty) Text("Lists changed — press Update to apply.", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item { Text("Recent queries", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp)) }
        if (recent.isEmpty()) item {
            Text("Nothing yet. Start protection and use your device.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(recent.take(30)) { e ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (e.blocked) Icons.Rounded.Shield else Icons.Rounded.Checklist, null, Modifier.size(18.dp),
                    tint = if (e.blocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                )
                Spacer(Modifier.width(10.dp))
                Text(e.domain, Modifier.weight(1f), maxLines = 1, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ListsScreen(vm: AppViewModel) {
    var url by remember { mutableStateOf("") }
    LazyColumn(contentPadding = PAD, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Profile", style = MaterialTheme.typography.titleMedium) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Profile.entries.forEach { p ->
                    FilterChip(selected = vm.profile == p, onClick = { vm.selectProfile(p) }, label = { Text(p.title) })
                }
            }
        }
        item { Text(vm.profile.description, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { Text("Categories", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
        items(Category.entries.toList()) { c ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(c.title, style = MaterialTheme.typography.titleSmall)
                        val n = vm.counts[c.source.id] ?: 0
                        Text(c.description + if (n > 0) " · %,d entries".format(n) else "", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = c.id in vm.categories, onCheckedChange = { vm.toggleCategory(c, it) })
                }
            }
        }
        item { Text("Custom sources", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(url, { url = it }, Modifier.weight(1f), singleLine = true, label = { Text("https://…/hosts") })
                IconButton(onClick = { vm.addCustomSource(url); url = "" }) { Icon(Icons.Rounded.Add, "Add") }
            }
        }
        items(vm.customSources.toList()) { s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s, Modifier.weight(1f), maxLines = 1, style = MaterialTheme.typography.bodyMedium)
                IconButton(onClick = { vm.removeCustomSource(s) }) { Icon(Icons.Rounded.Close, "Remove") }
            }
        }
    }
}

@Composable
private fun RulesScreen(vm: AppViewModel) {
    var sub by remember { mutableIntStateOf(0) }
    var input by remember { mutableStateOf("") }
    var q by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        Card(Modifier.fillMaxWidth().padding(16.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Check a domain", style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(q, { q = it; vm.query(it) }, Modifier.weight(1f), singleLine = true, label = { Text("example.com") })
                }
                vm.queryResult?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
        }
        TabRow(selectedTabIndex = sub) {
            Tab(selected = sub == 0, onClick = { sub = 0 }, text = { Text("Whitelist") })
            Tab(selected = sub == 1, onClick = { sub = 1 }, text = { Text("Blacklist") })
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(input, { input = it }, Modifier.weight(1f), singleLine = true,
                label = { Text(if (sub == 0) "Domain or *.domain to allow" else "Domain or *.domain to block") })
            IconButton(onClick = {
                if (sub == 0) vm.addWhite(input) else vm.addBlack(input)
                input = ""
            }) { Icon(Icons.Rounded.Add, "Add") }
        }
        val list = if (sub == 0) vm.whitelist else vm.blacklist
        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp)) {
            if (list.isEmpty()) item { Text("Empty", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(list) { d ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(d, Modifier.weight(1f))
                    IconButton(onClick = { if (sub == 0) vm.removeWhite(d) else vm.removeBlack(d) }) { Icon(Icons.Rounded.Close, "Remove") }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(vm: AppViewModel) {
    val dns = listOf("1.1.1.1" to "Cloudflare", "8.8.8.8" to "Google", "9.9.9.9" to "Quad9", "94.140.14.14" to "AdGuard")
    LazyColumn(contentPadding = PAD, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SwitchRow("Auto-update hosts", "Once a day on unmetered networks", vm.autoUpdate) { vm.setAutoUpdate(it) }
                    SwitchRow("Start on boot", "Needs VPN permission granted once", vm.startOnBoot) { vm.setStartOnBoot(it) }
                }
            }
        }
        item { Text("Upstream DNS", style = MaterialTheme.typography.titleMedium) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                dns.forEach { (ip, name) -> FilterChip(selected = vm.upstream == ip, onClick = { vm.setUpstream(ip) }, label = { Text(name) }) }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Text(
                    "If Private DNS is set to a custom hostname in Android settings, it bypasses this filter. Use \"Automatic\" or \"Off\".",
                    Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        item {
            Text(
                "Rootless Re-Malwack · GPL-3.0\nInspired by Re-Malwack by ZG089. Blocks via a local VPN that only handles DNS; no traffic leaves your device except list downloads and DNS lookups.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SwitchRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
