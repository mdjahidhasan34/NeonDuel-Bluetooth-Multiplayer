package com.jahid.neonduel

import org.json.JSONArray
import org.json.JSONObject

/** One JSON object per line, over a Bluetooth Classic RFCOMM socket. */
object GameWire {
    fun input(c: Command): String = JSONObject().apply {
        put("t", "input")
        put("x", c.dx.toDouble())
        put("y", c.dy.toDouble())
        put("f", c.fire)
        put("d", c.dash)
    }.toString()

    fun decodeInput(j: JSONObject): Command = Command(
        j.optDouble("x", 0.0).toFloat().coerceIn(-1f, 1f),
        j.optDouble("y", 0.0).toFloat().coerceIn(-1f, 1f),
        j.optBoolean("f", false),
        j.optBoolean("d", false)
    )

    fun state(g: GameEngine): String {
        val s = g.snapshot()
        return JSONObject().apply {
            put("t", "state")
            put("r", s.round)
            put("sec", s.seconds.toDouble())
            put("phase", s.phase)
            put("msg", s.message)
            put("win", s.winner)
            put("p", JSONArray().apply {
                s.players.forEach { f ->
                    put(JSONArray().apply {
                        put(f.x.toDouble()); put(f.y.toDouble()); put(f.hp); put(f.shield)
                        put(f.score); put(f.aimX.toDouble()); put(f.aimY.toDouble())
                        put(f.dashCooldown.toDouble()); put(f.invulnerable.toDouble())
                    })
                }
            })
            put("b", JSONArray().apply {
                s.bullets.forEach { b ->
                    put(JSONArray().apply {
                        put(b.x.toDouble()); put(b.y.toDouble()); put(b.vx.toDouble()); put(b.vy.toDouble())
                        put(b.owner); put(b.id)
                    })
                }
            })
            s.powerUp?.let { q ->
                put("bonus", JSONArray().apply { put(q.x.toDouble()); put(q.y.toDouble()); put(q.kind) })
            }
        }.toString()
    }

    fun decodeState(j: JSONObject): GameSnapshot? {
        val p = j.optJSONArray("p") ?: return null
        if (p.length() != 2) return null
        val people = (0..1).map { i ->
            val v = p.getJSONArray(i)
            Fighter(
                x = v.getDouble(0).toFloat(), y = v.getDouble(1).toFloat(),
                hp = v.getInt(2), shield = v.getInt(3), score = v.getInt(4),
                aimX = v.getDouble(5).toFloat(), aimY = v.getDouble(6).toFloat(),
                dashCooldown = v.getDouble(7).toFloat(), invulnerable = v.getDouble(8).toFloat()
            )
        }
        val bullets = mutableListOf<Shot>()
        val array = j.optJSONArray("b") ?: JSONArray()
        for (i in 0 until minOf(array.length(), 60)) {
            val v = array.getJSONArray(i)
            bullets.add(Shot(v.getDouble(0).toFloat(), v.getDouble(1).toFloat(),
                v.getDouble(2).toFloat(), v.getDouble(3).toFloat(), v.getInt(4), v.getInt(5)))
        }
        val bonus = j.optJSONArray("bonus")?.let {
            PowerUp(it.getDouble(0).toFloat(), it.getDouble(1).toFloat(), it.getString(2))
        }
        return GameSnapshot(
            j.optInt("r", 1), j.optDouble("sec", 45.0).toFloat().coerceIn(0f, 45f),
            j.optString("phase", "PLAY"), j.optString("msg", ""), j.optInt("win", 0),
            people, bullets, bonus
        )
    }
}
