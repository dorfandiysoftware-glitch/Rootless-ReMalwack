package dev.dorfandiy.remalwack.data

import android.content.Context
import android.content.SharedPreferences

enum class Mode { VPN, PRIVATE_DNS, OFF }

class Prefs(ctx: Context) {
    val sp: SharedPreferences = ctx.applicationContext.getSharedPreferences("remalwack", Context.MODE_PRIVATE)

    var profile: Profile
        get() = Profile.from(sp.getString("profile", null))
        set(v) = sp.edit().putString("profile", v.id).apply()

    var categories: Set<String>
        get() = sp.getStringSet("categories", emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet("categories", v).apply()

    var customSources: Set<String>
        get() = sp.getStringSet("custom_sources", emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet("custom_sources", v).apply()

    var whitelist: Set<String>
        get() = sp.getStringSet("whitelist", emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet("whitelist", v).apply()

    var blacklist: Set<String>
        get() = sp.getStringSet("blacklist", emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet("blacklist", v).apply()

    var autoUpdate: Boolean
        get() = sp.getBoolean("auto_update", true)
        set(v) = sp.edit().putBoolean("auto_update", v).apply()

    var startOnBoot: Boolean
        get() = sp.getBoolean("start_on_boot", false)
        set(v) = sp.edit().putBoolean("start_on_boot", v).apply()

    var upstreamDns: String
        get() = sp.getString("upstream", "1.1.1.1") ?: "1.1.1.1"
        set(v) = sp.edit().putString("upstream", v).apply()

    /** How protection is delivered: local DNS VPN, system Private DNS (no VPN), or nothing. */
    var mode: Mode
        get() = runCatching { Mode.valueOf(sp.getString("mode", null) ?: "") }.getOrDefault(Mode.VPN)
        set(v) = sp.edit().putString("mode", v.name).apply()

    /** True while the person wants the VPN running; lets the app bring it back if the system kills it. */
    var vpnWanted: Boolean
        get() = sp.getBoolean("vpn_wanted", false)
        set(v) = sp.edit().putBoolean("vpn_wanted", v).apply()

    /** Uninterrupted mode: foreground service, automatic restart, DNS failover. */
    var seamless: Boolean
        get() = sp.getBoolean("seamless", true)
        set(v) = sp.edit().putBoolean("seamless", v).apply()

    var privateDnsId: String
        get() = sp.getString("pdns_id", "adguard") ?: "adguard"
        set(v) = sp.edit().putString("pdns_id", v).apply()

    var privateDnsCustom: String
        get() = sp.getString("pdns_custom", "") ?: ""
        set(v) = sp.edit().putString("pdns_custom", v).apply()

    /** True when this app (not the person) switched Private DNS on, so only then it switches it back. */
    var privateDnsOurs: Boolean
        get() = sp.getBoolean("pdns_ours", false)
        set(v) = sp.edit().putBoolean("pdns_ours", v).apply()

    /** Keep the update servers of browser content blockers (uBlock Origin and friends) reachable. */
    var uboCompat: Boolean
        get() = sp.getBoolean("ubo_compat", true)
        set(v) = sp.edit().putBoolean("ubo_compat", v).apply()

    /** Apps that bypass the VPN completely (for example a browser that already runs uBlock Origin). */
    var excludedApps: Set<String>
        get() = sp.getStringSet("excluded_apps", emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet("excluded_apps", v).apply()

    /** 0 = dark, 1 = follow the system, 2 = light. */
    var theme: Int
        get() = sp.getInt("theme", 0)
        set(v) = sp.edit().putInt("theme", v).apply()

    var lastUpdated: Long
        get() = sp.getLong("last_updated", 0L)
        set(v) = sp.edit().putLong("last_updated", v).apply()

    var totalEntries: Int
        get() = sp.getInt("total_entries", 0)
        set(v) = sp.edit().putInt("total_entries", v).apply()

    fun sourceCount(id: String): Int = sp.getInt("count_$id", 0)
    fun setSourceCount(id: String, n: Int) = sp.edit().putInt("count_$id", n).apply()

    /** Every source that is active under the current profile + categories + custom URLs. */
    fun activeSources(): List<Source> {
        val list = ArrayList<Source>()
        list += Sources.forProfile(profile)
        list += Category.entries.filter { it.id in categories }.flatMap { it.sources }
        list += Sources.custom(customSources)
        return list
    }
}
