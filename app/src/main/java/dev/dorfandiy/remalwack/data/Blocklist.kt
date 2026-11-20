package dev.dorfandiy.remalwack.data

import android.content.Context
import java.io.File
import java.nio.ByteBuffer

/**
 * Compact in-memory blocklist: sorted 64-bit FNV-1a hashes of domains (8 bytes each),
 * so even ~1M domains need only ~8 MB of RAM.
 */
object Blocklist {
    @Volatile private var hashes = LongArray(0)
    @Volatile private var white = Rules(emptySet(), emptySet())
    @Volatile private var black = Rules(emptySet(), emptySet())

    class Rules(val exact: Set<String>, val wild: Set<String>) {
        fun matches(domain: String): Boolean {
            if (domain in exact) return true
            if (wild.isEmpty()) return false
            var d = domain
            while (true) {
                if (d in wild) return true
                val i = d.indexOf('.')
                if (i < 0) return false
                d = d.substring(i + 1)
            }
        }
    }

    enum class Verdict { WHITELISTED, BLACKLISTED, BLOCKED, ALLOWED }

    /**
     * Hosts a content blocker such as uBlock Origin needs to download its filter lists and
     * updates. They are never blocked by the hosts lists while the compatibility option is on.
     */
    private val uboHosts = setOf(
        "ublockorigin.github.io", "ublockorigin.pages.dev", "raw.githubusercontent.com",
        "github.com", "objects.githubusercontent.com", "gitlab.com", "cdn.jsdelivr.net",
        "easylist.to", "easylist-downloads.adblockplus.org", "secure.fanboy.co.nz",
        "filters.adtidy.org", "malware-filter.gitlab.io", "pgl.yoyo.org", "cdn.statically.io",
        "easylist-msie.adblockplus.org", "adblockplus.org", "abp.oisd.nl",
        "addons.mozilla.org", "addons.cdn.mozilla.net",
    )
    private val uboRules = Rules(uboHosts, uboHosts)
    @Volatile var uboCompat = true

    fun file(ctx: Context) = File(ctx.filesDir, "blocklist.bin")

    fun hash(domain: String): Long {
        var h = -0x340d631b7bdddcdbL // FNV offset basis
        for (c in domain) {
            h = h xor c.code.toLong()
            h *= 0x100000001b3L
        }
        return h
    }

    fun clean(raw: String): String = raw.trim().lowercase().trimEnd('.')

    private fun rules(set: Set<String>): Rules {
        val exact = HashSet<String>()
        val wild = HashSet<String>()
        for (e in set) {
            val c = clean(e)
            if (c.startsWith("*.")) wild += c.substring(2) else if (c.isNotEmpty()) exact += c
        }
        return Rules(exact, wild)
    }

    fun load(ctx: Context) {
        val f = file(ctx)
        hashes = if (f.exists() && f.length() >= 8) {
            val bytes = f.readBytes()
            val lb = ByteBuffer.wrap(bytes).asLongBuffer()
            LongArray(lb.remaining()).also { lb.get(it) }
        } else LongArray(0)
        reloadRules(Prefs(ctx))
    }

    fun reloadRules(prefs: Prefs) {
        uboCompat = prefs.uboCompat
        white = rules(prefs.whitelist)
        black = rules(prefs.blacklist)
    }

    fun save(ctx: Context, sorted: LongArray) {
        val bb = ByteBuffer.allocate(sorted.size * 8)
        bb.asLongBuffer().put(sorted)
        val tmp = File(ctx.filesDir, "blocklist.tmp")
        tmp.writeBytes(bb.array())
        tmp.renameTo(file(ctx))
        hashes = sorted
    }

    val size: Int get() = hashes.size

    fun verdict(domain: String): Verdict {
        val d = clean(domain)
        if (d.isEmpty()) return Verdict.ALLOWED
        if (white.matches(d)) return Verdict.WHITELISTED
        if (black.matches(d)) return Verdict.BLACKLISTED
        if (uboCompat && uboRules.matches(d)) return Verdict.ALLOWED
        return if (java.util.Arrays.binarySearch(hashes, hash(d)) >= 0) Verdict.BLOCKED else Verdict.ALLOWED
    }

    fun isBlocked(domain: String): Boolean {
        val v = verdict(domain)
        return v == Verdict.BLOCKED || v == Verdict.BLACKLISTED
    }
}
