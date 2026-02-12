package com.vr2xr.tracking

import java.io.Closeable
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicReference

class XrealImuClient : Closeable {
    private var socket: Socket? = null
    private val latest = AtomicReference<ImuSample?>(null)

    fun connect() {
        if (socket?.isConnected == true) return
        socket = Socket().apply {
            connect(InetSocketAddress("169.254.2.1", 52998), 2_000)
        }
    }

    fun disconnect() {
        close()
    }

    fun latestSample(): ImuSample? = latest.get()

    override fun close() {
        runCatching { socket?.close() }
        socket = null
    }
}
