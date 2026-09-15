package com.curbme.app.core.utils

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer

/**
 * Direct DNS lookup to CleanBrowsing Adult Filter DNS (185.228.168.10 / 185.228.169.10)
 * over a dedicated UDP socket. Checks whether a domain is classified as an adult site
 * independently of system DNS and VPN state.
 */
object OnlineAdultChecker {

    private const val TAG = "OnlineAdultChecker"
    private const val PRIMARY_DNS_IP = "185.228.168.10"
    private const val SECONDARY_DNS_IP = "185.228.169.10"
    private const val DNS_PORT = 53
    private const val TIMEOUT_MS = 1500

    suspend fun isAdultSite(domain: String): Boolean = withContext(Dispatchers.IO) {
        if (domain.isBlank()) return@withContext false
        val cleanDomain = domain.trim().lowercase().removePrefix("www.")
        if (!cleanDomain.contains('.')) return@withContext false

        // Try primary IP, fallback to secondary on failure/timeout
        for (dnsIp in arrayOf(PRIMARY_DNS_IP, SECONDARY_DNS_IP)) {
            try {
                val isBlocked = queryDnsServer(cleanDomain, dnsIp)
                if (isBlocked != null) {
                    return@withContext isBlocked
                }
            } catch (e: Exception) {
                Log.w(TAG, "DNS query failed for $dnsIp: ${e.message}")
            }
        }
        return@withContext false
    }

    private fun queryDnsServer(domain: String, dnsIp: String): Boolean? {
        return try {
            DatagramSocket().use { socket ->
                socket.soTimeout = TIMEOUT_MS
                val queryPacket = buildDnsQueryPacket(domain)
                val address = InetAddress.getByName(dnsIp)
                val sendPacket = DatagramPacket(queryPacket, queryPacket.size, address, DNS_PORT)
                socket.send(sendPacket)

                val responseBuffer = ByteArray(512)
                val receivePacket = DatagramPacket(responseBuffer, responseBuffer.size)
                socket.receive(receivePacket)

                parseIsBlockedResponse(receivePacket.data, receivePacket.length)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun buildDnsQueryPacket(domain: String): ByteArray {
        val buffer = ByteBuffer.allocate(512)

        // Header: ID (0x1234), Flags (0x0100 standard query), Questions (1), Answers (0), Auth (0), Add (0)
        buffer.putShort(0x1234.toShort())
        buffer.putShort(0x0100.toShort())
        buffer.putShort(1.toShort())
        buffer.putShort(0.toShort())
        buffer.putShort(0.toShort())
        buffer.putShort(0.toShort())

        // Question: Domain Name Labels
        for (part in domain.split('.')) {
            val bytes = part.toByteArray(Charsets.US_ASCII)
            buffer.put(bytes.size.toByte())
            buffer.put(bytes)
        }
        buffer.put(0.toByte()) // End label

        // QTYPE (1 = A), QCLASS (1 = IN)
        buffer.putShort(1.toShort())
        buffer.putShort(1.toShort())

        val packet = ByteArray(buffer.position())
        buffer.flip()
        buffer.get(packet)
        return packet
    }

    private fun parseIsBlockedResponse(data: ByteArray, length: Int): Boolean {
        if (length < 12) return false

        // Check Response Code (RCODE in header byte 3)
        val rcode = data[3].toInt() and 0x0F
        if (rcode == 3) {
            // NXDOMAIN returned by CleanBrowsing Adult Filter -> Blocked/Adult site!
            return true
        }

        // Search response for 0.0.0.0 or 127.0.0.1 in answer records
        for (i in 12 until length - 3) {
            // 0.0.0.0
            if (data[i] == 0.toByte() && data[i + 1] == 0.toByte() &&
                data[i + 2] == 0.toByte() && data[i + 3] == 0.toByte()
            ) {
                return true
            }
            // 127.0.0.1
            if (data[i] == 127.toByte() && data[i + 1] == 0.toByte() &&
                data[i + 2] == 0.toByte() && data[i + 3] == 1.toByte()
            ) {
                return true
            }
        }

        return false
    }
}
