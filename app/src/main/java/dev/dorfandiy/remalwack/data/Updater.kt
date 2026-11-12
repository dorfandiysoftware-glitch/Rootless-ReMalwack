package dev.dorfandiy.remalwack.data

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL

object Updater {

    /** Growable primitive long list. */
    private class LongList {
        var a = LongArray(1 shl 16)
        var n = 0
        fun add(v: Long) {
            if (n == a.size) a = a.copyOf(a.size * 2)
            a[n++] = v
        }
    }

    private val VALID = Regex("^[a-z0-9_]([a-z0-9_.-]*[a-z0-9])?$")
    private val SINK = setOf("0.0.0.0", "127.0.0.1", "::", "::1", "0", "255.255.255.255")

    /** Extracts a blockable domain from one line of a hosts / domain-list / adblock file. */
    fun parseLine(raw: String): String? {
        var line = raw
        val hash = line.indexOf('#')
        if (hash >= 0) line = line.substring(0, hash)
        line = line.trim()
        if (line.isEmpty() || line.startsWith("!") || line.startsWith("[")) return null
        val d: String
        if (line.startsWith("||")) {
            val end = line.indexOf('^')
            d = (if (end > 0) line.substring(2, end) else line.substring(2))
        } else {
            val parts = line.split(' ', '\t').filter { it.isNotEmpty() }
            d = when {
                parts.size >= 2 && parts[0] in SINK -> parts[1]
                parts.size == 1 -> parts[0]
                else -> return null
            }
        }
        val c = d.trim().lowercase().trimEnd('.')
        if (c == "localhost" || !c.contains('.') || c.length > 253 || !VALID.matches(c)) return null
        return c
    }

    private fun download(url: String, sink: (String) -> Unit) {
        var u = url
        repeat(5) {
            val c = URL(u).openConnection() as HttpURLConnection
            c.connectTimeout = 15000
            c.readTimeout = 30000
            c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", "Rootless-ReMalwack/0.1")
            try {
                val code = c.responseCode
                if (code in 300..399) {
                    u = c.getHeaderField("Location") ?: throw java.io.IOException("redirect without Location")
                    return@repeat
                }
                if (code != 200) throw java.io.IOException("HTTP $code")
                c.inputStream.bufferedReader().useLines { it.forEach(sink) }
                return
            } finally {
                c.disconnect()
            }
        }
        throw java.io.IOException("too many redirects")
    }

    /** Downloads every active source, merges and stores them. Returns number of unique domains. */
    fun update(ctx: Context, onProgress: (String) -> Unit = {}): Result<Int> = runCatching {
        val prefs = Prefs(ctx)
        val sources = prefs.activeSources()
        val all = LongList()
        var failures = 0
        for (s in sources) {
            onProgress("Downloading ${s.name}…")
            val before = all.n
            try {
                download(s.url) { line -> parseLine(line)?.let { all.add(Blocklist.hash(it)) } }
                prefs.setSourceCount(s.id, all.n - before)
            } catch (e: Exception) {
                failures++
                onProgress("Failed: ${s.name} (${e.message})")
            }
        }
        if (sources.isNotEmpty() && failures == sources.size) throw java.io.IOException("All sources failed — check your connection")
        onProgress("Merging…")
        val arr = all.a.copyOf(all.n)
        arr.sort()
        var w = 0
        for (i in arr.indices) if (i == 0 || arr[i] != arr[i - 1]) arr[w++] = arr[i]
        val unique = arr.copyOf(w)
        Blocklist.save(ctx, unique)
        prefs.totalEntries = unique.size
        prefs.lastUpdated = System.currentTimeMillis()
        unique.size
    }
}
