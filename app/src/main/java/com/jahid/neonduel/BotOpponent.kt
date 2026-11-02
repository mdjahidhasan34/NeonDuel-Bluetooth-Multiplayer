package com.jahid.neonduel

import kotlin.math.hypot
import kotlin.random.Random

/** Standalone offline opponent. No Bluetooth, Wi-Fi, or Android permissions required. */
enum class BotDifficulty(val title: String, val movement: Float, val fireChance: Float) {
    EASY("ROOKIE", .76f, .55f),
    NORMAL("CHALLENGER", .92f, .8f),
    HARD("VETERAN", 1f, .96f)
}

class BotOpponent(
    private val difficulty: BotDifficulty = BotDifficulty.NORMAL,
    private val random: Random = Random.Default
) {
    private var changeDirectionIn = 0f
    private var orbitDirection = 1f
    private var fireDecisionIn = 0f
    private var firing = false
    private var dashDecisionIn = 1.7f

    /** Player 1 is always the human; player 2 is controlled by this bot. */
    fun choose(game: GameEngine, secondsElapsed: Float): Command {
        if (game.phase != "PLAY") return Command()
        val dt = secondsElapsed.coerceIn(0f, .05f)
        val human = game.players[0]
        val bot = game.players[1]

        changeDirectionIn -= dt
        if (changeDirectionIn <= 0f) {
            orbitDirection = if (random.nextBoolean()) 1f else -1f
            changeDirectionIn = 1.0f + random.nextFloat() * 1.5f
        }

        val ex = human.x - bot.x
        val ey = human.y - bot.y
        val distance = hypot(ex.toDouble(), ey.toDouble()).toFloat().coerceAtLeast(1f)
        val ux = ex / distance
        val uy = ey / distance

        // Stay at fighting distance and strafe rather than simply rushing the player.
        val approach = when {
            distance > 310f -> 1f
            distance < 185f -> -.8f
            else -> .12f
        }
        var moveX = ux * approach - uy * orbitDirection * .72f
        var moveY = uy * approach + ux * orbitDirection * .72f

        // Chase useful pickups when hurt or low on shields.
        val bonus = game.powerUp
        if (bonus != null && (bonus.kind == "HEAL" && bot.hp <= 3 ||
                    bonus.kind == "SHIELD" && bot.shield == 0)) {
            val bx = bonus.x - bot.x
            val by = bonus.y - bot.y
            val bd = hypot(bx.toDouble(), by.toDouble()).toFloat()
            if (bd > 1f && bd < 330f) {
                moveX = moveX * .25f + bx / bd * 1.1f
                moveY = moveY * .25f + by / bd * 1.1f
            }
        }

        // Push away from rocks before the game engine's collision check blocks movement.
        for (rock in Arena.rocks) {
            val rx = bot.x - rock.x
            val ry = bot.y - rock.y
            val rd = hypot(rx.toDouble(), ry.toDouble()).toFloat().coerceAtLeast(1f)
            val safeDistance = rock.radius + 100f
            if (rd < safeDistance) {
                val strength = (safeDistance - rd) / safeDistance * 2.7f
                moveX += rx / rd * strength
                moveY += ry / rd * strength
            }
        }

        // Dodge shots travelling towards the bot, not shots that already passed it.
        var bulletDanger = false
        for (shot in game.bullets) {
            if (shot.owner != 0) continue
            val speedSquared = shot.vx * shot.vx + shot.vy * shot.vy
            if (speedSquared < 1f) continue
            val dx = bot.x - shot.x
            val dy = bot.y - shot.y
            val t = ((dx * shot.vx + dy * shot.vy) / speedSquared).coerceIn(0f, .40f)
            val cx = shot.x + shot.vx * t
            val cy = shot.y + shot.vy * t
            val miss = hypot((bot.x - cx).toDouble(), (bot.y - cy).toDouble()).toFloat()
            if (miss < 60f && t < .35f) {
                bulletDanger = true
                val side = if (shot.vx * (bot.y - shot.y) - shot.vy * (bot.x - shot.x) >= 0f) 1f else -1f
                val shotSpeed = kotlin.math.sqrt(speedSquared)
                moveX += -shot.vy / shotSpeed * side * 1.8f
                moveY += shot.vx / shotSpeed * side * 1.8f
                break
            }
        }

        val moveLength = hypot(moveX.toDouble(), moveY.toDouble()).toFloat()
        if (moveLength > 1f) { moveX /= moveLength; moveY /= moveLength }
        // Easy bot moves more slowly so the player gets a chance to practise.
        moveX *= difficulty.movement
        moveY *= difficulty.movement

        fireDecisionIn -= dt
        if (fireDecisionIn <= 0f) {
            firing = random.nextFloat() < difficulty.fireChance
            fireDecisionIn = when (difficulty) {
                BotDifficulty.EASY -> .60f
                BotDifficulty.NORMAL -> .38f
                BotDifficulty.HARD -> .22f
            }
        }
        dashDecisionIn -= dt
        val dash = (bulletDanger || distance < 125f) && dashDecisionIn <= 0f &&
                bot.dashCooldown <= 0f && difficulty != BotDifficulty.EASY
        if (dash) dashDecisionIn = 2.2f + random.nextFloat() * 1.3f

        return Command(moveX, moveY, fire = firing && distance < 690f, dash = dash)
    }
}
