package com.jahid.neonduel

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

/** Host-authoritative arena for two to ten people on the same local Wi-Fi. */
enum class RoomMode { FREE_FOR_ALL, DUO_TEAMS }
enum class LightMode { DAY, SUNSET, NIGHT }

data class OnlinePlayer(
    val id: Int,
    val team: Int,
    var x: Float,
    var y: Float,
    var hp: Int = 5,
    var shield: Int = 0,
    var kills: Int = 0,
    var aimX: Float = 1f,
    var aimY: Float = 0f,
    var fireCooldown: Float = 0f,
    var dashCooldown: Float = 0f,
    var respawnIn: Float = 0f,
    var connected: Boolean = true
)

data class OnlineBullet(var x: Float, var y: Float, val vx: Float, val vy: Float, val owner: Int)

data class OnlineState(
    val mode: RoomMode,
    val width: Float,
    val height: Float,
    val time: Float,
    val running: Boolean,
    val players: List<OnlinePlayer>,
    val bullets: List<OnlineBullet>,
    val light: LightMode = LightMode.DAY,
    val winningTeam: Int = -1,
    val winningPlayer: Int = -1
)

class OnlineGame(val roomMode: RoomMode, memberIds: List<Int>, var light: LightMode = LightMode.DAY) {
    init { require(memberIds.size in 2..10 && memberIds.distinct().size == memberIds.size) }
    val width = when { memberIds.size <= 2 -> 1120f; memberIds.size <= 4 -> 1660f; memberIds.size <= 6 -> 2020f; else -> 2580f }
    val height = when { memberIds.size <= 2 -> 740f; memberIds.size <= 4 -> 1050f; memberIds.size <= 6 -> 1320f; else -> 1680f }
    var time = 120f
        private set
    var running = true
        private set
    var winningTeam = -1
        private set
    var winningPlayer = -1
        private set
    val players = memberIds.mapIndexed { i, id ->
        val angle = 2.0 * PI * i / memberIds.size
        OnlinePlayer(id, if (roomMode == RoomMode.DUO_TEAMS) i / 2 else -1,
            width / 2f + cos(angle).toFloat() * (width * .34f),
            height / 2f + sin(angle).toFloat() * (height * .32f))
    }.toMutableList()
    val bullets = mutableListOf<OnlineBullet>()

    // Map obstacles are deterministic and need no network transfer.
    val rocks: List<Rock> = (0 until (8 + memberIds.size * 4)).mapNotNull { i ->
        val x = 115f + ((i * 683 + 149) % (width.toInt() - 230))
        val y = 100f + ((i * 409 + 217) % (height.toInt() - 200))
        if (players.any { hypot(it.x - x, it.y - y) < 96f }) null
        else Rock(x, y, if (i % 4 == 0) 47f else 28f)
    }

    private fun enemy(a: OnlinePlayer, b: OnlinePlayer): Boolean =
        a.id != b.id && a.connected && b.connected && b.hp > 0 &&
            (roomMode == RoomMode.FREE_FOR_ALL || a.team != b.team)

    fun disconnect(id: Int) {
        players.firstOrNull { it.id == id }?.apply { connected = false; hp = 0 }
        bullets.removeAll { it.owner == id }
    }

    fun update(dtRaw: Float, commands: Map<Int, Command>) {
        if (!running) return
        val dt = dtRaw.coerceIn(0f, .05f)
        time = max(0f, time - dt)
        for (p in players) {
            if (!p.connected) continue
            p.fireCooldown = max(0f, p.fireCooldown - dt)
            p.dashCooldown = max(0f, p.dashCooldown - dt)
            if (p.hp == 0) {
                p.respawnIn -= dt
                if (p.respawnIn <= 0f) {
                    p.hp = 5
                    p.shield = 1
                    p.x = (120f + ((p.id * 359) % (width.toInt() - 240)))
                    p.y = (110f + ((p.id * 227) % (height.toInt() - 220)))
                }
                continue
            }
            val c = commands[p.id] ?: Command()
            val speedLength = hypot(c.dx, c.dy).coerceAtLeast(1f)
            val ux = c.dx / speedLength
            val uy = c.dy / speedLength
            val dash = c.dash && p.dashCooldown == 0f && (ux != 0f || uy != 0f)
            val step = if (dash) 124f else 215f * dt
            val nx = (p.x + ux * step).coerceIn(35f, width - 35f)
            val ny = (p.y + uy * step).coerceIn(35f, height - 35f)
            if (rocks.none { hypot(it.x - nx, it.y - ny) < it.radius + 20f }) { p.x = nx; p.y = ny }
            if (dash) p.dashCooldown = 2.3f
            if (c.fire && p.fireCooldown == 0f) {
                val target = players.filter { enemy(p, it) }.minByOrNull { hypot(it.x - p.x, it.y - p.y) }
                if (target != null) {
                    val len = hypot(target.x - p.x, target.y - p.y).coerceAtLeast(1f)
                    p.aimX = (target.x - p.x) / len
                    p.aimY = (target.y - p.y) / len
                    bullets += OnlineBullet(p.x + p.aimX * 26f, p.y + p.aimY * 26f,
                        p.aimX * 790f, p.aimY * 790f, p.id)
                    p.fireCooldown = .31f
                }
            }
        }
        val iterator = bullets.iterator()
        while (iterator.hasNext()) {
            val b = iterator.next()
            b.x += b.vx * dt
            b.y += b.vy * dt
            val source = players.firstOrNull { it.id == b.owner }
            if (b.x < 0 || b.x > width || b.y < 0 || b.y > height ||
                rocks.any { hypot(b.x - it.x, b.y - it.y) < it.radius }) {
                iterator.remove(); continue
            }
            val hit = players.firstOrNull { source != null && enemy(source, it) && hypot(it.x - b.x, it.y - b.y) < 23f }
            if (hit != null) {
                if (hit.shield > 0) hit.shield-- else hit.hp--
                if (hit.hp <= 0) {
                    hit.hp = 0
                    hit.respawnIn = 2.5f
                    source?.kills = (source?.kills ?: 0) + 1
                }
                iterator.remove()
            }
        }
        if (time <= 0f) {
            running = false
            if (roomMode == RoomMode.FREE_FOR_ALL) winningPlayer = players.filter { it.connected }.maxByOrNull { it.kills }?.id ?: -1
            else winningTeam = players.filter { it.connected }.groupBy { it.team }
                .maxByOrNull { entry -> entry.value.sumOf { it.kills } }?.key ?: -1
        }
    }

    fun state(): OnlineState = OnlineState(roomMode, width, height, time, running,
        players.map { it.copy() }, bullets.map { it.copy() }, light, winningTeam, winningPlayer)

    fun load(state: OnlineState) {
        light = state.light
        time = state.time
        running = state.running
        winningTeam = state.winningTeam
        winningPlayer = state.winningPlayer
        players.clear(); players.addAll(state.players.map { it.copy() })
        bullets.clear(); bullets.addAll(state.bullets.map { it.copy() })
    }
}
