package dev.dorfandiy.remalwack.shizuku

import dev.dorfandiy.remalwack.IShellService
import kotlin.system.exitProcess

/** Runs inside the Shizuku server process with shell (adb) privileges. */
class ShellService : IShellService.Stub() {
    override fun destroy() { exitProcess(0) }

    override fun exec(command: String): String = try {
        val p = ProcessBuilder("sh", "-c", command).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        val code = p.waitFor()
        if (code == 0) out.trim() else "ERR($code): ${out.trim()}"
    } catch (e: Exception) {
        "ERR: ${e.message}"
    }
}
