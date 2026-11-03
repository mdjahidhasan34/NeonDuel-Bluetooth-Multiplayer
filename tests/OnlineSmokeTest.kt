import com.jahid.neonduel.*

fun main() {
    // All supported FFA room sizes: host is a player, not a dedicated server.
    for (size in 2..10) {
        val ids = (0 until size).toList()
        val game = OnlineGame(RoomMode.FREE_FOR_ALL, ids)
        check(game.players.size == size)
        check(game.players.all { it.team == -1 })
        check(game.width > 0f && game.height > 0f)
        val startX = game.players[0].x
        repeat(20) { game.update(.033f, mapOf(0 to Command(dx = 1f, fire = true))) }
        check(game.time < 120f)
        check(game.players[0].x >= startX || game.rocks.isNotEmpty())
        val copy = OnlineGame(RoomMode.FREE_FOR_ALL, ids)
        copy.load(game.state())
        check(copy.players.size == game.players.size)
        check(copy.bullets.size == game.bullets.size)
    }
    // In team mode the largest team has exactly two fighters.
    for (size in listOf(4, 6, 8, 10)) {
        val game = OnlineGame(RoomMode.DUO_TEAMS, (0 until size).toList())
        check(game.players.groupBy { it.team }.values.all { it.size == 2 })
        game.disconnect(1)
        check(game.players[1].hp == 0)
        repeat(60) { game.update(.033f, emptyMap()) }
        check(!game.players[1].connected && game.players[1].hp == 0)
    }
    val battle = OnlineGame(RoomMode.FREE_FOR_ALL, listOf(0, 1))
    repeat(4000) { battle.update(.033f, emptyMap()) }
    check(!battle.running && battle.time == 0f)
    check(battle.winningPlayer in 0..1)
    println("OnlineGame 2-10 FFA, duo teams, disconnect, sync, timer tests passed")
}
