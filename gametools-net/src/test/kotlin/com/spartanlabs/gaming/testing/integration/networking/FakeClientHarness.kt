package com.spartanlabs.gaming.testing.integration.networking

//region 1. Organization Internal
// 1.1 Spartan Laboratories
import com.spartanlabs.webtools.MultiConnectionUDPServer
//endregion

//region 2. Intended Function
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
//endregion

//region 4. Programming Infrastructure and Support
// 4.1 Logging
import org.slf4j.LoggerFactory
//endregion

/**
 * Drives the client half of the [GameServer] handshake and every subsequent exchange over
 * real loopback sockets. Under WebTools 2.0.0c there is one socket per client for the whole
 * session - the same one `Iam` was sent from also carries application data, broadcasts, and
 * `KA` keepalives both ways - so this harness is both the handshake driver and the data
 * channel a `FakePlayerChannel` used to be. The harness owns one socket, so it is exactly one
 * handshake origin; a second [handshake] on the same harness is seen by the server as a
 * retransmit and repeats the first reply rather than registering anything new. Tests that need
 * several distinct players create several harnesses (`fixture.client()` per player).
 *
 * Everything happens over the loopback address, so the tests neither need a network nor
 * disturb one.
 */
internal class FakeClientHarness : AutoCloseable {

    /** The address fake players claim to live at, and where replies are delivered. */
    val address: InetAddress = InetAddress.getLoopbackAddress()

    /** The one socket this fake client sends its `Iam` from and reads every reply on.
     *  Its local port is the handshake origin the server keys this client by. */
    private val socket = DatagramSocket()

    /**
     * Performs a full `Iam` handshake for [name] and confirms the server replied `REGISTERED`.
     *
     * Note that a successful result only proves the server replied - it is sent before the
     * server admits the player, so callers that care about the roster must wait for it
     * separately via [ServerFixture.awaitPlayers].
     *
     * @param name the player name to handshake under
     * @param timeoutMillis how long to wait for the reply
     * @return [Result.success] if the server replied `REGISTERED`, or the failure that prevented it
     */
    fun handshake(name: String, timeoutMillis: Int = REPLY_TIMEOUT_MILLIS): Result<Unit> {
        log.info("Fake client '{}' is handshaking", name)
        return send("$HANDSHAKE_VERB $name")
            .andThen { receive(timeoutMillis) }
            .andThen(::parseReply)
    }

    /**
     * Reads the next datagram addressed back to this harness's socket - a handshake reply, a
     * pushed message, or a broadcast.
     * @param timeoutMillis how long to wait before giving up
     * @return the decoded message, or the failure that prevented reading one
     */
    fun receive(timeoutMillis: Int = REPLY_TIMEOUT_MILLIS): Result<String> = runCatching {
        socket.soTimeout = timeoutMillis
        val packet = DatagramPacket(ByteArray(BUFFER_BYTES), BUFFER_BYTES)
        socket.receive(packet)
        String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
            .also { reply -> log.debug("Fake client received '{}'", reply) }
    }

    /**
     * Sends a datagram to the server's common port from this harness's socket - the same
     * socket every reply, broadcast, and push to this client arrives on.
     * @param message the text to send
     * @return [Result.success] if the datagram was sent, or the failure that prevented it
     */
    fun send(message: String): Result<Unit> = runCatching {
        message.toByteArray(Charsets.UTF_8).let { payload ->
            socket.send(
                DatagramPacket(payload, payload.size, address, MultiConnectionUDPServer.COMMON_LISTEN_PORT)
            )
        }
    }

    /**
     * Sends a bare `KA` keepalive datagram from this harness's socket, one-shot - mirrors
     * [com.spartanlabs.webtools.Connection.keepAlive]'s own one-shot contract.
     * @return [Result.success] if the datagram was sent, or the failure that prevented it
     */
    fun sendKeepAlive(): Result<Unit> = send(KEEPALIVE_TOKEN)

    /**
     * Confirms a handshake reply was the bare token `REGISTERED`.
     * @param reply the raw reply text
     * @return [Result.success], or [Result.failure] if the reply was not `REGISTERED`
     */
    private fun parseReply(reply: String): Result<Unit> = runCatching {
        require(reply == HANDSHAKE_REPLY_VERB) { "Expected '$HANDSHAKE_REPLY_VERB' but got '$reply'" }
    }

    /** Releases the socket so the next test can bind it. */
    override fun close() {
        socket.close()
    }

    companion object {
        /** Shared slf4j logger for all [FakeClientHarness] instances. */
        private val log = LoggerFactory.getLogger(FakeClientHarness::class.java)

        /** The verb that opens a client handshake. */
        private const val HANDSHAKE_VERB = "Iam"

        /** The bare token the server answers a handshake with. */
        private const val HANDSHAKE_REPLY_VERB = "REGISTERED"

        /** The bare token a client sends to keep its NAT mapping warm. */
        private const val KEEPALIVE_TOKEN = "KA"

        /** How long to wait for a handshake reply before declaring the handshake failed. */
        private const val REPLY_TIMEOUT_MILLIS = 4000

        /** Size of the buffer incoming datagrams are read into. */
        private const val BUFFER_BYTES = 8192
    }
}
