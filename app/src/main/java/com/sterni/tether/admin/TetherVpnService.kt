package com.sterni.tether.admin

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.sterni.tether.data.model.WebFilterMode
import com.sterni.tether.data.model.CommunityPolicy
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer

class TetherVpnService : VpnService() {

    companion object {
        const val TAG = "TetherVpn"
        const val ACTION_DOMAIN_BLOCKED = "com.sterni.tether.DOMAIN_BLOCKED"
        const val EXTRA_DOMAIN = "domain"
        const val CHANNEL_ID = "tether_vpn"
        const val NOTIF_ID = 9001

        // All packages that constitute "Google Play" — routing only these through VPN lets us
        // drop all their traffic while leaving every other app untouched (WhatsApp etc.)
        val PLAY_STORE_PACKAGES = listOf(
            "com.android.vending",       // Play Store UI
            "com.google.android.finsky", // Play Store backend
            "com.google.android.gsf"     // Google Services Framework (push / FCM trigger)
        )

        @Volatile
        var isRunning = false
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var vpnOutput: FileOutputStream? = null
    @Volatile private var running = false

    // true  → addAllowedApplication mode: ONLY Play Store traffic enters the VPN, everything dropped
    // false → DNS-only mode: only 10.0.0.1:53 enters the VPN, TCP bypasses entirely
    @Volatile private var playStoreKillMode = false

    // ניהול תהליכי רקע מודרני (Coroutines)
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    // מנגנון סנכרון לכתיבה חזרה ל-VPN Interface
    private val writeLock = Any()

    // מטמון (Cache) כדי לא לקרוא מהדיסק על כל פאקט רשת
    private var cachedPolicy: CommunityPolicy? = null
    private var lastPolicySyncTime = 0L
    private val POLICY_CACHE_DURATION_MS = 60_000L // עדכון מהדיסק רק פעם בדקה

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

        val policy = TetherPolicyManager.loadPolicy(this)
        val hidePlayStore = policy?.hideGooglePlay == true
        val webFilterActive = policy?.webFilterMode != null &&
                policy.webFilterMode != WebFilterMode.NONE

        val builder = Builder()
            .setSession("Tether Filter")
            .addAddress("10.0.0.2", 32)
            .addDnsServer("10.0.0.1")
            .setMtu(1500)

        if (hidePlayStore && !webFilterActive) {
            // Kill-mode: route ONLY Play Store packages through the VPN and drop every packet.
            // All other apps (WhatsApp, browsers, …) bypass the VPN entirely — nothing breaks.
            playStoreKillMode = true
            PLAY_STORE_PACKAGES.forEach { pkg ->
                runCatching { builder.addAllowedApplication(pkg) }
            }
            builder.addRoute("0.0.0.0", 0)
            Log.i(TAG, "VPN: Play Store kill-mode — all Play Store traffic will be dropped")
        } else {
            // DNS-only mode: only packets destined to our fake DNS server (10.0.0.1:53) enter
            // the VPN. All TCP and non-DNS UDP bypass the VPN → WhatsApp, etc. unaffected.
            // When hideGooglePlay is also true here, Play Store DNS is NXDOMAIN'd below.
            playStoreKillMode = false
            builder.addRoute("10.0.0.1", 32)
            Log.i(TAG, "VPN: DNS-filter mode (hidePlay=$hidePlayStore, webFilter=$webFilterActive)")
        }

        vpnInterface = builder.establish() ?: run {
            Log.e(TAG, "Failed to establish VPN")
            stopSelf()
            return
        }

        vpnOutput = FileOutputStream(vpnInterface!!.fileDescriptor)
        isRunning = true
        running = true

        // מתחיל את לולאת הקריאה בקורוטינה נפרדת
        serviceScope.launch(Dispatchers.IO) {
            runVpnLoop()
        }
    }

    private suspend fun runVpnLoop() {
        val input = FileInputStream(vpnInterface!!.fileDescriptor)
        val packet = ByteBuffer.allocate(32767)

        while (running) {
            try {
                packet.clear()
                val length = input.read(packet.array())
                if (length <= 0) continue

                // אנחנו חייבים עותק של הפאקט כי אנחנו משחררים אותו ל-Thread אחר
                val packetCopy = packet.array().copyOf(length)

                // מעבדים את הפאקט בצורה אסינכרונית (כדי לא לעצור את שאר התעבורה)
                serviceScope.launch {
                    processPacket(packetCopy, length)
                }

            } catch (e: Exception) {
                if (running) Log.e(TAG, "VPN loop error: ${e.message}")
            }
        }
    }

    private suspend fun processPacket(packetData: ByteArray, length: Int) {
        // In kill-mode the VPN only sees Play Store traffic — drop every packet unconditionally.
        // DNS, TCP, QUIC, everything: Play Store gets no response and all connections time out.
        if (playStoreKillMode) return

        if (length < 20) return
        val protocol = packetData[9].toInt() and 0xFF
        if (protocol != 17) { // 17 = UDP
            writeToVpn(packetData, length)
            return
        }

        val ipHeaderLen = (packetData[0].toInt() and 0x0F) * 4
        if (length < ipHeaderLen + 8) return

        val dstPort = ((packetData[ipHeaderLen + 2].toInt() and 0xFF) shl 8) or
                (packetData[ipHeaderLen + 3].toInt() and 0xFF)

        if (dstPort != 53) {
            writeToVpn(packetData, length)
            return
        }

        // חילוץ DNS
        val dnsOffset = ipHeaderLen + 8
        val dnsLength = length - dnsOffset
        if (dnsLength < 12) return

        val dnsData = packetData.copyOfRange(dnsOffset, dnsOffset + dnsLength)
        val domain = parseDnsDomain(dnsData)
        if (domain == null) {
            writeToVpn(packetData, length)
            return
        }

        // טעינת פוליסה מתוך מטמון יעיל (ללא I/O מיותר)
        val policy = getActivePolicy()
        val blocked = isDomainBlockedByPolicy(domain, policy)

        if (blocked) {
            Log.i(TAG, "Blocked domain: $domain")
            broadcastBlocked(domain)
            val nxdomain = buildNxDomainResponse(dnsData)
            val response = buildIpUdpResponse(packetData, ipHeaderLen, nxdomain)
            writeToVpn(response, response.size)
        } else {
            // Forwarding אסינכרוני שלא תוקע את האפליקציה!
            val response = forwardDnsAsync(dnsData)
            if (response != null) {
                val ipResponse = buildIpUdpResponse(packetData, ipHeaderLen, response)
                writeToVpn(ipResponse, ipResponse.size)
            }
        }
    }

    // ── ניהול מטמון (Cache) למדיניות ──────────────────────────────────────────

    private fun getActivePolicy(): CommunityPolicy? { // שנה Type אם קוראים לו אחרת
        val now = System.currentTimeMillis()
        if (cachedPolicy == null || (now - lastPolicySyncTime > POLICY_CACHE_DURATION_MS)) {
            cachedPolicy = TetherPolicyManager.loadPolicy(this)
            lastPolicySyncTime = now
        }
        return cachedPolicy
    }

    // ── פעולת רשת אסינכרונית ──────────────────────────────────────────────────

    private suspend fun forwardDnsAsync(query: ByteArray): ByteArray? = withContext(Dispatchers.IO) {
        try {
            DatagramSocket().use { socket ->
                protect(socket)
                val upstream = InetAddress.getByName("8.8.8.8")
                socket.send(DatagramPacket(query, query.size, upstream, 53))
                socket.soTimeout = 3000
                val buf = ByteArray(512)
                val resp = DatagramPacket(buf, buf.size)
                socket.receive(resp)
                buf.copyOf(resp.length)
            }
        } catch (e: Exception) {
            null
        }
    }

    // ── כתיבה בטוחה (Thread-Safe) לממשק ה-VPN ─────────────────────────────────

    private fun writeToVpn(data: ByteArray, length: Int) {
        if (!running) return
        synchronized(writeLock) {
            try {
                vpnOutput?.write(data, 0, length)
            } catch (e: Exception) {
                if (running) Log.e(TAG, "Failed to write to VPN: ${e.message}")
            }
        }
    }

    // ── פונקציות העזר נשארו כמעט זהות, רק הוסדר ה-Type ────────────────────────

    private fun parseDnsDomain(dns: ByteArray): String? {
        return try {
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

    /**
     * Central block decision.
     *
     * Priority order:
     *  1. hideGooglePlay → always block Play Store endpoints (regardless of webFilterMode)
     *  2. BLACKLIST → block domains in blockedDomains list
     *  3. WHITELIST → block anything NOT in allowedDomains
     */
    private fun isDomainBlockedByPolicy(domain: String, policy: CommunityPolicy?): Boolean {
        // חוסם הורדת APK מגוגל תמיד כשמחובר — ללא קשר לפוליסי
        if (TetherPolicyManager.isEnrolled(this) && isPlayStoreDomain(domain)) return true

        if (policy == null) return false

        return when (policy.webFilterMode) {
            WebFilterMode.BLACKLIST ->
                policy.blockedDomains.any { domain == it || domain.endsWith(".$it") }
            WebFilterMode.WHITELIST ->
                !isDomainAllowed(domain, policy.allowedDomains)
            else -> false
        }
    }

    /**
     * Play Store infrastructure domains.
     * Blocking these prevents the Play Store app from downloading or installing anything
     * even if the user manages to open it.
     */
    private fun isPlayStoreDomain(domain: String): Boolean {
        val playDomains = listOf(
            // Play Store API
            "play.google.com",
            "play.googleapis.com",
            "android.clients.google.com",
            "market.android.com",
            "androidmarket.com",
            "vending.android.clients.google.com",
            // APK download CDN — blocks remote installs triggered from web
            "redirector.gvt1.com",
            "gvt1.com",
            "gvt2.com",
            "dl.google.com",
            "dl-ssl.google.com",
            "commondatastorage.googleapis.com",
            "storage.googleapis.com",
            "ggpht.com",
            // Play Store backend
            "playatoms-pa.googleapis.com",
            "playstore.google.com",
            "instantapps.googleapis.com"
        )
        return playDomains.any { d -> domain == d || domain.endsWith(".$d") }
    }

    private fun isDomainAllowed(domain: String, allowedDomains: List<String>): Boolean {
        if (domain.contains("dahanswebsite.com")) return true
        return allowedDomains.any { allowed ->
            domain == allowed || domain.endsWith(".$allowed")
        }
    }

    private fun buildNxDomainResponse(query: ByteArray): ByteArray {
        val response = query.copyOf()
        response[2] = (0x81).toByte()
        response[3] = (0x83).toByte()
        response[6] = 0; response[7] = 0
        response[8] = 0; response[9] = 0
        response[10] = 0; response[11] = 0
        return response
    }

    private fun buildIpUdpResponse(originalIp: ByteArray, ipHeaderLen: Int, dnsPayload: ByteArray): ByteArray {
        val totalLen = ipHeaderLen + 8 + dnsPayload.size
        val pkt = ByteArray(totalLen)

        System.arraycopy(originalIp, 0, pkt, 0, ipHeaderLen)
        System.arraycopy(originalIp, 12, pkt, 16, 4) 
        System.arraycopy(originalIp, 16, pkt, 12, 4) 
        
        pkt[2] = (totalLen shr 8).toByte()
        pkt[3] = (totalLen and 0xFF).toByte()
        pkt[8] = 64

        pkt[ipHeaderLen] = originalIp[ipHeaderLen + 2]
        pkt[ipHeaderLen + 1] = originalIp[ipHeaderLen + 3]
        pkt[ipHeaderLen + 2] = originalIp[ipHeaderLen]
        pkt[ipHeaderLen + 3] = originalIp[ipHeaderLen + 1]
        
        val udpLen = 8 + dnsPayload.size
        pkt[ipHeaderLen + 4] = (udpLen shr 8).toByte()
        pkt[ipHeaderLen + 5] = (udpLen and 0xFF).toByte()
        pkt[ipHeaderLen + 6] = 0 
        pkt[ipHeaderLen + 7] = 0

        System.arraycopy(dnsPayload, 0, pkt, ipHeaderLen + 8, dnsPayload.size)

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

    private fun broadcastBlocked(domain: String) {
        val intent = Intent(ACTION_DOMAIN_BLOCKED).putExtra(EXTRA_DOMAIN, domain)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun stopVpn() {
        running = false
        isRunning = false
        serviceJob.cancelChildren() // עצירת כל תהליכי הרקע הפעילים
        vpnOutput?.close()
        vpnInterface?.close()
        vpnInterface = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onRevoke() {
        stopVpn()
    }

    override fun onDestroy() {
        serviceJob.cancel()
        super.onDestroy()
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
