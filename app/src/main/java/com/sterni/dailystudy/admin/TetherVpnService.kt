package com.sterni.dailystudy.admin

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.sterni.dailystudy.data.model.WebFilterMode
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer

class TetherVpnService : VpnService() {

    companion object {
        const val TAG = "TetherVpn"
        const val ACTION_DOMAIN_BLOCKED = "com.sterni.dailystudy.DOMAIN_BLOCKED"
        const val EXTRA_DOMAIN = "domain"
        const val CHANNEL_ID = "tether_vpn"
        const val NOTIF_ID = 9001

        @Volatile
        var isRunning = false
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var running = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopVpn()
            return START_NOT_STICKY
        }
        startVpn()
        return START_STICKY
    }

    private fun startVpn() {
        if (isRunning) return

        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())

        val builder = Builder()
            .setSession("Tether Filter")
            .addAddress("10.0.0.2", 32)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("10.0.0.1")
            .setMtu(1500)

        vpnInterface = builder.establish() ?: run {
            Log.e(TAG, "Failed to establish VPN")
            stopSelf()
            return
        }

        isRunning = true
        running = true

        Thread { runVpnLoop() }.start()
    }

    private fun runVpnLoop() {
        val input = FileInputStream(vpnInterface!!.fileDescriptor)
        val output = FileOutputStream(vpnInterface!!.fileDescriptor)
        val packet = ByteBuffer.allocate(32767)

        while (running) {
            try {
                packet.clear()
                val length = input.read(packet.array())
                if (length <= 0) continue
                packet.limit(length)

                // Parse IP header to find UDP port 53 (DNS)
                if (length < 20) continue
                val protocol = packet.get(9).toInt() and 0xFF
                if (protocol != 17) { // 17 = UDP
                    // Forward non-UDP packets as-is
                    output.write(packet.array(), 0, length)
                    continue
                }

                val ipHeaderLen = (packet.get(0).toInt() and 0x0F) * 4
                if (length < ipHeaderLen + 8) continue

                val dstPort = ((packet.get(ipHeaderLen + 2).toInt() and 0xFF) shl 8) or
                        (packet.get(ipHeaderLen + 3).toInt() and 0xFF)

                if (dstPort != 53) {
                    output.write(packet.array(), 0, length)
                    continue
                }

                // Extract DNS payload
                val dnsOffset = ipHeaderLen + 8
                val dnsLength = length - dnsOffset
                if (dnsLength < 12) continue

                val dnsData = packet.array().copyOfRange(dnsOffset, dnsOffset + dnsLength)
                val domain = parseDnsDomain(dnsData)
                if (domain == null) {
                    output.write(packet.array(), 0, length)
                    continue
                }

                val policy = TetherPolicyManager.loadPolicy(this)
                val blocked = when (policy?.webFilterMode) {
                    WebFilterMode.BLACKLIST -> isDomainBlocked(domain, policy.blockedDomains)
                    WebFilterMode.WHITELIST -> !isDomainAllowed(domain, policy.allowedDomains)
                    else -> false
                }

                if (blocked) {
                    Log.i(TAG, "Blocked domain: $domain")
                    broadcastBlocked(domain)
                    val nxdomain = buildNxDomainResponse(dnsData)
                    val response = buildIpUdpResponse(packet.array(), ipHeaderLen, nxdomain)
                    output.write(response)
                } else {
                    val response = forwardDns(dnsData)
                    if (response == null) continue
                    val ipResponse = buildIpUdpResponse(packet.array(), ipHeaderLen, response)
                    output.write(ipResponse)
                }
            } catch (e: Exception) {
                if (running) Log.e(TAG, "VPN loop error: ${e.message}")
            }
        }
    }

    // ── DNS domain name parser ────────────────────────────────────────────────

    private fun parseDnsDomain(dns: ByteArray): String? {
        return try {
            // DNS header is 12 bytes, questions start at offset 12
            val sb = StringBuilder()
            var i = 12
            while (i < dns.size) {
                val len = dns[i].toInt() and 0xFF
                if (len == 0) break
                if (sb.isNotEmpty()) sb.append('.')
                sb.append(String(dns, i + 1, len))
                i += len + 1
            }
            sb.toString().lowercase().ifEmpty { null }
        } catch (e: Exception) {
            null
        }
    }

    private fun isDomainBlocked(domain: String, blockedDomains: List<String>): Boolean {
        return blockedDomains.any { blocked ->
            domain == blocked || domain.endsWith(".$blocked")
        }
    }

    private fun isDomainAllowed(domain: String, allowedDomains: List<String>): Boolean {
        // Always allow our own server
        if (domain.contains("dahanswebsite.com")) return true
        return allowedDomains.any { allowed ->
            domain == allowed || domain.endsWith(".$allowed")
        }
    }

    // ── DNS NXDOMAIN response builder ─────────────────────────────────────────

    private fun buildNxDomainResponse(query: ByteArray): ByteArray {
        val response = query.copyOf()
        // Set QR=1 (response), AA=0, TC=0, RA=1, RCODE=3 (NXDOMAIN)
        response[2] = (0x81).toByte() // QR=1, Opcode=0, AA=0, TC=0, RD=1
        response[3] = (0x83).toByte() // RA=1, RCODE=3 (NXDOMAIN)
        // ANCOUNT, NSCOUNT, ARCOUNT = 0
        response[6] = 0; response[7] = 0
        response[8] = 0; response[9] = 0
        response[10] = 0; response[11] = 0
        return response
    }

    // ── Forward DNS to upstream ───────────────────────────────────────────────

    private fun forwardDns(query: ByteArray): ByteArray? {
        return try {
            val socket = DatagramSocket()
            protect(socket)
            val upstream = InetAddress.getByName("8.8.8.8")
            socket.send(DatagramPacket(query, query.size, upstream, 53))
            socket.soTimeout = 3000
            val buf = ByteArray(512)
            val resp = DatagramPacket(buf, buf.size)
            socket.receive(resp)
            socket.close()
            buf.copyOf(resp.length)
        } catch (e: Exception) {
            null
        }
    }

    // ── IP/UDP packet builder ─────────────────────────────────────────────────

    private fun buildIpUdpResponse(originalIp: ByteArray, ipHeaderLen: Int, dnsPayload: ByteArray): ByteArray {
        val totalLen = ipHeaderLen + 8 + dnsPayload.size
        val pkt = ByteArray(totalLen)

        // Copy and swap src/dst IP
        System.arraycopy(originalIp, 0, pkt, 0, ipHeaderLen)
        // Swap source and destination IP
        System.arraycopy(originalIp, 12, pkt, 16, 4) // original dst → new src
        System.arraycopy(originalIp, 16, pkt, 12, 4) // original src → new dst
        // Total length
        pkt[2] = (totalLen shr 8).toByte()
        pkt[3] = (totalLen and 0xFF).toByte()
        // TTL
        pkt[8] = 64

        // UDP header: swap ports
        pkt[ipHeaderLen] = originalIp[ipHeaderLen + 2]
        pkt[ipHeaderLen + 1] = originalIp[ipHeaderLen + 3]
        pkt[ipHeaderLen + 2] = originalIp[ipHeaderLen]
        pkt[ipHeaderLen + 3] = originalIp[ipHeaderLen + 1]
        val udpLen = 8 + dnsPayload.size
        pkt[ipHeaderLen + 4] = (udpLen shr 8).toByte()
        pkt[ipHeaderLen + 5] = (udpLen and 0xFF).toByte()
        pkt[ipHeaderLen + 6] = 0 // checksum (skip)
        pkt[ipHeaderLen + 7] = 0

        System.arraycopy(dnsPayload, 0, pkt, ipHeaderLen + 8, dnsPayload.size)

        // Recalculate IP checksum
        pkt[10] = 0; pkt[11] = 0
        val checksum = ipChecksum(pkt, ipHeaderLen)
        pkt[10] = (checksum shr 8).toByte()
        pkt[11] = (checksum and 0xFF).toByte()

        return pkt
    }

    private fun ipChecksum(data: ByteArray, headerLen: Int): Int {
        var sum = 0
        var i = 0
        while (i < headerLen - 1) {
            sum += ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            i += 2
        }
        while (sum shr 16 != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        return sum.inv() and 0xFFFF
    }

    // ── Broadcast & notification ──────────────────────────────────────────────

    private fun broadcastBlocked(domain: String) {
        val intent = Intent(ACTION_DOMAIN_BLOCKED).putExtra(EXTRA_DOMAIN, domain)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun stopVpn() {
        running = false
        isRunning = false
        vpnInterface?.close()
        vpnInterface = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onRevoke() {
        stopVpn()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Tether Web Filter",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Tether")
            .setContentText("סינון אינטרנט פעיל")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .build()
}
