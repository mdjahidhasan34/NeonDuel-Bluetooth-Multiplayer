import com.jahid.neonduel.*
import kotlin.random.Random

fun main() {
    val g = GameEngine(Random(42))
    check(g.round == 1 && g.phase == "PLAY")
    val x0 = g.players[0].x
    repeat(30) { g.update(1f / 30f, Command(dx = 1f), Command()) }
    check(g.players[0].x > x0)
    check(g.seconds < 45f)
    val shots = g.bullets.size
    g.update(0.03f, Command(fire = true), Command())
    check(g.bullets.size > shots)
    val saved = g.snapshot()
    val replica = GameEngine()
    replica.loadRemote(saved)
    check(replica.players[0].x == g.players[0].x)
    check(replica.bullets.size == g.bullets.size)
    val beforeDash = g.players[0].x
    val dash = Command(dx = 1f, dash = true)
    g.update(0.03f, dash, Command())
    check(g.players[0].x > beforeDash + 80f)
    check(g.players[0].dashCooldown > 0f)
    check(!dash.dash)
    g.resetMatch()
    check(g.players.all { it.hp == 5 && it.score == 0 })
    repeat(1500) { g.update(1f / 30f, Command(), Command()) }
    check(g.phase == "ROUND_OVER" || g.round >= 2)

    // From a fresh game, repeated fire eventually wins 3 rounds.
    val battle = GameEngine(Random(2))
    repeat(2500) {
        if (battle.phase != "MATCH_OVER") battle.update(.033f, Command(fire = true), Command())
    }
    check(battle.phase == "MATCH_OVER")
    check(battle.players[0].score == 3)
    battle.resetMatch()
    check(battle.phase == "PLAY" && battle.players[0].score == 0)
    // Offline bot moves and can fire without needing any Bluetooth connection.
    val botBattle = GameEngine(Random(102))
    val opponent = BotOpponent(BotDifficulty.NORMAL, Random(99))
    val startX = botBattle.players[1].x
    var botShots = 0
    repeat(180) {
        val command = opponent.choose(botBattle, 1f / 30f)
        if (command.fire) botShots++
        botBattle.update(1f / 30f, Command(), command)
    }
    check(botBattle.players[1].x != startX)
    check(botShots > 0)
    check(botBattle.players[1].x in Arena.LEFT..Arena.RIGHT)
    check(botBattle.players[1].y in Arena.TOP..Arena.BOTTOM)
    botBattle.resetMatch()
    check(botBattle.phase == "PLAY" && botBattle.players.all { it.score == 0 })
    println("GameEngine + offline bot + rematch smoke tests passed")
}
