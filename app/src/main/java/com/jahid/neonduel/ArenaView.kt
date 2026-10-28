package com.jahid.neonduel

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Neon arcade visuals drawn in code: no online assets, internet, or third-party libraries. */
class ArenaView(
    context: Context,
    private val game: GameEngine,
    private val mySide: Int,
    private val onLeave: () -> Unit,
    private val onRematch: () -> Unit
) : View(context) {
    companion object {
        private val CYAN = Color.rgb(56, 233, 255)
        private val PINK = Color.rgb(255, 80, 164)
        private val WHITE = Color.rgb(228, 242, 255)
        private val DIM = Color.rgb(114, 144, 174)
        private val GOLD = Color.rgb(255, 215, 95)
        private val BG = Color.rgb(4, 12, 29)
        private val PANEL = Color.rgb(13, 30, 54)
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val starDots = List(75) { n ->
        val x = ((n * 137 + 49) % 947).toFloat()
        val y = ((n * 73 + 21) % 527).toFloat()
        x to y
    }
    private var stickId = -1
    private var fireId = -1
    private val baseX = 160f
    private val baseY = 423f
    private var stickX = 0f
    private var stickY = 0f
    private var dashPressed = false
    private var vwScale = 1f
    private var dxScreen = 0f
    private var dyScreen = 0f

    init {
        isFocusable = true
        keepScreenOn = true
    }

    fun takeCommand(): Command = Command(stickX, stickY, fireId != -1, dashPressed).also {
        dashPressed = false
    }

    private fun color(c: Int, a: Int = 255): Paint = paint.apply {
        color = Color.argb(a.coerceIn(0, 255), Color.red(c), Color.green(c), Color.blue(c))
        style = Paint.Style.FILL
        strokeWidth = 1f
        shader = null
        pathEffect = null
        strokeCap = Paint.Cap.ROUND
        typeface = Typeface.DEFAULT
    }

    private fun txt(c: Canvas, words: String, x: Float, y: Float, size: Float, shade: Int = WHITE,
                    centered: Boolean = false, weight: Boolean = false, alpha: Int = 255) {
        val p = color(shade, alpha)
        p.textSize = size
        p.typeface = if (weight) Typeface.create("sans-serif-black", Typeface.BOLD) else Typeface.create("sans-serif-medium", Typeface.NORMAL)
        p.textAlign = if (centered) Paint.Align.CENTER else Paint.Align.LEFT
        c.drawText(words, x, y, p)
        p.textAlign = Paint.Align.LEFT
    }

    private fun roundRect(c: Canvas, x: Float, y: Float, w: Float, h: Float, r: Float, shade: Int, alpha: Int = 255) {
        c.drawRoundRect(RectF(x, y, x + w, y + h), r, r, color(shade, alpha))
    }

    private fun ring(c: Canvas, x: Float, y: Float, radius: Float, shade: Int, width: Float, alpha: Int = 255) {
        val p = color(shade, alpha)
        p.style = Paint.Style.STROKE
        p.strokeWidth = width
        c.drawCircle(x, y, radius, p)
        p.style = Paint.Style.FILL
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        c.drawColor(BG)
        vwScale = min(width / Arena.W, height / Arena.H).coerceAtLeast(.01f)
        dxScreen = (width - Arena.W * vwScale) / 2f
        dyScreen = (height - Arena.H * vwScale) / 2f
        c.save()
        c.translate(dxScreen, dyScreen)
        c.scale(vwScale, vwScale)

        val time = SystemClock.uptimeMillis() / 1000f
        val bg = color(BG)
        bg.shader = LinearGradient(0f, 0f, 960f, 540f,
            Color.rgb(7, 25, 47), BG, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, Arena.W, Arena.H, bg)
        bg.shader = null
        starDots.forEachIndexed { n, dot ->
            c.drawCircle(dot.first, dot.second, if (n % 5 == 0) 1.5f else .8f,
                color(CYAN, (40 + 25 * (1 + sin(time * 1.2f + n)).toInt()).coerceIn(20, 115)))
        }
        drawArena(c, time)
        drawBonuses(c, time)
        game.bullets.forEach { drawBullet(c, it) }
        game.players.forEachIndexed { i, p -> drawFighter(c, i, p, time) }
        drawHud(c)
        drawControls(c, time)
        drawOverlays(c)
        c.restore()
        postInvalidateDelayed(16L)
    }

    private fun drawArena(c: Canvas, t: Float) {
        roundRect(c, 59f, 91f, 842f, 414f, 23f, PANEL, 190)
        val border = color(CYAN, 72)
        border.style = Paint.Style.STROKE
        border.strokeWidth = 2.5f
        c.drawRoundRect(RectF(59f, 91f, 901f, 505f), 23f, 23f, border)
        border.style = Paint.Style.FILL
        for (i in 0..16) {
            val x = 75f + i * 50f
            c.drawLine(x, 103f, x, 495f, color(CYAN, 20))
        }
        for (i in 0..7) {
            val y = 110f + i * 52f
            c.drawLine(65f, y, 895f, y, color(CYAN, 20))
        }
        ring(c, 480f, 299f, 128f, PINK, 1.5f, 44)
        ring(c, 480f, 299f, 65f, CYAN, 1.5f, 35)
        for (rock in Arena.rocks) {
            c.drawCircle(rock.x, rock.y + 5f, rock.radius + 5f, color(Color.BLACK, 115))
            c.drawCircle(rock.x, rock.y, rock.radius, color(Color.rgb(26, 55, 79)))
            ring(c, rock.x, rock.y, rock.radius, CYAN, 2f, 112)
            c.drawLine(rock.x - rock.radius / 2, rock.y - 5f,
                rock.x + rock.radius / 4, rock.y - rock.radius / 2, color(WHITE, 48))
        }
        if (game.seconds <= 10 && game.phase == "PLAY") {
            val glow = (55 + (25 * (1 + sin(t * 4f))).toInt()).coerceIn(50, 105)
            ring(c, 480f, 297f, 403f, PINK, 13f, glow)
            txt(c, "SUDDEN DEATH  •  DOUBLE DAMAGE", 480f, 123f, 12f, GOLD, true, true)
        }
    }

    private fun drawBonuses(c: Canvas, t: Float) {
        val power = game.powerUp ?: return
        val isHeal = power.kind == "HEAL"
        val shade = if (isHeal) Color.rgb(72, 255, 160) else GOLD
        val grow = 1f + sin(t * 6f) * .08f
        c.drawCircle(power.x, power.y, 24f * grow, color(shade, 28))
        ring(c, power.x, power.y, 19f * grow, shade, 3f, 210)
        txt(c, if (isHeal) "+" else "S", power.x, power.y + 7f, 22f, shade, true, true)
    }

    private fun drawBullet(c: Canvas, b: Shot) {
        val shade = if (b.owner == 0) CYAN else PINK
        val len = max(1f, hypot(b.vx.toDouble(), b.vy.toDouble()).toFloat())
        val ux = b.vx / len
        val uy = b.vy / len
        val p = color(shade, 190)
        p.strokeWidth = 4.2f
        c.drawLine(b.x - ux * 16f, b.y - uy * 16f, b.x + ux * 3f, b.y + uy * 3f, p)
        c.drawCircle(b.x, b.y, 4f, color(WHITE))
    }

    private fun drawFighter(c: Canvas, i: Int, f: Fighter, t: Float) {
        val shade = if (i == 0) CYAN else PINK
        val flicker = f.invulnerable > 0f && (t * 16f).toInt() % 2 == 0
        if (flicker) return
        c.drawCircle(f.x, f.y, 31f, color(shade, 23))
        ring(c, f.x, f.y, 25f, shade, 2.5f, 120)
        if (f.shield > 0) ring(c, f.x, f.y, 32f, GOLD, 4f, 210)
        c.drawCircle(f.x, f.y + 3f, 20f, color(Color.rgb(9, 22, 40)))
        c.drawCircle(f.x, f.y, 18f, color(shade))
        ring(c, f.x, f.y, 18f, WHITE, 2f, 140)
        val p = color(WHITE)
        p.strokeWidth = 8f
        c.drawLine(f.x + f.aimX * 9f, f.y + f.aimY * 9f,
            f.x + f.aimX * 31f, f.y + f.aimY * 31f, p)
        c.drawCircle(f.x, f.y, 6f, color(Color.rgb(8, 31, 48)))
        txt(c, if (i == 0) "P1" else "P2", f.x, f.y - 35f, 11f, shade, true, true)
        // Tiny health meter above character, in addition to the top HUD.
        roundRect(c, f.x - 23f, f.y - 47f, 46f, 4f, 2f, DIM, 120)
        roundRect(c, f.x - 23f, f.y - 47f, 46f * f.hp.coerceIn(0, 5) / 5f, 4f, 2f, shade)
    }

    private fun drawHud(c: Canvas) {
        roundRect(c, 15f, 13f, 930f, 66f, 17f, PANEL, 235)
        val a = game.players[0]
        val b = game.players[1]
        txt(c, "CYAN", 34f, 35f, 16f, CYAN, weight = true)
        txt(c, "PINK", 791f, 35f, 16f, PINK, weight = true) // right-aligned below via bar
        for (i in 0 until 5) {
            roundRect(c, 35f + i * 26f, 46f, 21f, 12f, 4f, if (i < a.hp) CYAN else DIM, if (i < a.hp) 245 else 64)
            roundRect(c, 791f + i * 26f, 46f, 21f, 12f, 4f, if (i < b.hp) PINK else DIM, if (i < b.hp) 245 else 64)
        }
        txt(c, "${a.score}  :  ${b.score}", 480f, 47f, 30f, WHITE, centered = true, weight = true)
        txt(c, "ROUND ${game.round}   •   ${game.seconds.toInt()}s", 480f, 67f, 12f, DIM, centered = true)
        txt(c, "${if (mySide == 0) "YOU" else "RIVAL"}", 191f, 56f, 12f, DIM, weight = true)
        txt(c, "${if (mySide == 1) "YOU" else "RIVAL"}", 738f, 56f, 12f, DIM, weight = true)
        roundRect(c, 850f, 17f, 84f, 17f, 8f, Color.rgb(39, 58, 78))
        txt(c, "QUIT ✕", 892f, 29f, 10f, WHITE, centered = true, weight = true)
    }

    private fun drawControls(c: Canvas, t: Float) {
        val p = game.players[mySide]
        // Floating-left joystick
        c.drawCircle(baseX, baseY, 73f, color(CYAN, 19))
        ring(c, baseX, baseY, 64f, CYAN, 2f, 90)
        c.drawCircle(baseX + stickX * 43f, baseY + stickY * 43f, 28f, color(CYAN, 55))
        ring(c, baseX + stickX * 43f, baseY + stickY * 43f, 27f, CYAN, 2.5f, 180)
        txt(c, "MOVE", baseX, 514f, 11f, DIM, centered = true, weight = true)
        // Large dedicated fire button; the player can hold it.
        c.drawCircle(810f, 414f, 62f, color(PINK, if (fireId >= 0) 100 else 43))
        ring(c, 810f, 414f, 58f, PINK, if (fireId >= 0) 5f else 3f, 200)
        txt(c, "FIRE", 810f, 422f, 24f, WHITE, true, true)
        val ready = p.dashCooldown <= .01f
        c.drawCircle(669f, 449f, 40f, color(if (ready) GOLD else DIM, 62))
        ring(c, 669f, 449f, 38f, if (ready) GOLD else DIM, 2.5f)
        txt(c, "DASH", 669f, 454f, 14f, if (ready) WHITE else DIM, true, true)
        if (!ready) txt(c, "${p.dashCooldown.toInt() + 1}s", 669f, 475f, 10f, DIM, true)
        txt(c, "AUTO-AIM  •  FIRST TO 3 ROUNDS", 482f, 526f, 10f, DIM, true)
    }

    private fun drawOverlays(c: Canvas) {
        if (game.phase == "PLAY") return
        roundRect(c, 260f, 166f, 440f, 191f, 24f, BG, 236)
        ring(c, 480f, 250f, 104f, if (game.winner == 1) CYAN else if (game.winner == 2) PINK else GOLD, 2f, 80)
        txt(c, game.message, 480f, 240f, 25f, WHITE, centered = true, weight = true)
        if (game.phase == "ROUND_OVER") {
            txt(c, "NEXT ROUND STARTING...", 480f, 281f, 14f, DIM, centered = true)
        } else {
            txt(c, "MATCH COMPLETE", 480f, 282f, 15f, DIM, centered = true)
            roundRect(c, 366f, 304f, 228f, 41f, 15f, CYAN)
            txt(c, "PLAY AGAIN  ↻", 480f, 331f, 16f, BG, centered = true, weight = true)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
            stickId = -1; fireId = -1; stickX = 0f; stickY = 0f
            return true
        }
        val ai = event.actionIndex
        val pointerId = event.getPointerId(ai)
        val x = (event.getX(ai) - dxScreen) / vwScale
        val y = (event.getY(ai) - dyScreen) / vwScale
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (x > 848f && y < 85f) { onLeave(); return true }
                if (game.phase == "MATCH_OVER" && x in 340f..625f && y in 294f..358f) {
                    onRematch(); return true
                }
                if (game.phase == "PLAY") {
                    when {
                        x < 355f && y > 290f && stickId == -1 -> { stickId = pointerId; setStick(x, y) }
                        x in 605f..728f && y > 375f -> { dashPressed = true }
                        x > 732f && y > 315f -> fireId = pointerId
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (stickId >= 0) {
                    val index = event.findPointerIndex(stickId)
                    if (index >= 0) setStick((event.getX(index) - dxScreen) / vwScale,
                        (event.getY(index) - dyScreen) / vwScale)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (pointerId == stickId) { stickId = -1; stickX = 0f; stickY = 0f }
                if (pointerId == fireId) fireId = -1
            }
        }
        invalidate()
        return true
    }

    private fun setStick(x: Float, y: Float) {
        val dx = x - baseX
        val dy = y - baseY
        val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        val divisor = max(64f, distance)
        stickX = (dx / divisor).coerceIn(-1f, 1f)
        stickY = (dy / divisor).coerceIn(-1f, 1f)
        if (distance < 9f) { stickX = 0f; stickY = 0f }
    }
}
