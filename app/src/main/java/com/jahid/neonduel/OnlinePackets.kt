package com.jahid.neonduel

import org.json.JSONArray
import org.json.JSONObject

/** Newline-separated JSON packets shared by hotspot host and guests. */
object OnlinePackets {
    fun input(command: Command) = JSONObject().apply {
        put("t", "cmd")
        put("x", command.dx.toDouble()); put("y", command.dy.toDouble())
        put("f", command.fire); put("d", command.dash)
    }.toString()

    fun decodeInput(j: JSONObject): Command = Command(
        j.optDouble("x", 0.0).toFloat().coerceIn(-1f, 1f),
        j.optDouble("y", 0.0).toFloat().coerceIn(-1f, 1f),
        j.optBoolean("f"), j.optBoolean("d"))

    fun welcome(id: Int) = JSONObject().put("t", "welcome").put("id", id).toString()
    fun reject(reason: String) = JSONObject().put("t", "reject").put("message", reason).toString()
    fun lobby(mode: RoomMode, ids: List<Int>, light: LightMode = LightMode.DAY) = JSONObject().apply {
        put("t", "lobby"); put("mode", mode.name); put("light", light.name)
        put("members", JSONArray(ids))
    }.toString()
    fun start(mode: RoomMode, ids: List<Int>, light: LightMode = LightMode.DAY) = JSONObject().apply {
        put("t", "start"); put("mode", mode.name); put("light", light.name)
        put("members", JSONArray(ids))
    }.toString()
    fun memberIds(j: JSONObject): List<Int> = j.optJSONArray("members")?.let { a ->
        (0 until a.length()).map { a.getInt(it) }
    } ?: emptyList()

    fun state(game: OnlineGame): String {
        val s = game.state()
        return JSONObject().apply {
            put("t", "world"); put("mode", s.mode.name); put("light", s.light.name)
            put("w", s.width.toDouble()); put("h", s.height.toDouble())
            put("time", s.time.toDouble()); put("run", s.running)
            put("wt", s.winningTeam); put("wp", s.winningPlayer)
            put("players", JSONArray().apply {
                s.players.forEach { p ->
                    put(JSONArray().apply {
                        put(p.id); put(p.team); put(p.x.toDouble()); put(p.y.toDouble())
                        put(p.hp); put(p.shield); put(p.kills)
                        put(p.aimX.toDouble()); put(p.aimY.toDouble())
                        put(p.dashCooldown.toDouble()); put(p.respawnIn.toDouble()); put(p.connected)
                    })
                }
            })
            put("bullets", JSONArray().apply {
                s.bullets.forEach { b -> put(JSONArray().apply {
                    put(b.x.toDouble()); put(b.y.toDouble())
                    put(b.vx.toDouble()); put(b.vy.toDouble()); put(b.owner)
                }) }
            })
        }.toString()
    }

    fun decodeState(j: JSONObject): OnlineState? {
        val mode = try { RoomMode.valueOf(j.getString("mode")) } catch (_: Exception) { return null }
        val ps = j.optJSONArray("players") ?: return null
        val players = (0 until ps.length()).map { i ->
            val a = ps.getJSONArray(i)
            OnlinePlayer(a.getInt(0), a.getInt(1), a.getDouble(2).toFloat(), a.getDouble(3).toFloat(),
                a.getInt(4), a.getInt(5), a.getInt(6), a.getDouble(7).toFloat(),
                a.getDouble(8).toFloat(), 0f, a.getDouble(9).toFloat(),
                a.getDouble(10).toFloat(), a.getBoolean(11))
        }
        val bs = j.optJSONArray("bullets") ?: JSONArray()
        val shots = (0 until bs.length()).map { i ->
            val a = bs.getJSONArray(i)
            OnlineBullet(a.getDouble(0).toFloat(), a.getDouble(1).toFloat(),
                a.getDouble(2).toFloat(), a.getDouble(3).toFloat(), a.getInt(4))
        }
        return OnlineState(mode, j.getDouble("w").toFloat(), j.getDouble("h").toFloat(),
            j.getDouble("time").toFloat(), j.getBoolean("run"), players, shots,
            LightMode.valueOf(j.optString("light", "DAY")),
            j.optInt("wt", -1), j.optInt("wp", -1))
    }
}
