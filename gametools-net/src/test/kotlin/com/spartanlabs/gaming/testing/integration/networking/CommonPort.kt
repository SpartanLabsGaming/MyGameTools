package com.spartanlabs.gaming.testing.integration.networking

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.webtools.MultiConnectionUDPServer
//endregion

//region 2. Intended Function
import java.net.BindException
import java.net.DatagramSocket
//endregion

/**
 * Blocks until [MultiConnectionUDPServer.COMMON_LISTEN_PORT] can be bound again, or
 * [timeoutMillis] elapses.
 *
 * WebTools' `stop()` signals its listener thread to close the shared socket rather than
 * closing it inline, so the fixed common port can still be held for a moment after
 * `GameServer.shutDown()` returns. The next `GameServer` binds that port in its constructor,
 * so a test (or test class) that starts one straight after tearing another down races that
 * release and, on a fast machine, hits a `BindException`. Every port-binding teardown calls
 * this so the coupling lives here rather than as a fixed sleep in each test.
 */
internal fun awaitCommonPortFree(timeoutMillis: Long = 4000L) {
    val deadline = System.currentTimeMillis() + timeoutMillis
    while (System.currentTimeMillis() < deadline) {
        try {
            DatagramSocket(MultiConnectionUDPServer.COMMON_LISTEN_PORT).close()
            return
        } catch (_: BindException) {
            Thread.sleep(20L)
        }
    }
}
