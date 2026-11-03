package com.jahid.neonduel

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** LAN TCP transport. The hotspot owner's phone is both player zero and server. */
private class LocalConnection(
    private val socket: Socket,
    private val onMessage: (String) -> Unit,
    private val onClosed: (String) -> Unit
) {
    private val alive = AtomicBoolean(true)
    private val pending = LinkedBlockingDeque<String>(48)
    fun send(line: String) {
        if (!alive.get()) return
        if (!pending.offerLast(line)) { pending.pollFirst(); pending.offerLast(line) }
    }
    fun run() {
        socket.tcpNoDelay = true
        Thread({
            try {
                val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))
                while (alive.get()) {
                    val line = pending.pollFirst(400, TimeUnit.MILLISECONDS) ?: continue
                    writer.write(line); writer.newLine(); writer.flush()
                }
            } catch (_: Exception) { close("Connection lost") }
        }, "NeonLanWrite").start()
        Thread({
            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                while (alive.get()) {
                    val line = reader.readLine() ?: break
                    if (line.length > 16000) break
                    onMessage(line)
                }
            } catch (_: Exception) { /* Will report disconnect below. */ }
            finally { close("Connection closed") }
        }, "NeonLanRead").start()
    }
    fun close(reason: String = "Closed") {
        if (alive.compareAndSet(true, false)) {
            try { socket.close() } catch (_: Exception) {}
            onClosed(reason)
        }
    }
}

class HotspotHost(
    private val onJoined: (Int) -> Unit,
    private val onLine: (Int, String) -> Unit,
    private val onLeft: (Int) -> Unit,
    private val onFailure: (String) -> Unit
) {
    companion object { const val PORT = 37841 }
    private val running = AtomicBoolean(false)
    private var server: ServerSocket? = null
    private val peers = ConcurrentHashMap<Int, LocalConnection>()
    @Volatile private var locked = false

    fun start() {
        if (!running.compareAndSet(false, true)) return
        Thread({
            try {
                val ss = ServerSocket()
                ss.reuseAddress = true
                ss.bind(InetSocketAddress(PORT))
                server = ss
                while (running.get()) {
                    val socket = ss.accept()
                    if (!running.get()) { socket.close(); break }
                    synchronized(peers) {
                        val id = (1..9).firstOrNull { !peers.containsKey(it) }
                        if (locked || id == null) {
                            try {
                                socket.getOutputStream().write((OnlinePackets.reject(
                                    if (locked) "Match already started" else "Room is full (10 players)") + "\n").toByteArray())
                            } catch (_: Exception) {}
                            socket.close()
                        } else {
                            lateinit var conn: LocalConnection
                            conn = LocalConnection(socket,
                                { onLine(id, it) },
                                { peers.remove(id, conn); if (running.get()) onLeft(id) })
                            peers[id] = conn
                            conn.send(OnlinePackets.welcome(id))
                            conn.run()
                            onJoined(id)
                        }
                    }
                }
            } catch (e: Exception) {
                if (running.get()) onFailure(e.message ?: "Cannot open hotspot server")
            }
        }, "NeonHotspotAccept").start()
    }
    fun ids(): List<Int> = listOf(0) + peers.keys.sorted()
    fun lock() { synchronized(peers) { locked = true } }
    fun unlock() { synchronized(peers) { locked = false } }
    fun send(id: Int, line: String) { peers[id]?.send(line) }
    fun broadcast(line: String) { peers.values.forEach { it.send(line) } }
    fun stop() {
        running.set(false)
        try { server?.close() } catch (_: Exception) {}
        peers.values.forEach { it.close() }
        peers.clear()
    }
}

class HotspotGuest(
    private val onLine: (String) -> Unit,
    private val onClosed: (String) -> Unit
) {
    private var connection: LocalConnection? = null
    private val connecting = AtomicBoolean(false)
    fun connect(ip: String) {
        if (!connecting.compareAndSet(false, true)) return
        Thread({
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(ip, HotspotHost.PORT), 7000)
                if (!connecting.get()) { socket.close(); return@Thread }
                connection = LocalConnection(socket, onLine, onClosed)
                connection?.run()
            } catch (e: Exception) {
                if (connecting.get()) onClosed(e.message ?: "Unable to connect to host")
            }
        }, "NeonHotspotJoin").start()
    }
    fun send(line: String) { connection?.send(line) }
    fun stop() { connecting.set(false); connection?.close(); connection = null }
}
