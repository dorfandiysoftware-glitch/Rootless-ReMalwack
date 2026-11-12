package dev.dorfandiy.remalwack.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import dev.dorfandiy.remalwack.data.Blocklist
import dev.dorfandiy.remalwack.data.Prefs
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.Executors

/**
 * Local DNS filter. The VPN interface only routes the (fake) DNS server address, so the rest of the
 * traffic never enters the tunnel. DNS queries for blocked names are answered with 0.0.0.0 / ::,
 * everything else is forwarded to the real upstream resolver.
 */
class DnsVpnService : VpnService() {

    private var tun: ParcelFileDescriptor? = null
    private var worker: Thread? = null
    @Volatile private var running = false
    private val pool = Executors.newFixedThreadPool(6)
    private lateinit var out: FileOutputStream

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopVpn(); stopSelf(); return START_NOT_STICKY }
            ACTION_RELOAD -> { Thread { Blocklist.load(this) }.start(); return START_STICKY }
        }
        startVpn()
        return START_STICKY
    }

    private fun startVpn() {
        if (running) return
        Blocklist.load(this)
        val upstream = runCatching { InetAddress.getByName(Prefs(this).upstreamDns) }
            .getOrElse { InetAddress.getByName("1.1.1.1") }
        val fd = Builder()
            .setSession("Re-Malwack")
            .addAddress(CLIENT, 32)
            .addDnsServer(FAKE_DNS)
            .addRoute(FAKE_DNS, 32)
            .setMtu(1500)
            .setBlocking(true)
            .establish() ?: return
        tun = fd
        running = true
        VpnState.running.value = true
        val input = FileInputStream(fd.fileDescriptor)
        out = FileOutputStream(fd.fileDescriptor)
        worker = Thread({
            val buf = ByteArray(32767)
            try {
                while (running) {
                    val n = input.read(buf)
                    if (n <= 0) continue
                    handle(buf, n, upstream)
                }
            } catch (_: Exception) {
            } finally {
                if (running) { stopVpn(); }
            }
        }, "dns-vpn").also { it.start() }
    }

    private fun stopVpn() {
        running = false
        VpnState.running.value = false
        runCatching { tun?.close() }
        tun = null
    }

    override fun onDestroy() {
        stopVpn()
        pool.shutdownNow()
        super.onDestroy()
    }

    override fun onRevoke() {
        stopVpn()
        super.onRevoke()
    }

    // ---- packet handling ----

    private fun handle(buf: ByteArray, len: Int, upstream: InetAddress) {
        if ((buf[0].toInt() shr 4) != 4) return
        val ihl = (buf[0].toInt() and 0x0F) * 4
        if (len < ihl + 8 || buf[9].toInt() != 17) return // UDP only
        val dstPort = ((buf[ihl + 2].toInt() and 0xFF) shl 8) or (buf[ihl + 3].toInt() and 0xFF)
        if (dstPort != 53) return
        val total = (((buf[2].toInt() and 0xFF) shl 8) or (buf[3].toInt() and 0xFF)).coerceAtMost(len)
        val dns = buf.copyOfRange(ihl + 8, total)
        if (dns.size < 17) return
        val srcIp = buf.copyOfRange(12, 16)
        val dstIp = buf.copyOfRange(16, 20)
        val srcPort = ((buf[ihl].toInt() and 0xFF) shl 8) or (buf[ihl + 1].toInt() and 0xFF)

        val q = parseQuestion(dns) ?: run { forward(dns, srcIp, dstIp, srcPort, upstream); return }
        val blocked = Blocklist.isBlocked(q.name)
        VpnState.record(q.name, blocked)
        if (blocked && (q.type == 1 || q.type == 28 || q.type == 65)) {
            write(buildBlocked(dns, q), srcIp, dstIp, srcPort)
        } else {
            forward(dns, srcIp, dstIp, srcPort, upstream)
        }
    }

    private class Question(val name: String, val type: Int, val end: Int)

    private fun parseQuestion(d: ByteArray): Question? {
        if (d.size < 12) return null
        val qd = ((d[4].toInt() and 0xFF) shl 8) or (d[5].toInt() and 0xFF)
        if (qd < 1) return null
        var p = 12
        val sb = StringBuilder()
        while (true) {
            if (p >= d.size) return null
            val l = d[p].toInt() and 0xFF
            if (l == 0) { p++; break }
            if (l and 0xC0 != 0 || p + 1 + l > d.size) return null
            if (sb.isNotEmpty()) sb.append('.')
            for (i in 1..l) sb.append((d[p + i].toInt() and 0xFF).toChar())
            p += 1 + l
        }
        if (p + 4 > d.size) return null
        val type = ((d[p].toInt() and 0xFF) shl 8) or (d[p + 1].toInt() and 0xFF)
        return Question(sb.toString().lowercase(), type, p + 4)
    }

    private fun buildBlocked(q: ByteArray, qu: Question): ByteArray {
        val answerLen = when (qu.type) { 1 -> 16; 28 -> 28; else -> 0 }
        val r = ByteArray(qu.end + answerLen)
        System.arraycopy(q, 0, r, 0, qu.end)
        r[2] = (0x80 or (q[2].toInt() and 0x01)).toByte() // QR=1, keep RD
        r[3] = 0x80.toByte()                                // RA=1, RCODE=0
        r[6] = 0; r[7] = if (answerLen > 0) 1 else 0       // ANCOUNT
        r[8] = 0; r[9] = 0; r[10] = 0; r[11] = 0           // NS/AR = 0
        if (answerLen > 0) {
            var p = qu.end
            r[p++] = 0xC0.toByte(); r[p++] = 0x0C            // name pointer
            r[p++] = 0; r[p++] = qu.type.toByte()            // type
            r[p++] = 0; r[p++] = 1                           // class IN
            r[p++] = 0; r[p++] = 0; r[p++] = 0; r[p++] = 60  // TTL
            r[p++] = 0; r[p++] = if (qu.type == 1) 4 else 16 // RDLENGTH, RDATA stays zero
        }
        return r
    }

    private fun forward(dns: ByteArray, srcIp: ByteArray, dstIp: ByteArray, srcPort: Int, upstream: InetAddress) {
        pool.execute {
            try {
                DatagramSocket().use { s ->
                    protect(s)
                    s.soTimeout = 4000
                    s.send(DatagramPacket(dns, dns.size, upstream, 53))
                    val rb = ByteArray(4096)
                    val p = DatagramPacket(rb, rb.size)
                    s.receive(p)
                    write(rb.copyOf(p.length), srcIp, dstIp, srcPort)
                }
            } catch (_: Exception) { }
        }
    }

    /** Wraps a DNS payload in IPv4+UDP (addresses swapped) and writes it to the tunnel. */
    private fun write(dns: ByteArray, clientIp: ByteArray, dnsIp: ByteArray, clientPort: Int) {
        val total = 20 + 8 + dns.size
        val p = ByteArray(total)
        p[0] = 0x45
        p[2] = (total shr 8).toByte(); p[3] = total.toByte()
        p[6] = 0x40 // don't fragment
        p[8] = 64; p[9] = 17
        System.arraycopy(dnsIp, 0, p, 12, 4)
        System.arraycopy(clientIp, 0, p, 16, 4)
        var sum = 0
        for (i in 0 until 20 step 2) sum += ((p[i].toInt() and 0xFF) shl 8) or (p[i + 1].toInt() and 0xFF)
        while (sum shr 16 != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        val ck = sum.inv() and 0xFFFF
        p[10] = (ck shr 8).toByte(); p[11] = ck.toByte()
        p[20] = 0; p[21] = 53
        p[22] = (clientPort shr 8).toByte(); p[23] = clientPort.toByte()
        val ul = 8 + dns.size
        p[24] = (ul shr 8).toByte(); p[25] = ul.toByte()
        System.arraycopy(dns, 0, p, 28, dns.size)
        synchronized(this) { runCatching { out.write(p) } }
    }

    companion object {
        const val ACTION_STOP = "dev.dorfandiy.remalwack.STOP"
        const val ACTION_RELOAD = "dev.dorfandiy.remalwack.RELOAD"
        private const val CLIENT = "10.111.222.1"
        private const val FAKE_DNS = "10.111.222.2"

        fun start(ctx: Context) { ctx.startService(Intent(ctx, DnsVpnService::class.java)) }
        fun stop(ctx: Context) { ctx.startService(Intent(ctx, DnsVpnService::class.java).setAction(ACTION_STOP)) }
        fun reload(ctx: Context) {
            if (VpnState.running.value) ctx.startService(Intent(ctx, DnsVpnService::class.java).setAction(ACTION_RELOAD))
        }
    }
}
