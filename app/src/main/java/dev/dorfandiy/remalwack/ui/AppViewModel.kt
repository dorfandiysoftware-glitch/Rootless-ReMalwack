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
import dev.dorfandiy.remalwack.data.Prefs
import dev.dorfandiy.remalwack.data.Profile
import dev.dorfandiy.remalwack.data.Updater
import dev.dorfandiy.remalwack.vpn.DnsVpnService
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

    init {
        viewModelScope.launch(Dispatchers.IO) { Blocklist.load(getApplication()) }
        refreshCounts()
        UpdateWorker.schedule(app, autoUpdate)
    }

    private fun refreshCounts() {
        counts = (prefs.activeSources().map { it.id } + Category.entries.map { it.source.id })
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

    fun changeAutoUpdate(v: Boolean) { prefs.autoUpdate = v; autoUpdate = v; UpdateWorker.schedule(getApplication(), v) }
    fun changeStartOnBoot(v: Boolean) { prefs.startOnBoot = v; startOnBoot = v }
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
