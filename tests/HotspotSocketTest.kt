import com.jahid.neonduel.HotspotGuest
import com.jahid.neonduel.HotspotHost
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

fun main() {
    val joined = CountDownLatch(9)
    val welcomed = CountDownLatch(9)
    val broadcasts = CountDownLatch(9)
    val commands = CountDownLatch(9)
    val errors = mutableListOf<String>()
    val host = HotspotHost(
        onJoined = { joined.countDown() },
        onLine = { _, line -> if (line == "PING") commands.countDown() },
        onLeft = { },
        onFailure = { synchronized(errors) { errors += it } }
    )
    host.start()
    Thread.sleep(250)
    val peers = (1..9).map {
        HotspotGuest(
            onLine = { line ->
                if (line.contains("\"welcome\"")) welcomed.countDown()
                if (line == "BROADCAST") broadcasts.countDown()
            },
            onClosed = { }
        ).also { it.connect("127.0.0.1") }
    }
    try {
        check(joined.await(8, TimeUnit.SECONDS)) { "9 guests could not join" }
        check(welcomed.await(8, TimeUnit.SECONDS)) { "9 welcome packets not received" }
        check(host.ids().size == 10) { "Expected host + 9 guests" }
        host.broadcast("BROADCAST")
        check(broadcasts.await(8, TimeUnit.SECONDS)) { "Broadcast failed" }
        peers.forEach { it.send("PING") }
        check(commands.await(8, TimeUnit.SECONDS)) { "Guest-to-host messages failed" }
        val extraRejected = CountDownLatch(1)
        val extra = HotspotGuest(
            onLine = { line -> if (line.contains("reject")) extraRejected.countDown() },
            onClosed = { }
        )
        extra.connect("127.0.0.1")
        check(extraRejected.await(8, TimeUnit.SECONDS)) { "Room-full rejection failed" }
        check(host.ids().size == 10)
        extra.stop()
        host.lock()
        check(errors.isEmpty()) { "Host failures: $errors" }
        println("Host + 9 guests; welcome, commands, broadcasts and 11th player rejection passed")
    } finally {
        peers.forEach { it.stop() }
        host.stop()
    }
}
