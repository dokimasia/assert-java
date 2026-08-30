package dev.dokimi.assertion.kotlin

import dev.dokimi.assertion.Recorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The assertions built on coroutines rather than translated from Java.
 *
 * Every one is driven twice, once with a subject that holds and once with one that does
 * not. A one-sided test passes against an assertion that reports nothing whatever it is
 * given, which is a real way for one of these to be wrong.
 */
class CoroutinesTest {

    @Test
    fun `honoursCancellation passes a subject that suspends`() = runTest {
        val seat = Recorder()
        Check.honoursCancellation(seat, "it stops when told") { awaitCancellation() }

        assertFalse(seat.failed(), seat.message())
    }

    @Test
    fun `honoursCancellation reports a subject that never suspends`() = runTest {
        val seat = Recorder()
        Check.honoursCancellation(seat, "it stops when told") { }

        assertTrue(seat.failed())
        assertContains(seat.message(), "ran to completion")
    }

    @Test
    fun `honoursDeadline passes a subject that suspends`() = runTest {
        val seat = Recorder()
        Check.honoursDeadline(seat, "it respects its deadline") { delay(10.seconds) }

        assertFalse(seat.failed(), seat.message())
    }

    @Test
    fun `honoursDeadline reports a subject that never suspends`() = runTest {
        val seat = Recorder()
        Check.honoursDeadline(seat, "it respects its deadline") { }

        assertTrue(seat.failed())
    }

    @Test
    fun `completesWithin passes a fast subject`() = runTest {
        val seat = Recorder()
        Check.completesWithin(seat, 1.seconds, "it is quick") { }

        assertFalse(seat.failed(), seat.message())
    }

    @Test
    fun `completesWithin reports a slow subject`() = runTest {
        val seat = Recorder()
        Check.completesWithin(seat, 1.milliseconds, "it is quick") { delay(10.seconds) }

        assertTrue(seat.failed())
        assertContains(seat.message(), "did not finish within")
    }

    @Test
    fun `eventually reports the last attempt's own reason`() = runTest {
        val seat = Recorder()
        Check.eventually(seat, 20.milliseconds, 5.milliseconds, "it converges") { trial ->
            trial.fail("the inner reason")
        }

        assertTrue(seat.failed())
        assertContains(seat.message(), "the inner reason")
    }

    @Test
    fun `eventually passes once the body settles`() = runTest {
        var attempts = 0
        val seat = Recorder()
        Check.eventually(seat, 1.seconds, 1.milliseconds, "it converges") { trial ->
            attempts++
            if (attempts < 3) trial.fail("not yet")
        }

        assertFalse(seat.failed(), seat.message())
        assertTrue(attempts >= 3)
    }

    @Test
    fun `eventuallyTrue reports the wait running out`() = runTest {
        val seat = Recorder()
        Check.eventuallyTrue(seat, 20.milliseconds, "it settles") { false }

        assertTrue(seat.failed())
        assertContains(seat.message(), "still false")
    }

    @Test
    fun `eventuallyTrue passes a predicate that holds`() = runTest {
        val seat = Recorder()
        Check.eventuallyTrue(seat, 1.seconds, "it settles") { true }

        assertFalse(seat.failed(), seat.message())
    }

    @Test
    fun `noTaskLeaks reports a coroutine left running`() = runTest {
        val seat = Recorder()
        Check.noTaskLeaks(seat, "the handler cleans up") { scope: CoroutineScope ->
            scope.launch { awaitCancellation() }
        }

        assertTrue(seat.failed())
        assertContains(seat.message(), "still running")
    }

    @Test
    fun `noTaskLeaks passes a scope that leaves nothing behind`() = runTest {
        val seat = Recorder()
        Check.noTaskLeaks(seat, "the handler cleans up") { scope: CoroutineScope ->
            scope.launch { }.join()
        }

        assertFalse(seat.failed(), seat.message())
    }
}
