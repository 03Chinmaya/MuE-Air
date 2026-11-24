package com.mue.core.network

import android.util.Log
import okhttp3.Dns
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.UnknownHostException

object ReliableDns : Dns {
    private val staticMap = mapOf(
        "www.jiosaavn.com" to listOf("23.36.177.43", "23.36.177.67"),
        "c.saavncdn.com" to listOf("167.82.58.144", "167.82.62.144"),
        "aac.saavncdn.com" to listOf("167.82.58.144", "167.82.62.144"),
        "jiotunepreview.jio.com" to listOf("49.44.79.17")
    )

    override fun lookup(hostname: String): List<InetAddress> {
        try {
            val addresses = Dns.SYSTEM.lookup(hostname)
            if (addresses.isNotEmpty()) return addresses
        } catch (_: Exception) {
            Log.w("ReliableDns", "System DNS failed for $hostname, using fallback resolver...")
        }

        staticMap[hostname]?.let { ips ->
            return ips.map { InetAddress.getByName(it) }
        }

        if (hostname.endsWith("jiosaavn.com")) {
            return listOf(InetAddress.getByName("23.36.177.43"), InetAddress.getByName("23.36.177.67"))
        }
        if (hostname.endsWith("saavncdn.com")) {
            return listOf(InetAddress.getByName("167.82.58.144"), InetAddress.getByName("167.82.62.144"))
        }

        // Try direct DNS query to 8.8.8.8 over UDP (works even when system resolver fails)
        try {
            val addresses = queryUdpDns(hostname, "8.8.8.8")
            if (addresses.isNotEmpty()) return addresses
        } catch (e: Exception) {
            Log.w("ReliableDns", "UDP DNS query to 8.8.8.8 failed for $hostname: ${e.message}")
        }

        throw UnknownHostException("Unable to resolve $hostname via System or ReliableDns")
    }

    private fun queryUdpDns(hostname: String, dnsServerIp: String): List<InetAddress> {
        val socket = DatagramSocket()
        socket.soTimeout = 2500
        try {
            val query = ByteArray(512)
            // Transaction ID
            query[0] = 0x12; query[1] = 0x34
            // Flags: standard query, recursion desired
            query[2] = 0x01; query[3] = 0x00
            // QDCOUNT = 1
            query[4] = 0x00; query[5] = 0x01

            var offset = 12
            for (part in hostname.split('.')) {
                val bytes = part.toByteArray()
                query[offset++] = bytes.size.toByte()
                System.arraycopy(bytes, 0, query, offset, bytes.size)
                offset += bytes.size
            }
            query[offset++] = 0x00 // end of labels

            // QTYPE: A (0x0001)
            query[offset++] = 0x00; query[offset++] = 0x01
            // QCLASS: IN (0x0001)
            query[offset++] = 0x00; query[offset++] = 0x01

            val dest = InetAddress.getByName(dnsServerIp)
            val packet = DatagramPacket(query, offset, dest, 53)
            socket.send(packet)

            val responseBuf = ByteArray(512)
            val responsePacket = DatagramPacket(responseBuf, responseBuf.size)
            socket.receive(responsePacket)

            val anCount = ((responseBuf[6].toInt() and 0xFF) shl 8) or (responseBuf[7].toInt() and 0xFF)
            if (anCount == 0) return emptyList()

            var pos = 12
            while (pos < responsePacket.length && responseBuf[pos].toInt() != 0) {
                val len = responseBuf[pos].toInt() and 0xFF
                if (len and 0xC0 == 0xC0) {
                    pos += 2
                    break
                }
                pos += len + 1
            }
            if (pos < responsePacket.length && responseBuf[pos].toInt() == 0) pos++
            pos += 4 // QTYPE + QCLASS

            val result = mutableListOf<InetAddress>()
            for (i in 0 until anCount) {
                if (pos >= responsePacket.length) break
                if ((responseBuf[pos].toInt() and 0xC0) == 0xC0) {
                    pos += 2
                } else {
                    while (pos < responsePacket.length && responseBuf[pos].toInt() != 0) {
                        pos += (responseBuf[pos].toInt() and 0xFF) + 1
                    }
                    pos++
                }
                if (pos + 10 > responsePacket.length) break
                val type = ((responseBuf[pos].toInt() and 0xFF) shl 8) or (responseBuf[pos + 1].toInt() and 0xFF)
                pos += 8 // type(2) + class(2) + ttl(4)
                val dataLen = ((responseBuf[pos].toInt() and 0xFF) shl 8) or (responseBuf[pos + 1].toInt() and 0xFF)
                pos += 2
                if (type == 1 && dataLen == 4 && pos + 4 <= responsePacket.length) {
                    val ipBytes = ByteArray(4)
                    System.arraycopy(responseBuf, pos, ipBytes, 0, 4)
                    result.add(InetAddress.getByAddress(ipBytes))
                }
                pos += dataLen
            }
            return result
        } finally {
            socket.close()
        }
    }
}

