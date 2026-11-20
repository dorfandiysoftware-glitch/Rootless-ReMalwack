package dev.dorfandiy.remalwack.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.dorfandiy.remalwack.UpdateWorker
import dev.dorfandiy.remalwack.data.Blocklist
import dev.dorfandiy.remalwack.data.Category
import dev.dorfandiy.remalwack.data.Mode
import dev.dorfandiy.remalwack.data.Prefs
import dev.dorfandiy.remalwack.dns.PrivateDns
import dev.dorfandiy.remalwack.vpn.Watchdog
import dev.dorfandiy.remalwack.data.Profile
import dev.dorfandiy.remalwack.data.Updater
import dev.dorfandiy.remalwack.vpn.DnsVpnService
import dev.dorfandiy.remalwack.shizuku.ShizukuHelper
import dev.dorfandiy.remalwack.shizuku.ShizukuStatus
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = Prefs(app)

    var profile by mutableStateOf(prefs.profile); private set
    var categories by mutableStateOf(prefs.categories); private set
    var customSources by mutableStateOf(prefs.customSources); private set
    var whitelist by mutableStateOf(prefs.whitelist.sorted()); private set
    var blacklist by mutableStateOf(prefs.blacklist.sorted()); private set
    var autoUpdate by mutableStateOf(prefs.autoUpdate); private set
    var startOnBoot by mutableStateOf(prefs.startOnBoot); private set
    var upstream by mutableStateOf(prefs.upstreamDns); private set
    var lastUpdated by mutableStateOf(prefs.lastUpdated); private set
    var totalEntries by mutableStateOf(prefs.totalEntries); private set
    var counts by mutableStateOf<Map<String, Int>>(emptyMap()); private set
    var updating by mutableStateOf(false); private set
    var progress by mutableStateOf(""); private set
    var dirty by mutableStateOf(false); private set
    var queryResult by mutableStateOf<String?>(null); private set

    val shizuku: StateFlow<ShizukuStatus> = ShizukuHelper.status
    var shizukuMsg by mutableStateOf<String?>(null); private set
    var mode by mutableStateOf(prefs.mode); private set
    var seamless by mutableStateOf(prefs.seamless); private set
    var uboCompat by mutableStateOf(prefs.uboCompat); private set
    var excluded by mutableStateOf(prefs.excludedApps); private set
    var theme by mutableStateOf(prefs.theme); private set
    var dnsId by mutableStateOf(prefs.privateDnsId); private set
    var dnsCustom by mutableStateOf(prefs.privateDnsCustom); private set
    var dnsActive by mutableStateOf(false); private set
    var dnsNote by mutableStateOf<String?>(null); private set
    var needVpnPermission by mutableStateOf(false); private set
    var autoInterval by mutableStateOf(prefs.sp.getInt("auto_hours", 24)); private set

    init {
        ShizukuHelper.init()
        // Auto-update on launch when the lists are stale (or were never downloaded).
        val stale = System.currentTimeMillis() - prefs.lastUpdated > autoInterval * 3600_000L
        if (autoUpdate && (prefs.lastUpdated == 0L || stale)) update()
        viewModelScope.launch(Dispatchers.IO) { Blocklist.load(getApplication()) }
        refreshCounts()
        UpdateWorker.schedule(app, autoUpdate, autoInterval)
        Watchdog.schedule(app, seamless && mode == Mode.VPN)
        refreshDns()
    }

    private fun refreshCounts() {
        counts = (prefs.activeSources().map { it.id } + Category.entries.flatMap { c -> c.sources.map { it.id } })
            .associateWith { prefs.sourceCount(it) }
    }

    fun selectProfile(p: Profile) { prefs.profile = p; profile = p; dirty = true; refreshCounts() }

    fun toggleCategory(c: Category, on: Boolean) {
        val n = if (on) categories + c.id else categories - c.id
        prefs.categories = n; categories = n; dirty = true; refreshCounts()
    }

    fun addCustomSource(url: String) {
        val u = url.trim()
        if (!u.startsWith("http")) return
        val n = customSources + u
        prefs.customSources = n; customSources = n; dirty = true
    }

    fun removeCustomSource(url: String) {
        val n = customSources - url
        prefs.customSources = n; customSources = n; dirty = true
    }

    fun addWhite(d: String) = edit(d, true, true)
    fun removeWhite(d: String) = edit(d, true, false)
    fun addBlack(d: String) = edit(d, false, true)
    fun removeBlack(d: String) = edit(d, false, false)

    private fun edit(raw: String, white: Boolean, add: Boolean) {
        val d = Blocklist.clean(raw)
        if (d.isEmpty()) return
        if (white) {
            prefs.whitelist = if (add) prefs.whitelist + d else prefs.whitelist - d
            whitelist = prefs.whitelist.sorted()
        } else {
            prefs.blacklist = if (add) prefs.blacklist + d else prefs.blacklist - d
            blacklist = prefs.blacklist.sorted()
        }
        Blocklist.reloadRules(prefs)
    }

    fun changeAutoUpdate(v: Boolean) { prefs.autoUpdate = v; autoUpdate = v; UpdateWorker.schedule(getApplication(), v, autoInterval) }
    fun changeStartOnBoot(v: Boolean) { prefs.startOnBoot = v; startOnBoot = v }
    fun changeInterval(h: Int) {
        prefs.sp.edit().putInt("auto_hours", h).apply(); autoInterval = h
        UpdateWorker.schedule(getApplication(), autoUpdate, h)
    }

    fun shizukuRequest() = ShizukuHelper.requestPermission()
    fun shizukuRefresh() { ShizukuHelper.refresh(); refreshDns() }
    fun shizukuGrant() = viewModelScope.launch(Dispatchers.IO) { shizukuMsg = ShizukuHelper.setupPermissions(getApplication()); refreshDns() }

    fun refreshDns() {
        dnsActive = runCatching { PrivateDns.isActive(getApplication(), PrivateDns.host(prefs)) }.getOrDefault(false)
    }

    fun changeDnsProvider(id: String) { prefs.privateDnsId = id; dnsId = id; dnsNote = null; refreshDns() }
    fun changeDnsCustom(h: String) { prefs.privateDnsCustom = h; dnsCustom = h; refreshDns() }

    private fun applyPrivateDns() = viewModelScope.launch(Dispatchers.IO) {
        val app = getApplication<Application>()
        val host = PrivateDns.host(prefs)
        when (val r = PrivateDns.apply(app, host)) {
            is PrivateDns.Result.Applied -> { prefs.privateDnsOurs = true; dnsNote = "Private DNS: $host" }
            is PrivateDns.Result.Manual -> {
                PrivateDns.copyHost(app, r.host)
                dnsNote = "Hostname copied. Open Settings → Network → Private DNS, choose \"Private DNS provider hostname\" and paste it."
            }
            is PrivateDns.Result.Failed -> dnsNote = r.message
        }
        refreshDns()
    }

    fun applyDnsAgain() { applyPrivateDns() }

    /** Switch protection mode. VPN needs a consent dialog, so the UI is told via [needVpnPermission]. */
    fun changeMode(m: Mode) {
        val app = getApplication<Application>()
        if (m == mode) return
        mode = m; prefs.mode = m
        when (m) {
            Mode.OFF -> { DnsVpnService.stop(app); restoreDns() }
            Mode.PRIVATE_DNS -> { DnsVpnService.stop(app); applyPrivateDns() }
            Mode.VPN -> { restoreDns(); needVpnPermission = true }
        }
        Watchdog.schedule(app, seamless && m == Mode.VPN)
    }

    fun vpnPermissionHandled() { needVpnPermission = false }

    private fun restoreDns() = viewModelScope.launch(Dispatchers.IO) {
        if (prefs.privateDnsOurs) { if (PrivateDns.restore(getApplication())) prefs.privateDnsOurs = false }
        refreshDns()
    }

    fun changeSeamless(v: Boolean) {
        prefs.seamless = v; seamless = v
        Watchdog.schedule(getApplication(), v && mode == Mode.VPN)
    }

    fun changeUbo(v: Boolean) { prefs.uboCompat = v; uboCompat = v; Blocklist.reloadRules(prefs) }
    fun changeExcluded(s: Set<String>) { prefs.excludedApps = s; excluded = s }
    fun changeTheme(t: Int) { prefs.theme = t; theme = t }
    fun clearShizukuMsg() { shizukuMsg = null }
    fun changeProfileName(p: Profile) = selectProfile(p)

    fun changeUpstream(v: String) { prefs.upstreamDns = v; upstream = v }

    fun update() {
        if (updating) return
        updating = true; progress = "Starting…"
        viewModelScope.launch(Dispatchers.IO) {
            val r = Updater.update(getApplication()) { progress = it }
            progress = r.fold({ "Done: ${"%,d".format(it)} domains" }, { "Update failed: ${it.message}" })
            lastUpdated = prefs.lastUpdated; totalEntries = prefs.totalEntries
            if (r.isSuccess) { dirty = false; DnsVpnService.reload(getApplication()) }
            refreshCounts()
            updating = false
        }
    }

    fun reset() {
        viewModelScope.launch(Dispatchers.IO) {
            Blocklist.save(getApplication(), LongArray(0)); Blocklist.load(getApplication())
            prefs.totalEntries = 0; prefs.lastUpdated = 0; totalEntries = 0; lastUpdated = 0
            counts = counts.mapValues { 0 }
            Category.entries.forEach { c -> c.sources.forEach { prefs.setSourceCount(it.id, 0) } }
            progress = "Hosts reset"; dirty = false
            DnsVpnService.reload(getApplication())
        }
    }

    fun query(domain: String) {
        val d = Blocklist.clean(domain)
        if (d.isEmpty()) { queryResult = null; return }
        queryResult = when (Blocklist.verdict(d)) {
            Blocklist.Verdict.WHITELISTED -> "$d is whitelisted (never blocked)"
            Blocklist.Verdict.BLACKLISTED -> "$d is blocked by your blacklist"
            Blocklist.Verdict.BLOCKED -> "$d is blocked by the hosts lists"
            Blocklist.Verdict.ALLOWED -> "$d is not blocked"
        }
    }
}
