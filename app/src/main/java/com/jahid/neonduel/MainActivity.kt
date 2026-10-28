package com.jahid.neonduel

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject

/** Two phones, Bluetooth Classic only. No server, account, location tracking, or internet. */
class MainActivity : Activity() {
    companion object {
        private const val REQUEST_PERMISSIONS = 1001
        private const val REQUEST_ENABLE = 1002
        private const val REQUEST_DISCOVERABLE = 1003
        private const val HOST = 1
        private const val GUEST = 2
        private const val IDLE = 0
        private val CYAN = Color.rgb(56, 233, 255)
        private val PINK = Color.rgb(255, 80, 164)
        private val WHITE = Color.rgb(233, 243, 255)
        private val MUTED = Color.rgb(139, 166, 195)
        private val NAVY = Color.rgb(7, 18, 35)
    }

    private val adapter: BluetoothAdapter? by lazy { BluetoothAdapter.getDefaultAdapter() }
    private val loop = Handler(Looper.getMainLooper())
    private lateinit var link: BluetoothLink
    private var mode = IDLE
    private var pendingAction: (() -> Unit)? = null
    private var lastStatus = "Connect two Android phones to play offline."
    private var game: GameEngine? = null
    private var arena: ArenaView? = null
    private var remoteInput = Command()
    private var scanning = false
    private var discoveryReceiverRegistered = false
    private val foundDevices = linkedMapOf<String, BluetoothDevice>()
    private var lastTick = 0L
    private var frames = 0

    private val receiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        @Suppress("DEPRECATION")
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = if (Build.VERSION.SDK_INT >= 33)
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    else intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    if (device != null) foundDevices[device.address] = device
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    scanning = false
                    unregisterDiscovery()
                    showDevices()
                }
            }
        }
    }

    private val runner = object : Runnable {
        override fun run() {
            if (mode == IDLE || !link.isConnected()) return
            val g = game ?: return
            val view = arena ?: return
            val now = SystemClock.uptimeMillis()
            val dt = ((now - lastTick) / 1000f).coerceIn(0f, .05f)
            lastTick = now
            if (mode == HOST) {
                g.update(dt, view.takeCommand(), remoteInput)
                if (++frames % 2 == 0) link.send(GameWire.state(g))
            } else {
                link.send(GameWire.input(view.takeCommand()))
            }
            view.invalidate()
            loop.postDelayed(this, 33L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
        link = BluetoothLink(
            onConnected = { peer -> launchArena(peer) },
            onLine = { line -> receive(line) },
            onEnd = { reason ->
                if (mode != IDLE) {
                    mode = IDLE
                    loop.removeCallbacks(runner)
                    game = null
                    arena = null
                    showMenu("Disconnected: $reason")
                }
            }
        )
        showMenu(lastStatus)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + .5f).toInt()

    private fun bg(fill: Int, edge: Int, radius: Float = 18f): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(fill)
        cornerRadius = dp(radius.toInt()).toFloat()
        setStroke(dp(1), edge)
    }

    private fun label(content: String, size: Float, tint: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            this.text = content
            textSize = size
            setTextColor(tint)
            typeface = android.graphics.Typeface.create(
                if (bold) "sans-serif-black" else "sans-serif-medium",
                if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL
            )
        }

    private fun button(title: String, color: Int, fill: Int, click: () -> Unit): Button = Button(this).apply {
        text = title
        isAllCaps = false
        textSize = 15f
        setTextColor(color)
        background = bg(fill, color)
        setOnClickListener { click() }
        minHeight = dp(53)
        minimumHeight = dp(53)
    }

    private fun showMenu(status: String) {
        lastStatus = status
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(NAVY)
        }
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(18), dp(28), dp(18))
        }
        scroll.addView(wrap, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT))

        val left = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        wrap.addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        left.addView(label("⚡  NEON", 39f, CYAN, true))
        left.addView(label("DUEL", 43f, WHITE, true))
        left.addView(label("BLUETOOTH BATTLE ARENA", 13f, PINK, true))
        val summary = label("Move • Auto-aim • Shoot • Dash\nGrab power-ups. Win 3 rounds.\nNo Wi-Fi. No mobile data. Just two phones.", 14f, MUTED)
        summary.setPadding(0, dp(18), dp(14), 0)
        left.addView(summary)

        val right = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = bg(Color.rgb(14, 29, 51), Color.rgb(30, 72, 95))
            setPadding(dp(20), dp(18), dp(20), dp(16))
        }
        wrap.addView(right, LinearLayout.LayoutParams(dp(343), ViewGroup.LayoutParams.WRAP_CONTENT))
        right.addView(label("READY TO BATTLE?", 18f, WHITE, true))
        right.addView(label("One player hosts. The other joins.", 12f, MUTED).also {
            it.setPadding(0, dp(3), 0, dp(10))
        })
        val host = button("HOST A MATCH  (PLAYER 1)", NAVY, CYAN) { beginHost() }
        val join = button("JOIN A MATCH  (PLAYER 2)", WHITE, Color.rgb(42, 42, 83)) { beginJoin() }
        val scan = button("SCAN NEARBY PHONES", WHITE, Color.rgb(27, 47, 69)) { beginScan() }
        val guide = button("HOW TO PLAY", PINK, Color.rgb(35, 30, 52)) { help() }
        val actions = if (mode != IDLE) listOf(
            button("CANCEL CONNECTION / HOST", WHITE, Color.rgb(74, 34, 57)) { leave() }, guide
        ) else listOf(host, join, scan, guide)
        for (b in actions) {
            right.addView(b, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(51)).apply {
                bottomMargin = dp(8)
            })
        }
        val statusText = label(status, 12f, MUTED)
        statusText.setPadding(0, dp(7), 0, 0)
        right.addView(statusText)
        setContentView(scroll)
    }

    private fun help() {
        AlertDialog.Builder(this)
            .setTitle("NEON DUEL — HOW TO PLAY")
            .setMessage(
                "1. Install the same game on TWO Android phones.\n\n" +
                "2. Turn on Bluetooth on both phones. Player 1 taps HOST. Player 2 taps JOIN and chooses the host. " +
                "You can also pair the phones in Android Bluetooth Settings first.\n\n" +
                "3. Drag the LEFT joystick to move. Hold FIRE to shoot at your opponent automatically. " +
                "Tap DASH to dodge (short cooldown).\n\n" +
                "4. Green pickup heals; gold pickup adds a shield. " +
                "The last 10 seconds of a round have DOUBLE damage.\n\n" +
                "5. Reduce the opponent's HP to 0, or have more HP when time expires. " +
                "First to win THREE rounds wins the match.\n\n" +
                "Play offline, nearby, without internet. Both phones must keep the app open."
            ).setPositiveButton("LET'S PLAY", null).show()
    }

    @SuppressLint("MissingPermission")
    private fun withBluetooth(needScan: Boolean = false, needAdvertise: Boolean = false, action: () -> Unit) {
        if (adapter == null) {
            showMenu("This phone has no Bluetooth Classic adapter.")
            return
        }
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 31) {
            perms += Manifest.permission.BLUETOOTH_CONNECT
            if (needScan) perms += Manifest.permission.BLUETOOTH_SCAN
            if (needAdvertise) perms += Manifest.permission.BLUETOOTH_ADVERTISE
        } else if (needScan) {
            perms += Manifest.permission.ACCESS_FINE_LOCATION
        }
        val missing = perms.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) {
            pendingAction = { withBluetooth(needScan, needAdvertise, action) }
            requestPermissions(missing.toTypedArray(), REQUEST_PERMISSIONS)
            return
        }
        if (adapter?.isEnabled != true) {
            pendingAction = { withBluetooth(needScan, needAdvertise, action) }
            try {
                startActivityForResult(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_ENABLE)
            } catch (e: Exception) {
                showMenu("Please enable Bluetooth in phone settings.")
            }
            return
        }
        action()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_PERMISSIONS) return
        val callback = pendingAction
        pendingAction = null
        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            callback?.invoke()
        } else showMenu("Nearby devices permission is required to play using Bluetooth.")
    }

    @Deprecated("Legacy system enable/discoverable activities")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ENABLE) {
            val callback = pendingAction
            pendingAction = null
            if (resultCode == RESULT_OK) callback?.invoke()
            else showMenu("Enable Bluetooth to host or join a match.")
        }
        // Host remains active even if discoverability is declined: paired devices can still join.
    }

    @SuppressLint("MissingPermission")
    private fun beginHost() = withBluetooth(needAdvertise = true) {
        clearDiscovery()
        mode = HOST
        showMenu("Waiting for Player 2... Keep this screen open. Accept discoverability if asked.")
        link.host(adapter!!)
        try {
            startActivityForResult(Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
            }, REQUEST_DISCOVERABLE)
        } catch (_: Exception) {
            showMenu("Host is listening. Pair both phones in Bluetooth settings, then join.")
        }
    }

    private fun beginJoin() = withBluetooth {
        clearDiscovery()
        showDevices()
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun beginScan() = withBluetooth(needScan = true) {
        val bt = adapter ?: return@withBluetooth
        clearDiscovery()
        foundDevices.clear()
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        else registerReceiver(receiver, filter)
        discoveryReceiverRegistered = true
        scanning = bt.startDiscovery()
        if (scanning) showMenu("Scanning for nearby Bluetooth phones... (about 12 seconds)")
        else {
            unregisterDiscovery()
            showMenu("Couldn't start Bluetooth discovery. Try pairing in Android settings.")
        }
    }

    @SuppressLint("MissingPermission")
    private fun showDevices() {
        if (mode == HOST) return // keep server listening; don't mix actions
        val entries = linkedMapOf<String, BluetoothDevice>()
        try { adapter?.bondedDevices?.forEach { entries[it.address] = it } }
        catch (_: SecurityException) { showMenu("Bluetooth permission missing."); return }
        foundDevices.forEach { (key, value) -> entries[key] = value }
        if (entries.isEmpty()) {
            AlertDialog.Builder(this).setTitle("No paired phones yet")
                .setMessage("Tap SCAN NEARBY PHONES, or pair the two phones in Android Bluetooth settings. " +
                    "The host must tap HOST A MATCH first.")
                .setPositiveButton("SCAN") { _, _ -> beginScan() }
                .setNeutralButton("BLUETOOTH SETTINGS") { _, _ ->
                    startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                }.setNegativeButton("CANCEL", null).show()
            return
        }
        val list = entries.values.toList()
        val lines = list.map { d ->
            val label = try { d.name ?: "Unnamed device" } catch (_: SecurityException) { "Bluetooth device" }
            "$label\n${d.address}"
        }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Choose Player 1's phone")
            .setItems(lines) { _, position -> connect(list[position]) }
            .setPositiveButton("SCAN AGAIN") { _, _ -> beginScan() }
            .setNegativeButton("CANCEL", null).show()
    }

    @SuppressLint("MissingPermission")
    private fun connect(device: BluetoothDevice) {
        clearDiscovery()
        mode = GUEST
        val label = try { device.name ?: device.address } catch (_: Exception) { "host" }
        showMenu("Connecting to $label... Accept the Bluetooth pairing request if shown.")
        link.join(adapter!!, device)
    }

    private fun unregisterDiscovery() {
        if (discoveryReceiverRegistered) {
            try { unregisterReceiver(receiver) } catch (_: Exception) {}
            discoveryReceiverRegistered = false
        }
    }

    @SuppressLint("MissingPermission")
    private fun clearDiscovery() {
        if (scanning) try { adapter?.cancelDiscovery() } catch (_: Exception) {}
        scanning = false
        unregisterDiscovery()
    }

    private fun launchArena(peer: String) {
        if (mode == IDLE) return
        val instance = GameEngine()
        game = instance
        remoteInput = Command()
        val v = ArenaView(this, instance, if (mode == HOST) 0 else 1,
            onLeave = { leave() }, onRematch = { rematch() })
        arena = v
        setContentView(v)
        lastTick = SystemClock.uptimeMillis()
        frames = 0
        loop.removeCallbacks(runner)
        loop.post(runner)
        if (mode == HOST) link.send(GameWire.state(instance))
    }

    private fun receive(line: String) {
        try {
            val packet = JSONObject(line)
            val g = game ?: return
            when (packet.optString("t")) {
                "input" -> if (mode == HOST) {
                    val next = GameWire.decodeInput(packet)
                    next.dash = next.dash || remoteInput.dash
                    remoteInput = next
                }
                "state" -> if (mode == GUEST) {
                    GameWire.decodeState(packet)?.let { g.loadRemote(it) }
                    arena?.invalidate()
                }
                "again" -> if (mode == HOST && g.phase == "MATCH_OVER") g.resetMatch()
            }
        } catch (_: Exception) { /* malformed peer message is ignored */ }
    }

    private fun rematch() {
        val g = game ?: return
        if (g.phase != "MATCH_OVER") return
        if (mode == HOST) g.resetMatch()
        else if (mode == GUEST) link.send("{\"t\":\"again\"}")
    }

    private fun leave() {
        mode = IDLE
        loop.removeCallbacks(runner)
        clearDiscovery()
        link.stop()
        game = null
        arena = null
        showMenu("Match closed. Host or join to play again.")
    }

    @Deprecated("Android back")
    override fun onBackPressed() {
        if (mode != IDLE) leave()
        else super.onBackPressed()
    }

    override fun onDestroy() {
        mode = IDLE
        loop.removeCallbacks(runner)
        clearDiscovery()
        link.stop()
        super.onDestroy()
    }
}
