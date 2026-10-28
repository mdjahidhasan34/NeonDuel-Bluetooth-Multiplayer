package com.jahid.neonduel

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

/** Pure Kotlin, Android-independent game simulation. Only the host advances it. */
object Arena {
    const val W = 960f
    const val H = 540f
    const val LEFT = 76f
    const val RIGHT = 884f
    const val TOP = 108f
    const val BOTTOM = 485f
    const val ROUND_SECONDS = 45f
    val rocks = listOf(Rock(474f, 205f, 33f), Rock(486f, 390f, 31f))
}

data class Rock(val x: Float, val y: Float, val radius: Float)
data class Command(var dx: Float = 0f, var dy: Float = 0f, var fire: Boolean = false, var dash: Boolean = false)
data class Fighter(
    var x: Float, var y: Float, var hp: Int = 5, var shield: Int = 0,
    var score: Int = 0, var aimX: Float = 1f, var aimY: Float = 0f,
    var fireCooldown: Float = 0f, var dashCooldown: Float = 0f,
    var invulnerable: Float = 0f
)
data class Shot(var x: Float, var y: Float, val vx: Float, val vy: Float, val owner: Int, val id: Int)
data class PowerUp(var x: Float, var y: Float, var kind: String)

data class GameSnapshot(
    val round: Int,
    val seconds: Float,
    val phase: String,
    val message: String,
    val winner: Int,
    val players: List<Fighter>,
    val bullets: List<Shot>,
    val powerUp: PowerUp?
)

class GameEngine(private val random: Random = Random.Default) {
    val players = arrayOf(Fighter(182f, 280f), Fighter(778f, 280f, aimX = -1f))
    val bullets = mutableListOf<Shot>()
    var powerUp: PowerUp? = null
        private set
    var round = 1
        private set
    var seconds = Arena.ROUND_SECONDS
        private set
    var phase = "PLAY"
        private set
    var message = "ROUND 1"
        private set
    var winner = 0
        private set
    private var betweenRounds = 0f
    private var powerUpTimer = 7f
    private var nextShotId = 0

    init { resetMatch() }

    fun snapshot(): GameSnapshot = GameSnapshot(
        round, seconds, phase, message, winner,
        players.map { it.copy() }, bullets.map { it.copy() }, powerUp?.copy()
    )

    // Used only by the guest; guest never runs physics locally.
    fun loadRemote(s: GameSnapshot) {
        if (s.players.size != 2) return
        round = s.round
        seconds = s.seconds
        phase = s.phase
        message = s.message
        winner = s.winner
        for (i in 0..1) {
            players[i] = s.players[i].copy()
        }
        bullets.clear()
        bullets.addAll(s.bullets.take(60).map { it.copy() })
        powerUp = s.powerUp?.copy()
    }

    fun resetMatch() {
        round = 1
        winner = 0
        players.forEach { it.score = 0 }
        resetRound()
    }

    private fun resetRound() {
        phase = "PLAY"
        seconds = Arena.ROUND_SECONDS
        betweenRounds = 0f
        bullets.clear()
        powerUp = null
        powerUpTimer = 7f
        players.forEachIndexed { index, p ->
            p.x = if (index == 0) 182f else 778f
            p.y = 280f
            p.hp = 5
            p.shield = 0
            p.aimX = if (index == 0) 1f else -1f
            p.aimY = 0f
            p.fireCooldown = 0f
            p.dashCooldown = 0f
            p.invulnerable = 0f
        }
        message = "ROUND $round — FIGHT!"
    }

    fun update(delta: Float, first: Command, second: Command) {
        val dt = delta.coerceIn(0f, .05f)
        if (phase == "MATCH_OVER") return
        if (phase == "ROUND_OVER") {
            betweenRounds -= dt
            if (betweenRounds <= 0f) {
                round++
                resetRound()
            }
            return
        }
        seconds = max(0f, seconds - dt)
        updateFighter(0, first, dt)
        updateFighter(1, second, dt)
        updateShots(dt)
        updatePowerUp(dt)
        if (players[0].hp <= 0 || players[1].hp <= 0 || seconds <= 0f) endRound()
    }

    private fun updateFighter(id: Int, cmd: Command, dt: Float) {
        val p = players[id]
        p.fireCooldown = max(0f, p.fireCooldown - dt)
        p.dashCooldown = max(0f, p.dashCooldown - dt)
        p.invulnerable = max(0f, p.invulnerable - dt)
        val norm = hypot(cmd.dx.toDouble(), cmd.dy.toDouble()).toFloat()
        val dx = if (norm > 1f) cmd.dx / norm else cmd.dx
        val dy = if (norm > 1f) cmd.dy / norm else cmd.dy
        val burst = if (cmd.dash && p.dashCooldown <= 0f && norm > .18f) 105f else 0f
        if (burst > 0f) {
            p.dashCooldown = 3.3f
            p.invulnerable = max(p.invulnerable, .22f)
        }
        cmd.dash = false
        val dist = 218f * dt + burst
        tryMove(p, dx * dist, dy * dist)

        // Auto-aim keeps the battle playable on small touch screens.
        val enemy = players[1 - id]
        val ex = enemy.x - p.x
        val ey = enemy.y - p.y
        val length = hypot(ex.toDouble(), ey.toDouble()).toFloat()
        if (length > 1f) {
            p.aimX = ex / length
            p.aimY = ey / length
        }
        if (cmd.fire && p.fireCooldown <= 0f) {
            p.fireCooldown = .31f
            val spread = random.nextFloat() * .065f - .0325f
            val vx = p.aimX - p.aimY * spread
            val vy = p.aimY + p.aimX * spread
            bullets.add(Shot(p.x + vx * 25f, p.y + vy * 25f, vx * 490f, vy * 490f, id, ++nextShotId))
            if (bullets.size > 60) bullets.removeAt(0)
        }
    }

    private fun tryMove(p: Fighter, dx: Float, dy: Float) {
        val xx = (p.x + dx).coerceIn(Arena.LEFT, Arena.RIGHT)
        if (!touchesRock(xx, p.y, 20f)) p.x = xx
        val yy = (p.y + dy).coerceIn(Arena.TOP, Arena.BOTTOM)
        if (!touchesRock(p.x, yy, 20f)) p.y = yy
    }

    private fun touchesRock(x: Float, y: Float, radius: Float): Boolean = Arena.rocks.any {
        val dx = x - it.x
        val dy = y - it.y
        dx * dx + dy * dy < (radius + it.radius) * (radius + it.radius)
    }

    private fun updateShots(dt: Float) {
        val iterator = bullets.iterator()
        while (iterator.hasNext()) {
            val b = iterator.next()
            b.x += b.vx * dt
            b.y += b.vy * dt
            if (b.x < Arena.LEFT - 6f || b.x > Arena.RIGHT + 6f ||
                b.y < Arena.TOP - 6f || b.y > Arena.BOTTOM + 6f || touchesRock(b.x, b.y, 6f)) {
                iterator.remove()
                continue
            }
            val target = players[1 - b.owner]
            val dx = b.x - target.x
            val dy = b.y - target.y
            if (dx * dx + dy * dy <= 24f * 24f) {
                if (target.invulnerable <= 0f) {
                    if (target.shield > 0) target.shield--
                    else target.hp = max(0, target.hp - if (seconds <= 10f) 2 else 1)
                    target.invulnerable = .25f
                }
                iterator.remove()
            }
        }
    }

    private fun updatePowerUp(dt: Float) {
        if (powerUp == null) {
            powerUpTimer -= dt
            if (powerUpTimer <= 0f) {
                // Spawn away from obstacles and the joystick corners.
                var x: Float
                var y: Float
                do {
                    x = 300f + random.nextFloat() * 360f
                    y = 145f + random.nextFloat() * 285f
                } while (touchesRock(x, y, 22f))
                powerUp = PowerUp(x, y, if (random.nextBoolean()) "HEAL" else "SHIELD")
            }
        } else {
            val item = powerUp ?: return
            for (p in players) {
                val dx = p.x - item.x
                val dy = p.y - item.y
                if (dx * dx + dy * dy < 35f * 35f) {
                    if (item.kind == "HEAL") p.hp = min(5, p.hp + 2)
                    else p.shield = min(2, p.shield + 1)
                    powerUp = null
                    powerUpTimer = 9f
                    break
                }
            }
        }
    }

    private fun endRound() {
        val a = players[0]
        val b = players[1]
        winner = when {
            a.hp > b.hp -> 1
            b.hp > a.hp -> 2
            else -> 0
        }
        if (winner != 0) players[winner - 1].score++
        val champion = players.firstOrNull { it.score >= 3 }
        if (champion != null) {
            phase = "MATCH_OVER"
            message = if (players[0] == champion) "CYAN WINS THE MATCH!" else "PINK WINS THE MATCH!"
        } else {
            phase = "ROUND_OVER"
            betweenRounds = 2.5f
            message = when (winner) {
                1 -> "CYAN WINS ROUND $round!"
                2 -> "PINK WINS ROUND $round!"
                else -> "ROUND $round IS A DRAW!"
            }
        }
    }
}
