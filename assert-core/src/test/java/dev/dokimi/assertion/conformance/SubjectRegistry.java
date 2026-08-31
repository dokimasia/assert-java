package dev.dokimi.assertion.conformance;

import dev.dokimi.assertion.Check;
import dev.dokimi.assertion.Recorder;
import dev.dokimi.assertion.Seat;
import dev.dokimi.assertion.Soft;
import dev.dokimi.assertion.matcher.Behaviour;
import dev.dokimi.assertion.matcher.Raises;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/// The behaviours a corpus case can name in place of a callable.
///
/// A case states its arguments as typed literals, which cannot describe a
/// callable. The assertions taking one are handed a named behaviour from a small
/// fixed set instead, and this builds each one natively.
@NullMarked
public final class SubjectRegistry {

  private SubjectRegistry() {}

  /// What a subject raises when it fails on its own terms.
  public static final class SubjectException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /// Raise with the stated reason.
    ///
    /// @param reason why the subject failed
    public SubjectException(String reason) {
      super(reason);
    }
  }

  /// One built behaviour, in every shape an assertion asks for.
  ///
  /// @param cancellable the shape the cancellation assertions take
  /// @param handled the shape nullHandleSafe takes
  /// @param body the shape throwsException, doesNotThrow and isPure take
  /// @param seated the shape eventually takes
  /// @param observe what isPure compares across the call
  public record Subject(
      Behaviour.Cancellable cancellable,
      Behaviour.Handled handled,
      Raises.Body body,
      Consumer<Seat> seated,
      Callable<@Nullable Object> observe) {}

  /// Build the named behaviour, or null when this language cannot.
  ///
  /// @param kind the subject kind the case names
  /// @return the behaviour, or null
  public static @Nullable Subject build(String kind) {
    return switch (kind) {
      case "returns-ok", "ignores-handle" -> returnsOk();
      case "reads-handle" -> readsHandle();
      case "raises" -> raises();
      case "fails-otherwise" -> failsOtherwise();
      case "dereferences-handle" -> dereferencesHandle();
      case "never-settles" -> neverSettles();
      case "settles-after" -> settlesAfter();
      case "accumulates" -> observed(true);
      case "leaves-state-alone" -> observed(false);
      default -> null;
    };
  }

  /// A subject that does the work and answers success.
  private static Subject returnsOk() {
    return new Subject(handle -> {}, handle -> {}, () -> {}, seat -> {}, ArrayList::new);
  }

  /// A subject that answers whatever the handle says.
  private static Subject readsHandle() {
    Behaviour.Cancellable reads =
        handle -> {
          if (Boolean.TRUE.equals(handle.get())) {
            throw new InterruptedException("the handle said to stop");
          }
        };
    Subject base = returnsOk();
    return new Subject(reads, base.handled(), base.body(), base.seated(), base.observe());
  }

  /// A subject that raises rather than answering.
  private static Subject raises() {
    Subject base = returnsOk();
    Raises.Body raising =
        () -> {
          throw new SubjectException("the subject raised");
        };
    return new Subject(
        handle -> raising.run(), base.handled(), raising, base.seated(), base.observe());
  }

  /// A subject that fails for a reason of its own.
  private static Subject failsOtherwise() {
    Subject base = returnsOk();
    return new Subject(
        handle -> {
          throw new SubjectException("the subject failed for its own reason");
        },
        base.handled(),
        base.body(),
        base.seated(),
        base.observe());
  }

  /// A subject that reads a handle without checking it is there.
  private static Subject dereferencesHandle() {
    Subject base = returnsOk();
    return new Subject(
        base.cancellable(),
        // Reading an absent handle is the behaviour under test: the
        // assertion asks whether a subject survives one.
        handle -> {
          Supplier<Boolean> held = handle;
          held.get();
        },
        base.body(),
        base.seated(),
        base.observe());
  }

  /// A subject that reports a failure on every attempt.
  private static Subject neverSettles() {
    Subject base = returnsOk();
    return new Subject(
        base.cancellable(),
        base.handled(),
        base.body(),
        seat -> seat.record("never settles"),
        base.observe());
  }

  /// A subject that reports a failure twice, then succeeds.
  ///
  /// The count is per subject, so two cases cannot see each other's attempts.
  private static Subject settlesAfter() {
    AtomicInteger attempts = new AtomicInteger();
    Subject base = returnsOk();
    return new Subject(
        base.cancellable(),
        base.handled(),
        base.body(),
        seat -> {
          if (attempts.incrementAndGet() < 3) {
            seat.record("not yet");
          }
        },
        base.observe());
  }

  /// A subject with state something outside it can read.
  ///
  /// @param changes whether a call appends to the state
  private static Subject observed(boolean changes) {
    List<Integer> held = new ArrayList<>(List.of(1, 2));
    Subject base = returnsOk();
    return new Subject(
        base.cancellable(),
        base.handled(),
        () -> {
          if (changes) {
            held.add(held.size());
          }
        },
        base.seated(),
        // A copy, so the projection does not share memory with the
        // subject and read the same value twice.
        () -> List.copyOf(held));
  }

  /// How long a retrying assertion is given, against a controlled clock.
  private static final Duration RETRY_TIMEOUT = Duration.ofHours(1);

  /// How long it waits between attempts on that clock.
  private static final Duration RETRY_INTERVAL = Duration.ofMinutes(1);

  /// Drive one subject case on the aborting surface, answering whether it ran.
  ///
  /// The assertions taking a callable differ in shape, so each says how it is
  /// called here rather than the corpus runner knowing all of them.
  ///
  /// @param assertion the canonical id under test
  /// @param held the behaviour the case named
  /// @param seat where the assertion reports
  /// @param msg the contract under test
  /// @return true when the case ran, false when this language drives no such
  ///     assertion
  public static boolean runCheck(String assertion, Subject held, Seat seat, String msg) {
    switch (assertion) {
      case "throws" -> Check.throwsException(seat, held.body(), msg);
      case "not-throws" -> Check.doesNotThrow(seat, held.body(), msg);
      case "honours-cancellation" -> Check.honoursCancellation(seat, held.cancellable(), msg);
      case "honours-deadline" -> Check.honoursDeadline(seat, held.cancellable(), msg);
      case "nil-context-safe" -> Check.nullHandleSafe(seat, held.handled(), msg);
      case "pure" -> Check.isPure(seat, held.observe(), held.body(), msg);
      case "eventually" ->
          Check.eventually(seat, RETRY_TIMEOUT, RETRY_INTERVAL, held.seated(), msg);
      case "eventually-true" ->
          Check.eventuallyTrue(seat, RETRY_TIMEOUT, flips(held), msg);
      default -> {
        return false;
      }
    }
    return true;
  }

  /// Drive one subject case on the recording surface.
  ///
  /// @param assertion the canonical id under test
  /// @param held the behaviour the case named
  /// @param seat where the assertion reports
  /// @param msg the contract under test
  /// @return true when the case ran
  public static boolean runSoft(String assertion, Subject held, Seat seat, String msg) {
    switch (assertion) {
      case "throws" -> Soft.throwsException(seat, held.body(), msg);
      case "not-throws" -> Soft.doesNotThrow(seat, held.body(), msg);
      case "honours-cancellation" -> Soft.honoursCancellation(seat, held.cancellable(), msg);
      case "honours-deadline" -> Soft.honoursDeadline(seat, held.cancellable(), msg);
      case "nil-context-safe" -> Soft.nullHandleSafe(seat, held.handled(), msg);
      case "pure" -> Soft.isPure(seat, held.observe(), held.body(), msg);
      case "eventually" ->
          Soft.eventually(seat, RETRY_TIMEOUT, RETRY_INTERVAL, held.seated(), msg);
      case "eventually-true" -> Soft.eventuallyTrue(seat, RETRY_TIMEOUT, flips(held), msg);
      default -> {
        return false;
      }
    }
    return true;
  }

  /// A predicate that reads the subject's seated shape, so one behaviour serves
  /// both retrying assertions.
  ///
  /// @param held the behaviour the case named
  /// @return the predicate
  private static BooleanSupplier flips(Subject held) {
    return () -> {
      Recorder trial = new Recorder();
      held.seated().accept(trial);
      return !trial.failed();
    };
  }
}
