package com.jahid.neonduel

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Landscape top-down game camera, trees, hills, helmeted characters, and touch controls. */
class HotspotArenaView(
    context: Context,
    private val state: () -> OnlineState?,
    private val terrain: () -> List<Rock>,
    private val selfId: () -> Int,
    private val onLeave: () -> Unit,
    private val onReplay: () -> Unit
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val controls = Command()
    private var joystickPointer = -1
    private var firePointer = -1
    private var dashLatch = false
    private var scale = 1f
    private var offsetX = 0f
    private var offsetY = 0f
    private val teamColors = intArrayOf(0xff39e5ff.toInt(), 0xffff599d.toInt(),
        0xffffca64.toInt(), 0xff99f089.toInt(), 0xffb69bff.toInt())
    init { keepScreenOn = true }

    fun takeCommand(): Command = controls.copy(fire = firePointer != -1, dash = dashLatch).also { dashLatch = false }

    private fun circle(c: Canvas, x: Float, y: Float, r: Float, col: Int, fill: Boolean = true) {
        paint.color = col; paint.style = if (fill) Paint.Style.FILL else Paint.Style.STROKE
        paint.strokeWidth = 3f
        c.drawCircle(x, y, r, paint)
        paint.style = Paint.Style.FILL
    }
    private fun txt(c: Canvas, s: String, x: Float, y: Float, size: Float = 17f,
        color: Int = Color.WHITE, centered: Boolean = false) {
        paint.color = color; paint.textSize = size; paint.typeface = Typeface.DEFAULT_BOLD
        paint.textAlign = if (centered) Paint.Align.CENTER else Paint.Align.LEFT
        c.drawText(s, x, y, paint)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        scale = min(width / 960f, height / 540f)
        offsetX = (width - scale * 960f) / 2
        offsetY = (height - scale * 540f) / 2
        canvas.drawColor(0xff081423.toInt())
        canvas.save()
        canvas.translate(offsetX, offsetY); canvas.scale(scale, scale)
        val s = state()
        if (s == null) { txt(canvas, "CONNECTING TO HOTSPOT MATCH...", 480f, 270f, 26f, Color.WHITE, true); canvas.restore(); return }
        val me = s.players.firstOrNull { it.id == selfId() }
        val cx = (me?.x ?: s.width / 2f).coerceIn(480f, max(480f, s.width - 480f))
        val cy = (me?.y ?: s.height / 2f).coerceIn(270f, max(270f, s.height - 270f))
        canvas.save()
        canvas.translate(480f - cx, 270f - cy)
        val grassTop = when (s.light) {
            LightMode.DAY -> 0xff639b55.toInt()
            LightMode.SUNSET -> 0xff806d49.toInt()
            LightMode.NIGHT -> 0xff24483b.toInt()
        }
        val grassBottom = when (s.light) {
            LightMode.DAY -> 0xff315e3b.toInt()
            LightMode.SUNSET -> 0xff574438.toInt()
            LightMode.NIGHT -> 0xff102c2e.toInt()
        }
        paint.shader = LinearGradient(0f, 0f, 0f, s.height, grassTop, grassBottom, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, s.width, s.height, paint)
        paint.shader = null
        paint.color = 0x2248d293
        for (x in 0..s.width.toInt() step 80) canvas.drawLine(x.toFloat(), 0f, x.toFloat(), s.height, paint)
        for (y in 0..s.height.toInt() step 80) canvas.drawLine(0f, y.toFloat(), s.width, y.toFloat(), paint)
        // Deterministic vegetation / rocky hills shared by host and guests.
        val hills = terrain()
        hills.forEachIndexed { i, r ->
            if (i % 4 == 0) {
                circle(canvas, r.x + 5f, r.y + 8f, r.radius + 18f, 0xff526753.toInt())
                circle(canvas, r.x, r.y, r.radius + 12f, 0xff687666.toInt())
                circle(canvas, r.x - 4f, r.y - 6f, r.radius, 0xff819083.toInt())
            } else {
                circle(canvas, r.x + 5f, r.y + 11f, r.radius + 12f, 0x55081012)
                circle(canvas, r.x, r.y, r.radius + 7f, 0xff1b5a37.toInt())
                circle(canvas, r.x - 7f, r.y - 8f, r.radius, 0xff3d9a54.toInt())
            }
        }
        s.bullets.forEach { circle(canvas, it.x, it.y, 6f, 0xffffe781.toInt()) }
        s.players.forEach { p ->
            if (!p.connected || p.hp <= 0) return@forEach
            val team = if (s.mode == RoomMode.DUO_TEAMS) p.team else p.id
            val color = teamColors[(team % teamColors.size + teamColors.size) % teamColors.size]
            circle(canvas, p.x + 5, p.y + 10, 24f, 0x66000000)
            // Simple top-down human: boots, uniform, hands, helmet and weapon.
            paint.color = 0xff17272a.toInt()
            canvas.drawRoundRect(RectF(p.x - 15, p.y + 12, p.x - 3, p.y + 32), 4f, 4f, paint)
            canvas.drawRoundRect(RectF(p.x + 3, p.y + 12, p.x + 15, p.y + 32), 4f, 4f, paint)
            circle(canvas, p.x, p.y, 21f, color)
            circle(canvas, p.x, p.y - 4, 13f, 0xffd49d70.toInt())
            circle(canvas, p.x, p.y - 9, 15f, 0xff26323a.toInt())
            paint.color = 0xffb5c3c1.toInt(); paint.strokeWidth = 8f
            canvas.drawLine(p.x + p.aimX * 9, p.y + p.aimY * 9,
                p.x + p.aimX * 36, p.y + p.aimY * 36, paint)
            txt(canvas, "P${p.id + 1}", p.x, p.y - 32, 15f, Color.WHITE, true)
            paint.color = 0xff411b25.toInt(); canvas.drawRect(p.x - 25f, p.y - 29f, p.x + 25f, p.y - 24f, paint)
            paint.color = 0xff66f1ae.toInt(); canvas.drawRect(p.x - 25f, p.y - 29f, p.x - 25f + p.hp * 10f, p.y - 24f, paint)
            if (p.id == selfId()) circle(canvas, p.x, p.y, 28f, 0xffecf8ff.toInt(), false)
        }
        canvas.restore()
        // Heads-up display and controls are not affected by camera scrolling.
        paint.color = 0xbb071728.toInt()
        canvas.drawRoundRect(RectF(15f, 12f, 945f, 73f), 12f, 12f, paint)
        txt(canvas, if (s.mode == RoomMode.FREE_FOR_ALL) "FREE FOR ALL" else "DUO TEAMS", 30f, 37f, 19f)
        txt(canvas, "${s.players.count { it.connected }} PLAYERS", 30f, 61f, 14f, 0xffbcd5e5.toInt())
        txt(canvas, "${s.time.toInt()}s", 480f, 48f, 28f, 0xffffe17c.toInt(), true)
        val lightColor = if (s.light == LightMode.NIGHT) 0xffd8e1ff.toInt() else 0xffffd16b.toInt()
        circle(canvas, 658f, 37f, 14f, lightColor)
        txt(canvas, s.light.name, 678f, 44f, 12f, Color.WHITE)
        txt(canvas, "P${selfId() + 1}: ${me?.kills ?: 0} KILLS", 733f, 47f, 17f)
        txt(canvas, "EXIT", 900f, 103f, 16f, 0xffff98a7.toInt(), true)
        circle(canvas, 145f, 440f, 73f, 0x66384c66)
        circle(canvas, 145f + controls.dx * 37f, 440f + controls.dy * 37f, 32f, 0xff67b6c8.toInt())
        circle(canvas, 835f, 437f, 62f, 0xffbf2d5f.toInt())
        txt(canvas, "FIRE", 835f, 446f, 23f, Color.WHITE, true)
        circle(canvas, 694f, 468f, 43f, 0xff334a88.toInt())
        txt(canvas, "DASH", 694f, 473f, 17f, Color.WHITE, true)
        if (!s.running) {
            paint.color = 0xe9081425.toInt(); canvas.drawRect(190f, 145f, 770f, 378f, paint)
            txt(canvas, "MATCH OVER", 480f, 210f, 36f, Color.WHITE, true)
            val winner = if (s.mode == RoomMode.FREE_FOR_ALL) "WINNER: PLAYER ${s.winningPlayer + 1}"
                else "WINNER: TEAM ${s.winningTeam + 1}"
            txt(canvas, winner, 480f, 265f, 22f, 0xffffdb83.toInt(), true)
            txt(canvas, "HOST: TAP REMATCH", 480f, 328f, 18f, Color.WHITE, true)
        }
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        val index = event.actionIndex
        val id = event.getPointerId(index)
        val x = (event.getX(index) - offsetX) / scale
        val y = (event.getY(index) - offsetY) / scale
        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (x > 850 && y < 135) { onLeave(); return true }
                if (state()?.running == false && x in 190f..770f && y in 145f..380f) {
                    onReplay(); return true
                }
                if (x < 290 && y > 310) { joystickPointer = id; updateStick(x, y) }
                if (x > 760 && y > 345) firePointer = id
                if (x in 635f..760f && y > 395) dashLatch = true
            }
            MotionEvent.ACTION_MOVE -> {
                if (joystickPointer != -1) {
                    val p = event.findPointerIndex(joystickPointer)
                    if (p >= 0) updateStick((event.getX(p) - offsetX) / scale,
                        (event.getY(p) - offsetY) / scale)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                if (id == joystickPointer || action == MotionEvent.ACTION_CANCEL) {
                    joystickPointer = -1; controls.dx = 0f; controls.dy = 0f
                }
                if (id == firePointer || action == MotionEvent.ACTION_CANCEL) firePointer = -1
            }
        }
        return true
    }
    private fun updateStick(x: Float, y: Float) {
        val dx = x - 145f; val dy = y - 440f
        val length = max(73f, hypot(dx, dy))
        controls.dx = dx / length
        controls.dy = dy / length
    }
}
