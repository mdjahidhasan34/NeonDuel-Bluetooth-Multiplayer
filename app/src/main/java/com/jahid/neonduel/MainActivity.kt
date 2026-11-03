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
import android.widget.ImageView
import android.widget.Button
import android.widget.LinearLayout
import android.widget.EditText
import android.widget.Toast
import android.net.wifi.WifiManager
import java.net.NetworkInterface
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject

/** Offline bot, legacy Bluetooth and local hotspot matches without internet. */
class MainActivity : Activity() {
    companion object {
        private const val REQUEST_PERMISSIONS = 1001
        private const val REQUEST_ENABLE = 1002
        private const val REQUEST_DISCOVERABLE = 1003
        private const val HOST = 1
        private const val GUEST = 2
        private const val IDLE = 0
        private const val BOT = 3
        private const val WIFI_HOST = 4
        private const val WIFI_GUEST = 5
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
    private var lastStatus = "Play offline or host a local Wi-Fi battle for 2–10 players."
    private var game: GameEngine? = null
    private var botOpponent: BotOpponent? = null
    private var botLevel = BotDifficulty.NORMAL
    private var arena: ArenaView? = null
    private var remoteInput = Command()
    private var scanning = false
    private var discoveryReceiverRegistered = false
    private val foundDevices = linkedMapOf<String, BluetoothDevice>()
    private var lastTick = 0L
    private var frames = 0
    private var wifiHost: HotspotHost? = null
    private var wifiGuest: HotspotGuest? = null
    private var wifiRule = RoomMode.FREE_FOR_ALL
    private var wifiLight = LightMode.DAY
    private var wifiMembers: List<Int> = listOf(0)
    private var wifiPlayerId = 0
    private var wifiGame: OnlineGame? = null
    private var wifiArena: HotspotArenaView? = null
    private val wifiInputs = mutableMapOf<Int, Command>()
    private var wifiFrames = 0
    private var wifiLastTick = 0L
    private val wifiRunner = object : Runnable {
        override fun run() {
            if (mode != WIFI_HOST && mode != WIFI_GUEST) return
            val g = wifiGame ?: return
            val v = wifiArena ?: return
            val now = SystemClock.uptimeMillis()
            val dt = ((now - wifiLastTick) / 1000f).coerceIn(0f, .05f)
            wifiLastTick = now
            if (mode == WIFI_HOST) {
                wifiInputs[0] = v.takeCommand()
                g.update(dt, wifiInputs)
                wifiInputs.values.forEach { it.dash = false }
                if (++wifiFrames % 3 == 0 || !g.running) wifiHost?.broadcast(OnlinePackets.state(g))
            } else {
                val next = v.takeCommand()
                if (++wifiFrames % 2 == 0 || next.dash) wifiGuest?.send(OnlinePackets.input(next))
            }
            v.invalidate()
            loop.postDelayed(this, 33L)
        }
    }

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
            if (mode == IDLE || (mode != BOT && !link.isConnected())) return
            val g = game ?: return
            val view = arena ?: return
            val now = SystemClock.uptimeMillis()
            val dt = ((now - lastTick) / 1000f).coerceIn(0f, .05f)
            lastTick = now
            when (mode) {
                HOST -> {
                    g.update(dt, view.takeCommand(), remoteInput)
                    if (++frames % 2 == 0) link.send(GameWire.state(g))
                }
                GUEST -> link.send(GameWire.input(view.takeCommand()))
                BOT -> g.update(dt, view.takeCommand(), botOpponent?.choose(g, dt) ?: Command())
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
            onConnected = { peer -> if (mode == HOST || mode == GUEST) launchArena(peer) },
            onLine = { line -> receive(line) },
            onEnd = { reason ->
                if (mode == HOST || mode == GUEST) {
                    mode = IDLE
                    loop.removeCallbacks(runner)
                    game = null
                    arena = null
                    botOpponent = null
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
        left.addView(label("FRIENDS", 37f, CYAN, true))
        left.addView(label("ZONE", 42f, WHITE, true))
        val cover = ImageView(this).apply {
            setImageResource(com.jahid.neonduel.R.drawable.friends_zone_banner)
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = "Friends Zone game poster"
        }
        left.addView(cover, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(175)).apply {
            topMargin = dp(7)
            rightMargin = dp(24)
            bottomMargin = dp(4)
        })
        left.addView(label("OFFLINE BOT + HOTSPOT 10 PLAYER BATTLE", 13f, PINK, true))
        val summary = label("Move • Auto-aim • Shoot • Dash\nFFA: 2–10 players. DUO TEAMS: max 2 per team.\nHost also plays. Use one phone hotspot.\nNo mobile data / internet required.", 14f, MUTED)
        summary.setPadding(0, dp(18), dp(14), 0)
        left.addView(summary)

        val right = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = bg(Color.rgb(14, 29, 51), Color.rgb(30, 72, 95))
            setPadding(dp(20), dp(18), dp(20), dp(16))
        }
        wrap.addView(right, LinearLayout.LayoutParams(dp(343), ViewGroup.LayoutParams.WRAP_CONTENT))
        right.addView(label("READY TO BATTLE?", 18f, WHITE, true))
        right.addView(label("Play solo or host a Wi-Fi room.", 12f, MUTED).also {
            it.setPadding(0, dp(3), 0, dp(10))
        })
        val botButton = button("PLAY VS BOT  (OFFLINE)", NAVY, CYAN) { selectBotDifficulty() }
        val wifiFfa = button("HOST WI-FI — FREE FOR ALL (2–10)", NAVY, Color.rgb(131, 216, 172)) {
            beginWifiHost(RoomMode.FREE_FOR_ALL)
        }
        val wifiDuos = button("HOST WI-FI — DUO TEAMS (2/TEAM)", WHITE, Color.rgb(40, 95, 110)) {
            beginWifiHost(RoomMode.DUO_TEAMS)
        }
        val wifiJoin = button("JOIN WI-FI / HOTSPOT MATCH", WHITE, Color.rgb(60, 67, 126)) {
            beginWifiJoin()
        }
        val host = button("BLUETOOTH HOST (LEGACY)", WHITE, Color.rgb(29, 82, 96)) { beginHost() }
        val join = button("BLUETOOTH JOIN (LEGACY)", WHITE, Color.rgb(42, 42, 83)) { beginJoin() }
        val scan = button("SCAN NEARBY PHONES", WHITE, Color.rgb(27, 47, 69)) { beginScan() }
        val guide = button("HOW TO PLAY", PINK, Color.rgb(35, 30, 52)) { help() }
        val actions = if (mode != IDLE) listOf(
            button("CANCEL CONNECTION / HOST", WHITE, Color.rgb(74, 34, 57)) { leave() }, guide
        ) else listOf(botButton, wifiFfa, wifiDuos, wifiJoin, host, join, scan, guide)
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
            .setTitle("FRIENDS ZONE — HOW TO PLAY")
            .setMessage(
                "WI-FI HOTSPOT: One phone turns on hotspot, then taps HOST WI-FI and chooses Free For All " +
                "or Duo Teams. Other phones connect to the hotspot in Android Settings, then tap JOIN WI-FI. " +
                "Enter the host phone's hotspot IP if auto-detection does not work. The host also plays. " +
                "FFA supports 2 to 10 players. Duo Teams has at most 2 members per team (4, 6, 8, or 10 total). " +
                "Host presses START MATCH once everyone joins. No internet is needed.\n\n" +
                "SOLO: Tap PLAY VS BOT and choose a difficulty. No Bluetooth or internet is needed.\n\n" +
                "BLUETOOTH: Install the same game on TWO Android phones. Turn on Bluetooth on both. " +
                "Player 1 taps HOST. Player 2 taps JOIN and chooses the host. " +
                "You can also pair the phones in Android Bluetooth Settings first.\n\n" +
                "CONTROLS: Drag the LEFT joystick to move. Hold FIRE to shoot at your opponent automatically. " +
                "Tap DASH to dodge (short cooldown).\n\n" +
                "PICKUPS: Green heals; gold adds a shield. " +
                "The last 10 seconds of a round have DOUBLE damage.\n\n" +
                "WINNING: Reduce the opponent's HP to 0, or have more HP when time expires. " +
                "First to win THREE rounds wins the match.\n\n" +
                "Offline bot mode works on one phone. For Bluetooth, both phones must keep the app open."
            ).setPositiveButton("LET'S PLAY", null).show()
    }

    private fun selectBotDifficulty() {
        val levels = BotDifficulty.values()
        AlertDialog.Builder(this)
            .setTitle("PLAY VS BOT — DIFFICULTY")
            .setItems(levels.map { it.title }.toTypedArray()) { _, selected ->
                beginBot(levels[selected])
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun beginBot(level: BotDifficulty) {
        clearDiscovery()
        link.stop()
        mode = BOT
        botLevel = level
        botOpponent = BotOpponent(level)
        launchArena("${level.title} bot")
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
        val v = ArenaView(this, instance, if (mode == GUEST) 1 else 0,
            onLeave = { leave() }, onRematch = { rematch() }, vsBot = mode == BOT)
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

    // The host owns all simulation and accepts up to nine remote phones.
    private fun beginWifiHost(rule: RoomMode) {
        clearDiscovery()
        link.stop()
        mode = WIFI_HOST
        wifiRule = rule
        wifiLight = LightMode.DAY
        wifiPlayerId = 0
        wifiMembers = listOf(0)
        wifiInputs.clear()
        wifiGame = null
        wifiArena = null
        wifiHost?.stop()
        wifiGuest?.stop()
        wifiGuest = null
        wifiHost = HotspotHost(
            onJoined = { id -> loop.post {
                if (mode == WIFI_HOST && wifiGame == null) {
                    wifiMembers = wifiHost?.ids() ?: listOf(0)
                    wifiHost?.broadcast(OnlinePackets.lobby(wifiRule, wifiMembers, wifiLight))
                    showWifiLobby("Player ${id + 1} joined")
                }
            } },
            onLine = { id, line -> loop.post {
                if (mode == WIFI_HOST) receiveWifiCommand(id, line)
            } },
            onLeft = { id -> loop.post {
                if (mode == WIFI_HOST) {
                    wifiInputs.remove(id)
                    wifiGame?.disconnect(id)
                    if (wifiGame == null) {
                        wifiMembers = wifiHost?.ids() ?: listOf(0)
                        wifiHost?.broadcast(OnlinePackets.lobby(wifiRule, wifiMembers, wifiLight))
                        showWifiLobby("Player ${id + 1} disconnected")
                    }
                }
            } },
            onFailure = { error -> loop.post {
                if (mode == WIFI_HOST) { leave(); showMenu("Hotspot server failed: $error") }
            } }
        )
        wifiHost?.start()
        showWifiLobby("Enable your phone's hotspot first. Guests join the hotspot network, then use JOIN WI-FI.")
    }

    @Suppress("DEPRECATION")
    private fun hotspotGateway(): String {
        return try {
            val manager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val address = manager.dhcpInfo?.gateway ?: 0
            if (address == 0) "192.168.43.1" else (0..3).joinToString(".") { i ->
                ((address shr (8 * i)) and 255).toString()
            }
        } catch (_: Exception) { "192.168.43.1" }
    }

    private fun hostAddressHints(): String {
        return try {
            val addresses = mutableListOf<String>()
            val networks = NetworkInterface.getNetworkInterfaces()
            while (networks != null && networks.hasMoreElements()) {
                val iface = networks.nextElement()
                if (!iface.isUp || iface.isLoopback) continue
                val values = iface.inetAddresses
                while (values.hasMoreElements()) {
                    val addr = values.nextElement()
                    if (addr is java.net.Inet4Address && addr.isSiteLocalAddress) {
                        addresses += addr.hostAddress ?: continue
                    }
                }
            }
            addresses.distinct().joinToString(", ").ifEmpty { "Check hotspot IP in phone network details" }
        } catch (_: Exception) { "Check hotspot IP in phone network details" }
    }

    private fun beginWifiJoin() {
        val field = EditText(this).apply {
            setSingleLine(true)
            setText(hotspotGateway())
            hint = "Host IP (e.g. 192.168.43.1)"
            setSelectAllOnFocus(true)
        }
        AlertDialog.Builder(this)
            .setTitle("JOIN HOTSPOT GAME")
            .setMessage("Connect your phone to the host's Wi-Fi hotspot in Android Settings first. The host must create a room. Enter the hotspot gateway IP below.")
            .setView(field)
            .setPositiveButton("CONNECT") { _, _ ->
                val ip = field.text.toString().trim()
                if (!ip.matches(Regex("[0-9.]{7,15}"))) {
                    Toast.makeText(this, "Enter a valid IPv4 address", Toast.LENGTH_LONG).show()
                } else connectWifiGuest(ip)
            }
            .setNegativeButton("CANCEL", null).show()
    }

    private fun connectWifiGuest(ip: String) {
        clearDiscovery(); link.stop()
        wifiHost?.stop(); wifiHost = null
        wifiGuest?.stop()
        wifiPlayerId = -1
        wifiMembers = emptyList()
        wifiGame = null
        wifiArena = null
        mode = WIFI_GUEST
        wifiGuest = HotspotGuest(
            onLine = { line -> loop.post { if (mode == WIFI_GUEST) receiveWifiPacket(line) } },
            onClosed = { error -> loop.post {
                if (mode == WIFI_GUEST) { leave(); showMenu("Hotspot disconnected: $error. Check host IP and hotspot connection.") }
            } }
        )
        showWifiLobby("Connecting to $ip ...")
        wifiGuest?.connect(ip)
    }

    private fun showWifiLobby(status: String) {
        if (mode != WIFI_HOST && mode != WIFI_GUEST) return
        val isHost = mode == WIFI_HOST
        val scroll = ScrollView(this).apply { setBackgroundColor(NAVY) }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(25), dp(16), dp(25), dp(18))
        }
        scroll.addView(content)
        content.addView(label("FRIENDS ZONE — WI-FI ROOM", 28f, CYAN, true))
        content.addView(label(if (wifiRule == RoomMode.FREE_FOR_ALL) "FREE FOR ALL • 2–10 PLAYERS"
            else "DUO TEAMS • 2 PLAYERS PER TEAM", 17f, PINK, true))
        content.addView(label(if (isHost) "Host hotspot IP: ${hostAddressHints()}"
            else "You are Player ${if (wifiPlayerId < 0) "?" else wifiPlayerId + 1}", 14f, WHITE))
        content.addView(label(status, 13f, MUTED))
        val playersLine = if (wifiMembers.isEmpty()) "Connecting..." else wifiMembers.mapIndexed { i, id ->
            "P${id + 1}" + if (wifiRule == RoomMode.DUO_TEAMS) " (Team ${i / 2 + 1})" else ""
        }.joinToString("    ")
        content.addView(label("PLAYERS (${wifiMembers.size}/10):  $playersLine", 15f, WHITE))
        content.addView(label("For hotspot: host and guests must share the same hotspot Wi-Fi. Host is also a player.", 12f, MUTED))
        if (isHost) {
            val count = wifiMembers.size
            val allowed = count >= 2 && (wifiRule == RoomMode.FREE_FOR_ALL || (count >= 4 && count % 2 == 0))
            content.addView(button("START MATCH", if (allowed) NAVY else MUTED,
                if (allowed) CYAN else Color.rgb(48, 59, 76)) {
                if (allowed) startWifiMatch()
                else Toast.makeText(this, "FFA needs 2–10. Duo Teams needs an even number, 4–10.", Toast.LENGTH_LONG).show()
            })
            content.addView(button("SCENERY: ${wifiLight.name} (TAP TO CHANGE)", WHITE, Color.rgb(53, 72, 61)) {
                val options = LightMode.values()
                wifiLight = options[(options.indexOf(wifiLight) + 1) % options.size]
                wifiHost?.broadcast(OnlinePackets.lobby(wifiRule, wifiMembers, wifiLight))
                showWifiLobby("Scenery changed to ${wifiLight.name}")
            })
            content.addView(button("OPEN WI-FI / HOTSPOT SETTINGS", WHITE, Color.rgb(44, 64, 88)) {
                startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
            })
        }
        content.addView(button("LEAVE ROOM", WHITE, Color.rgb(90, 35, 62)) { leave() })
        setContentView(scroll)
    }

    private fun startWifiMatch() {
        if (mode != WIFI_HOST) return
        val ids = wifiHost?.ids() ?: return
        if (ids.size !in 2..10 || (wifiRule == RoomMode.DUO_TEAMS && (ids.size < 4 || ids.size % 2 != 0))) {
            showWifiLobby("Waiting for more players. Team mode requires 4, 6, 8 or 10 players.")
            return
        }
        wifiMembers = ids
        wifiHost?.lock()
        wifiGame = OnlineGame(wifiRule, ids, wifiLight)
        wifiInputs.clear()
        wifiHost?.broadcast(OnlinePackets.start(wifiRule, ids, wifiLight))
        launchWifiScreen()
        wifiHost?.broadcast(OnlinePackets.state(wifiGame!!))
    }

    private fun launchWifiScreen() {
        val g = wifiGame ?: return
        wifiArena = HotspotArenaView(this, { wifiGame?.state() }, { wifiGame?.rocks ?: emptyList() },
            { wifiPlayerId }, { leave() }, { wifiRematch() })
        setContentView(wifiArena)
        wifiLastTick = SystemClock.uptimeMillis()
        wifiFrames = 0
        loop.removeCallbacks(wifiRunner)
        loop.post(wifiRunner)
    }

    private fun receiveWifiCommand(id: Int, line: String) {
        try {
            val j = JSONObject(line)
            if (j.optString("t") == "cmd" && wifiGame != null) {
                val c = OnlinePackets.decodeInput(j)
                c.dash = c.dash || (wifiInputs[id]?.dash == true)
                wifiInputs[id] = c
            }
        } catch (_: Exception) { }
    }

    private fun receiveWifiPacket(line: String) {
        try {
            val j = JSONObject(line)
            when (j.optString("t")) {
                "welcome" -> { wifiPlayerId = j.getInt("id"); showWifiLobby("Connected to host. Waiting for others...") }
                "reject" -> { val reason = j.optString("message", "Room unavailable"); leave(); showMenu(reason) }
                "lobby" -> {
                    wifiRule = RoomMode.valueOf(j.getString("mode"))
                    wifiLight = LightMode.valueOf(j.optString("light", "DAY"))
                    wifiMembers = OnlinePackets.memberIds(j)
                    if (wifiGame == null) showWifiLobby("Waiting for host to start match...")
                }
                "start" -> {
                    wifiRule = RoomMode.valueOf(j.getString("mode"))
                    wifiLight = LightMode.valueOf(j.optString("light", "DAY"))
                    wifiMembers = OnlinePackets.memberIds(j)
                    wifiGame = OnlineGame(wifiRule, wifiMembers, wifiLight)
                    launchWifiScreen()
                }
                "world" -> {
                    val state = OnlinePackets.decodeState(j) ?: return
                    wifiGame?.load(state)
                    wifiArena?.invalidate()
                }
            }
        } catch (_: Exception) { }
    }

    private fun wifiRematch() {
        if (mode != WIFI_HOST) {
            Toast.makeText(this, "Only the host can start a rematch", Toast.LENGTH_SHORT).show()
            return
        }
        if (wifiGame?.running != false) return
        // Use currently connected peers so a disconnected phone never blocks rematches.
        wifiHost?.unlock()
        wifiGame = null
        startWifiMatch()
    }

    private fun rematch() {
        val g = game ?: return
        if (g.phase != "MATCH_OVER") return
        if (mode == HOST || mode == BOT) {
            g.resetMatch()
            if (mode == BOT) botOpponent = BotOpponent(botLevel)
        } else if (mode == GUEST) link.send("{\"t\":\"again\"}")
    }

    private fun leave() {
        mode = IDLE
        loop.removeCallbacks(runner)
        loop.removeCallbacks(wifiRunner)
        wifiHost?.stop(); wifiHost = null
        wifiGuest?.stop(); wifiGuest = null
        wifiGame = null; wifiArena = null
        wifiInputs.clear()
        clearDiscovery()
        link.stop()
        game = null
        arena = null
        botOpponent = null
        showMenu("Match closed. Host a Wi-Fi battle, or play vs bot.")
    }

    @Deprecated("Android back")
    override fun onBackPressed() {
        if (mode != IDLE) leave()
        else super.onBackPressed()
    }

    override fun onDestroy() {
        mode = IDLE
        loop.removeCallbacks(runner)
        loop.removeCallbacks(wifiRunner)
        wifiHost?.stop()
        wifiGuest?.stop()
        clearDiscovery()
        link.stop()
        super.onDestroy()
    }
}
