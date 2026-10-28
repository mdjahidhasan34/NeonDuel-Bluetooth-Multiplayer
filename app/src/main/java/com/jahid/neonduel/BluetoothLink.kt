package com.jahid.neonduel

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.os.Handler
import android.os.Looper
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.UUID
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.atomic.AtomicInteger

/** Bluetooth Classic / RFCOMM. Exactly one phone hosts; the other joins. */
class BluetoothLink(
    private val onConnected: (String) -> Unit,
    private val onLine: (String) -> Unit,
    private val onEnd: (String) -> Unit
) {
    companion object {
        // This UUID MUST be identical in both installed copies of this game.
        val SERVICE_ID: UUID = UUID.fromString("d3069d0e-39c0-4822-98c3-c7b08da26a57")
    }

    private val ui = Handler(Looper.getMainLooper())
    private val generation = AtomicInteger(0)
    @Volatile private var socket: BluetoothSocket? = null
    @Volatile private var server: BluetoothServerSocket? = null
    @Volatile private var outgoing = LinkedBlockingDeque<String>(12)

    fun isConnected(): Boolean = socket?.isConnected == true

    @SuppressLint("MissingPermission") // MainActivity requests Nearby Devices first.
    fun host(adapter: BluetoothAdapter) {
        stop()
        val id = generation.get()
        Thread({
            try {
                val ss = adapter.listenUsingRfcommWithServiceRecord("NEON_DUEL", SERVICE_ID)
                if (id != generation.get()) { ss.close(); return@Thread }
                server = ss
                val accepted = ss.accept()
                ss.close()
                server = null
                if (id == generation.get()) attach(accepted, id) else accepted.close()
            } catch (e: Exception) { ended(id, e.message ?: "Host connection failed") }
        }, "NeonHostAccept").start()
    }

    @SuppressLint("MissingPermission")
    fun join(adapter: BluetoothAdapter, device: BluetoothDevice) {
        stop()
        val id = generation.get()
        Thread({
            try {
                adapter.cancelDiscovery()
                val candidate = device.createRfcommSocketToServiceRecord(SERVICE_ID)
                if (id != generation.get()) { candidate.close(); return@Thread }
                socket = candidate // so pressing Back closes a pending connection
                candidate.connect()
                if (id == generation.get()) attach(candidate, id) else candidate.close()
            } catch (e: Exception) { ended(id, e.message ?: "Unable to connect") }
        }, "NeonClientConnect").start()
    }

    @SuppressLint("MissingPermission")
    private fun attach(s: BluetoothSocket, id: Int) {
        if (generation.get() != id) { s.close(); return }
        socket = s
        val queue = LinkedBlockingDeque<String>(12)
        outgoing = queue
        val reader = BufferedReader(InputStreamReader(s.inputStream, Charsets.UTF_8))
        val writer = BufferedWriter(OutputStreamWriter(s.outputStream, Charsets.UTF_8))
        val peer = try { s.remoteDevice.name ?: "Second player" } catch (_: Exception) { "Second player" }
        ui.post { if (generation.get() == id) onConnected(peer) }

        Thread({
            try {
                while (generation.get() == id) {
                    val text = queue.take()
                    if (text == "__NEON_QUIT__") break
                    writer.write(text)
                    writer.newLine()
                    writer.flush()
                }
            } catch (_: InterruptedException) { /* shutdown */ }
            catch (e: Exception) { ended(id, e.message ?: "Send failed") }
        }, "NeonBluetoothWriter").apply { isDaemon = true; start() }

        Thread({
            try {
                while (generation.get() == id) {
                    val line = reader.readLine() ?: break
                    if (line.length <= 12000) ui.post { if (generation.get() == id) onLine(line) }
                }
                ended(id, "Bluetooth connection closed")
            } catch (e: Exception) { ended(id, e.message ?: "Connection lost") }
        }, "NeonBluetoothReader").apply { isDaemon = true; start() }
    }

    fun send(text: String) {
        if (!isConnected()) return
        val q = outgoing
        if (!q.offerLast(text)) {
            q.pollFirst() // real-time game: drop old snapshots instead of building lag
            q.offerLast(text)
        }
    }

    private fun ended(id: Int, reason: String) {
        if (!generation.compareAndSet(id, id + 1)) return
        closeSockets()
        ui.post { onEnd(reason) }
    }

    fun stop() {
        generation.incrementAndGet()
        closeSockets()
    }

    private fun closeSockets() {
        try { server?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        server = null
        socket = null
        outgoing.clear()
        outgoing.offerLast("__NEON_QUIT__")
    }
}
