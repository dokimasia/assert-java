package dev.dokimi.assertion.kotlin

import dev.dokimi.assertion.Seat
import dev.dokimi.assertion.matcher.Mode
import dev.dokimi.assertion.matcher.Report
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * The assertions a Java signature cannot reach.
 *
 * A Kotlin `suspend` lambda compiles to a method taking a hidden `Continuation`, so a
 * Java method cannot accept one. That is the whole reason this artifact exists: the
 * other thirty-four assertions are called straight from `dev.dokimi.assertion.Check`,
 * and only the concurrency-shaped ones need a Kotlin surface.
 *
 * Cancellation here is a coroutine's own: a `Job` cancelled, and `CancellationException`
 * raised at the next suspension point. That is what Kotlin code responds to, where Java
 * code responds to `Thread.interrupt`.
 */
public object Check {

    /** Every failure on this surface stops the test. */
    private val MODE = Mode.FATAL

    /**
     * Fail when a cancelled subject does not stop.
     *
     * The subject is launched and cancelled at once, so this asks whether it yields to
     * cancellation at all rather than how quickly it notices. A subject that never
     * suspends, or that swallows the [CancellationException] and returns, fails here.
     *
     * @param seat where the failure is reported
     * @param msg the contract under test
     * @param body the work under test
     */
    public suspend fun honoursCancellation(
        seat: Seat,
        msg: String,
        body: suspend () -> Unit,
    ) {
        seat.helper()
        var stopped = false
        var finished = false

        coroutineScope {
            // UNDISPATCHED, so the body runs up to its first suspension
            // point before anything cancels it. Cancelling a coroutine
            // that has not started yet cancels it without ever running
            // it, and counting that as honouring cancellation would
            // pass every subject there is.
            val job: Job = launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    body()
                    finished = true
                } catch (cancelled: CancellationException) {
                    stopped = true
                    throw cancelled
                }
            }
            job.cancel()
            job.join()
        }

        if (finished || !stopped) {
            Report.to(seat, MODE, "$msg: a cancelled subject ran to completion")
        }
    }

    /**
     * Fail when a subject given no time does not stop.
     *
     * The subject runs under a timeout that has already expired, so one that suspends at
     * all is cut short. One that never suspends fails, which is the case worth catching.
     *
     * @param seat where the failure is reported
     * @param msg the contract under test
     * @param body the work under test
     */
    public suspend fun honoursDeadline(
        seat: Seat,
        msg: String,
        body: suspend () -> Unit,
    ) {
        seat.helper()
        // Not Duration.ZERO: withTimeout with a zero deadline throws
        // before the block runs at all, so every subject would look
        // cut short. A deadline this small still expires at the
        // subject's first suspension point, and lets one that never
        // suspends run to completion, which is the case worth catching.
        val finished = withTimeoutOrNull(1.milliseconds) {
            body()
            true
        }

        if (finished == true) {
            Report.to(seat, MODE, "$msg: a subject given no time ran to completion")
        }
    }

    /**
     * Fail when the body takes longer than the given duration.
     *
     * The body is interrupted rather than measured: a coroutine has a cancellation
     * mechanism the JVM's thread model does not, so waiting for a slow subject to finish
     * would spend time for nothing.
     *
     * @param seat where the failure is reported
     * @param within the ceiling
     * @param msg the contract under test
     * @param body the work under test
     */
    public suspend fun completesWithin(
        seat: Seat,
        within: Duration,
        msg: String,
        body: suspend () -> Unit,
    ) {
        seat.helper()
        val started = TimeSource.Monotonic.markNow()
        try {
            withTimeout(within) { body() }
        } catch (expired: TimeoutCancellationException) {
            Report.to(
                seat,
                MODE,
                "$msg: did not finish within $within (gave up after ${started.elapsedNow()})",
            )
        }
    }

    /**
     * Fail when a body of assertions never passes within the timeout.
     *
     * The body is handed a seat of its own, so assertions inside it record an attempt
     * rather than ending the test. It runs at least once however short the timeout, and
     * the failure carries the last attempt's own reason.
     *
     * @param seat where the failure is reported
     * @param timeout how long to keep retrying
     * @param interval how long to wait between attempts
     * @param msg the contract under test
     * @param body states the condition as assertions, against the seat it is handed
     */
    public suspend fun eventually(
        seat: Seat,
        timeout: Duration,
        interval: Duration,
        msg: String,
        body: suspend (Seat) -> Unit,
    ) {
        seat.helper()
        val started = TimeSource.Monotonic.markNow()

        var attempt = 0
        while (true) {
            attempt++
            val trial = Trial()
            body(trial)

            val failure = trial.failure ?: return
            if (started.elapsedNow() > timeout) {
                Report.to(
                    seat,
                    MODE,
                    "$msg: still failing after $timeout and $attempt attempts: $failure",
                )
                return
            }
            delay(interval)
        }
    }

    /**
     * Fail when a predicate never becomes true within the timeout.
     *
     * Retried with a backoff that starts at a millisecond and doubles, capped at a
     * quarter of the timeout. A predicate carries no reason, so the failure says only
     * that the wait ran out.
     *
     * @param seat where the failure is reported
     * @param timeout how long to keep retrying
     * @param msg the contract under test
     * @param predicate must eventually answer true
     */
    public suspend fun eventuallyTrue(
        seat: Seat,
        timeout: Duration,
        msg: String,
        predicate: suspend () -> Boolean,
    ) {
        seat.helper()
        val started = TimeSource.Monotonic.markNow()
        var backoff = 1.milliseconds
        val cap = timeout / 4

        var attempt = 0
        while (true) {
            attempt++
            if (predicate()) return

            if (started.elapsedNow() > timeout) {
                Report.to(seat, MODE, "$msg: still false after $timeout and $attempt attempts")
                return
            }
            delay(backoff)
            backoff = minOf(backoff * 2, if (cap > Duration.ZERO) cap else backoff * 2)
        }
    }

    /**
     * Fail when a coroutine launched in the scope outlives it.
     *
     * A coroutine is tracked through its [Job], which is why this is here rather than
     * inherited from the Java artifact: `Thread.getAllStackTraces` never sees one.
     *
     * A leaked virtual thread is still not reported. Virtual threads appear in no
     * standard enumeration on any JVM version, which the standard's overlay records as a
     * limit.
     *
     * @param seat where the failure is reported
     * @param msg the contract under test
     * @param body the scope under test, given a scope to launch into
     */
    public suspend fun noTaskLeaks(
        seat: Seat,
        msg: String,
        body: suspend (CoroutineScope) -> Unit,
    ) {
        seat.helper()
        coroutineScope {
            val watched = Job(coroutineContext[Job])
            body(CoroutineScope(coroutineContext + watched))

            val running = watched.children.count { it.isActive }
            watched.cancel()

            if (running > 0) {
                Report.to(seat, MODE, "$msg: still running: $running coroutine(s)")
            }
        }
    }

    /** A seat that keeps one trial's failure instead of reporting it. */
    private class Trial : Seat {
        var failure: String? = null

        override fun fail(message: String) {
            if (failure == null) failure = message
        }

        override fun record(message: String) {
            fail(message)
        }
    }
}
