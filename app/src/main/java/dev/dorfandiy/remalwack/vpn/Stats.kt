package dev.dorfandiy.remalwack.vpn

import kotlinx.coroutines.flow.MutableStateFlow

data class LogEntry(val domain: String, val blocked: Boolean, val time: Long)

object VpnState {
    val running = MutableStateFlow(false)
    val queries = MutableStateFlow(0L)
    val blocked = MutableStateFlow(0L)
    val recent = MutableStateFlow<List<LogEntry>>(emptyList())

    @Synchronized
    fun record(domain: String, wasBlocked: Boolean) {
        queries.value = queries.value + 1
        if (wasBlocked) blocked.value = blocked.value + 1
        val e = LogEntry(domain, wasBlocked, System.currentTimeMillis())
        recent.value = (listOf(e) + recent.value).take(60)
    }

    fun reset() {
        queries.value = 0; blocked.value = 0; recent.value = emptyList()
    }
}
