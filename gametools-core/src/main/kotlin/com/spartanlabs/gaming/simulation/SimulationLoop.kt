package com.spartanlabs.gaming.simulation

//region 1. Organization Internal
// 1.2 Spartan Gaming
import com.spartanlabs.gaming.gameobjects.World
//endregion

//region 3. Utility / Catch-all
// 3.1 Java Standard library
import java.util.concurrent.locks.LockSupport
//endregion

//region 4. Programming Infrastructure and Support
// 4.1 Logging
import org.slf4j.Logger
import org.slf4j.LoggerFactory
//endregion

/** Shared slf4j logger for the simulation-loop layer. */
private val log: Logger = LoggerFactory.getLogger("com.spartanlabs.gaming.simulation.SimulationLoop")

/**
 * An **opt-in** fixed-timestep driver for a [World].
 *
 * A [World] never runs itself - something has to call [World.tick]. This is a convenience for
 * the common case: [start] spawns one daemon thread that calls [World.tick] at a steady
 * [LoopSettings.tickRateHz], catching up a bounded number of ticks after a stall and parking
 * the remainder of each frame. Nothing in the engine depends on it - [World.tick] stays
 * callable directly, and a caller who wants to drive the timestep from their own loop can call
 * [advance] instead of using the thread at all.
 *
 * Everything runs on the loop thread: [World.tick] and the [onTick] callback both execute
 * there, so - exactly as bare [World.tick] already requires - the world must not be mutated
 * from another thread while the loop is running.
 *
 * @param world the world to advance
 * @param settings the live-tunable pacing settings; the same instance can be read and written
 *   from any thread while the loop runs
 * @param onTick invoked on the loop thread after each [World.tick], with the new [World.tickCount]
 */
class SimulationLoop(
    private val world: World,
    val settings: LoopSettings = LoopSettings(),
    private val onTick: (tickCount: Long) -> Unit = {},
) {

    /** Nanoseconds of simulated time owed but not yet stepped. Only touched on the loop thread (or by a direct [advance] caller). */
    private var accumulatorNanos: Long = 0L

    /** The driver thread while [isRunning]; `null` otherwise. */
    @Volatile
    private var thread: Thread? = null

    /** Whether [start] has been called and [stop] has not. */
    val isRunning: Boolean get() = thread != null

    /**
     * Starts the driver thread.
     *
     * @return [Result.success] once the thread is running, or [Result.failure] if the loop was
     * already running
     */
    fun start(): Result<Unit> {
        if (isRunning) return Result.failure(IllegalStateException("the simulation loop is already running"))
        accumulatorNanos = 0L
        thread = Thread(::run, "gametools-sim-loop").apply {
            isDaemon = true
            start()
        }
        log.info("Simulation loop started at {} Hz", settings.tickRateHz)
        return Result.success(Unit)
    }

    /**
     * Stops the driver thread and waits for it to finish. Idempotent - calling it when the
     * loop is not running is a success.
     *
     * @return [Result.success] once the thread has stopped
     */
    fun stop(): Result<Unit> {
        val running = thread ?: return Result.success(Unit)
        thread = null
        LockSupport.unpark(running)
        return runCatching {
            running.join(STOP_JOIN_TIMEOUT_MILLIS)
            log.info("Simulation loop stopped")
        }
    }

    /** The driver loop: measure real elapsed time, [advance] by it, then park until the next tick is due. */
    private fun run() {
        var last = System.nanoTime()
        while (thread != null) {
            val now = System.nanoTime()
            advance(now - last)
            last = now
            LockSupport.parkNanos(nanosPerTick() / 2)
        }
    }

    /**
     * Advances the world by however many whole ticks [realElapsedNanos] of real time has made
     * due, up to [LoopSettings.maxCatchUpTicks] in one call (extra owed time past that cap is
     * discarded, so a long pause cannot trigger an unbounded burst of ticks - the
     * "spiral of death" guard).
     *
     * Public so a caller can drive the timestep from their own loop instead of [start]ing the
     * thread. Not safe to call concurrently with the running loop thread or with itself.
     *
     * @param realElapsedNanos real nanoseconds elapsed since the last advance; negative is treated as zero
     */
    fun advance(realElapsedNanos: Long) {
        accumulatorNanos += realElapsedNanos.coerceAtLeast(0L)
        val nanosPerTick = nanosPerTick()
        var stepped = 0
        val cap = settings.maxCatchUpTicks.coerceAtLeast(1)
        while (accumulatorNanos >= nanosPerTick && stepped < cap) {
            world.tick()
            onTick(world.tickCount)
            accumulatorNanos -= nanosPerTick
            stepped++
        }
        if (accumulatorNanos >= nanosPerTick) {
            log.debug("Simulation loop hit its {}-tick catch-up cap; dropping {} ns of owed time", cap, accumulatorNanos)
            accumulatorNanos = 0L
        }
    }

    /** Nanoseconds between ticks at the current [LoopSettings.tickRateHz]. */
    private fun nanosPerTick(): Long = (NANOS_PER_SECOND / settings.tickRateHz).toLong().coerceAtLeast(1L)

    private companion object {
        const val NANOS_PER_SECOND: Double = 1_000_000_000.0

        /** How long [stop] waits for the driver thread to notice and exit. */
        const val STOP_JOIN_TIMEOUT_MILLIS: Long = 1_000L
    }
}

/**
 * The pacing knobs for a [SimulationLoop], safe to read and write from any thread while the
 * loop runs - a change takes effect from the next frame.
 *
 * @param tickRateHz initial ticks per second; must be positive
 * @param maxCatchUpTicks initial cap on ticks run in one [SimulationLoop.advance]
 */
class LoopSettings(tickRateHz: Double = DEFAULT_TICK_RATE_HZ, maxCatchUpTicks: Int = DEFAULT_MAX_CATCH_UP_TICKS) {

    /** Ticks per second the loop aims for. Assigning a non-positive value is rejected. */
    @Volatile
    var tickRateHz: Double = validRate(tickRateHz)
        set(value) {
            field = validRate(value)
        }

    /**
     * The most [World.tick]s [SimulationLoop.advance] will run in a single call, so a long
     * pause is absorbed rather than replayed as a burst. Coerced to at least `1`.
     */
    @Volatile
    var maxCatchUpTicks: Int = maxCatchUpTicks.coerceAtLeast(1)
        set(value) {
            field = value.coerceAtLeast(1)
        }

    private fun validRate(value: Double): Double {
        require(value > 0.0 && value.isFinite()) { "tickRateHz must be a positive, finite number, was $value" }
        return value
    }

    private companion object {
        const val DEFAULT_TICK_RATE_HZ: Double = 20.0
        const val DEFAULT_MAX_CATCH_UP_TICKS: Int = 5
    }
}
