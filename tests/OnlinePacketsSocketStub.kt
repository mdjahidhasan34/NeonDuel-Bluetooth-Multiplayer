package com.jahid.neonduel

// JVM socket smoke-test shim only; the Android app uses src/main/.../OnlinePackets.kt.
object OnlinePackets {
    fun welcome(id: Int): String = "{\"t\":\"welcome\",\"id\":$id}"
    fun reject(reason: String): String = "{\"t\":\"reject\",\"message\":\"$reason\"}"
}
