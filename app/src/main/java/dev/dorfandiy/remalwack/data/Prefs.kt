package dev.dorfandiy.remalwack.data

import android.content.Context
import android.content.SharedPreferences

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
