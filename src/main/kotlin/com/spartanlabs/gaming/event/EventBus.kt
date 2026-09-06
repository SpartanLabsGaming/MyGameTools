package com.spartanlabs.gaming.event

//region 4. Programming Infrastructure and Support
// 4.1 Logging
import org.slf4j.Logger
import org.slf4j.LoggerFactory
//endregion

/** Shared slf4j logger for the event layer. */
private val log: Logger = LoggerFactory.getLogger("com.spartanlabs.gaming.event")

/**
 * A synchronous, single-threaded publish/subscribe channel for [GameEvent]s.
 *
 * [publish] delivers an event to every current [Listener], in subscription order, on the
 * calling thread. A listener that throws is caught and logged - it never aborts the publish
 * or the caller (typically a [com.spartanlabs.gaming.gameobjects.World.tick]). A listener that
 * publishes another event during delivery does not recurse: the new event is queued and
 * delivered once the current one finishes, so ordering stays deterministic and the stack
 * stays flat.
 *
 * The bus does no synchronisation of its own; drive one [com.spartanlabs.gaming.gameobjects.World]
 * (and its bus) from a single thread, the way the tick loop already requires.
 */
class EventBus {

    /** A recipient of published [GameEvent]s. */
    fun interface Listener {
        /**
         * Handles one delivered event. Must return promptly - delivery to later listeners
         * waits on it - and must not throw for control flow (a throw is caught and logged).
         *
         * @param event the event being delivered
         */
        fun onEvent(event: GameEvent)
    }

    /** The handle [subscribe] returns; [cancel] stops further delivery to that listener. */
    fun interface Subscription {
        /** Unsubscribes the listener. Idempotent. */
        fun cancel()
    }

    /** Registered listeners, in subscription order. Copied before each delivery pass. */
    private val listeners: MutableList<Listener> = mutableListOf()

    /** Events published while a delivery pass is already running; drained by that pass. */
    private val queued: ArrayDeque<GameEvent> = ArrayDeque()

    /** `true` while [publish] is walking [listeners], so a re-entrant publish only enqueues. */
    private var delivering: Boolean = false

    /**
     * Registers [listener] to receive every event published from now on.
     *
     * @param listener the recipient
     * @return a [Subscription] whose [Subscription.cancel] removes [listener]
     */
    fun subscribe(listener: Listener): Subscription {
        listeners.add(listener)
        log.debug("Event listener subscribed; {} now registered", listeners.size)
        return Subscription {
            if (listeners.remove(listener)) log.debug("Event listener unsubscribed; {} left", listeners.size)
        }
    }

    /**
     * Delivers [event] to every subscribed [Listener] in order. If called while a delivery is
     * already in progress, [event] is queued and delivered after the current one.
     *
     * @param event the event to broadcast
     */
    fun publish(event: GameEvent) {
        queued.addLast(event)
        if (delivering) return

        delivering = true
        try {
            while (queued.isNotEmpty()) {
                val next = queued.removeFirst()
                listeners.toList().forEach { listener ->
                    runCatching { listener.onEvent(next) }
                        .onFailure { cause -> log.warn("A GameEvent listener threw while handling {}", next, cause) }
                }
            }
        } finally {
            delivering = false
        }
    }
}
