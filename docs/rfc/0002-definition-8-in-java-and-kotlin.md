---
rfc: 0002
title: Definition 8.0 in Java and Kotlin
author: Roy Klopper <roy.klopper@stealthscale.io>
status: Draft
created: 2026-10-11
updated: 2026-10-11
discussion: https://github.com/dokimasia/assert-java/issues/7
supersedes: none
superseded-by: none
produces-adr: none
---

# RFC-0002: Definition 8.0 in Java and Kotlin

## Summary

Both artifacts move from definition 1.1.0 to 8.0.0 in one step. They gain
the 68 assertions that the definition added since 1.1.0, with 194 Java
names and 191 Kotlin names:

- the property engine, with its generators, its property forms and its
  derived inputs
- the 14 relations and `Option.BY_IDENTITY`
- the history seam and its three checks
- machines and the task scheduler
- trees of files, the file assertions and `Golden.matchTree`
- call records, faults, and the accessors of the failure record
- `warmup` and `runParallel` on the bench contract

The definition fixes the names, the rules and the vectors. This proposal
decides only what the JVM must decide:

- **Artifacts.** `assert-core` contains everything a Java signature can
  express. `assert-kotlin` adds the Kotlin type reader, the coroutine
  dispatcher of the task scheduler, and the assertions with a suspending
  body.
- **JSON.** `assert-core` reads and writes JSON with a small module of its
  own, so the artifact still has no runtime dependency.
- **Records.** The JUnit extension publishes each call record as a report
  entry of the test, and Gradle writes it into the XML report.
- **Tasks.** The Java task scheduler is an `Executor` that runs one task at
  a time. The Kotlin one is a `CoroutineDispatcher`.
- **Constraints.** The type reader reads Jakarta Bean Validation
  annotations by their names, so `assert-core` does not depend on the
  Bean Validation API.
- **Files.** The file helpers use `java.nio.file`, and a
  `SecureDirectoryStream` where the platform has one.
- **Java.** The artifact still targets Java 17, and the build still needs
  JDK 23.
- **The definition.** Definition 8.0.0 adds limits for time zones and for
  `char` to both overlays. The library vendors 8.0.0.

## Motivation

The library vendors definition 1.1.0. It implements 40 of the
definition's 110 assertions, and its naming table has 85 of the 279 Java
names of 7.2.1. Its completeness gate fails on every row that it neither
implements nor declines, so the library cannot vendor a later version
until it implements the rest.

The shared vectors pin each addition: 430 property vectors, 80 history
vectors, 14 machine vectors and 57 file vectors. The corpus grew from 87
cases at 1.1.0 to 195 at 7.2.1. The library runs none of the vectors. The
Go and TypeScript libraries run them, so the JVM disagrees with both
wherever it has not caught up:

- `length` counts the UTF-16 units of a `String`, where the definition
  counts Unicode scalar values
  (`assert-core/src/main/java/dev/dokimi/assertion/matcher/Sizes.java`,
  line 25).
- `contains` looks a key up with `containsKey`, so a NaN needle finds a
  NaN key and the needle `0.0` does not find the key `-0.0`
  (`matcher/Containment.java`, line 42).
- `matches` compiles the pattern with `Pattern.compile`, so it accepts
  every pattern of `java.util.regex`, including a backreference
  (`matcher/Text.java`, lines 88 to 96).
- `rejects` returns the first message of the check, and the definition
  returns its failure records (`Check.java`, line 547).

## Detailed design

### Components

| Component | Packages | Responsibility |
|---|---|---|
| JSON | `dev.dokimi.assertion.json` | Reads and writes JSON: the records, the store and the shape files |
| Typed literals | `dev.dokimi.assertion.literal` | Encodes a value as the definition's typed literal |
| Call records | `dev.dokimi.assertion.record` | Numbers calls, writes each record, and reads `DOKIMI_ASSERT_RECORD` once per process |
| Verdicts | `dev.dokimi.assertion.matcher` | Reports a pass, a failure and a fault, and writes the call record of each |
| Failure record | `dev.dokimi.assertion` | `Failure`, with `want`, `got` and `caseFailure` |
| Seats | `dev.dokimi.assertion`, `dev.dokimi.assertion.junit` | `Seat.cancelled`, `Recorder.records`, `Recorder.withCancelled`, and the report entries of a test |
| Portable patterns | `dev.dokimi.assertion.pattern` | Parses the portable subset for `matches` and `Prop.stringMatching` |
| Relations | `Check`, `Soft`, `dev.dokimi.assertion.matcher` | The 14 relations on both surfaces |
| Property engine | `dev.dokimi.assertion.prop` | `Prop`, `Case`, `Generator`, the source, the decoder, the shrinker, coverage, tokens, the store, campaigns, shapes, the forms and the fuzz bridge |
| Histories | `dev.dokimi.assertion.history` | `History`, the seam, specs, `concurrently` and the three checks |
| Machines | `dev.dokimi.assertion.stateful` | `Stateful`, `Machine`, `Action` and the `Executor` scheduler |
| Files | `dev.dokimi.assertion.files`, `Golden` | `FileTree`, `FileTrees`, and `Golden.matchTree` |
| Bench | `dev.dokimi.assertion.bench` | `warmup` and `runParallel` |
| Kotlin | `dev.dokimi.assertion.kotlin` | The Kotlin type reader, the dispatcher scheduler, and the suspending assertions |
| Conformance | the test sources | A driver for each vector family, and the corpus driver for the 8.0.0 corpus |

### The definition: 8.0.0

Two parts of 7.2.1 need an overlay entry that neither overlay states. The
changes go to assert-spec first:

| Change | Where | Reason |
|---|---|---|
| A limit on `prop.of`: a zone resolves through the time-zone data of the JDK that runs the test, so a value in a zone can show another wall time than tzdata 2026e states. A `char` reads as a `char` shape within the Basic Multilingual Plane | `overlays/java.json`, `overlays/kotlin.json` | The JDK ships its own copy of the time-zone data, and a Java `char` is one UTF-16 unit |
| A limit on `prop.of-shape`, with the zone text of the `prop.of` entry | `overlays/java.json`, `overlays/kotlin.json` | The same reason |

Definition 8.0.0 also changes the count of `max-allocs`, an assertion that
both overlays decline. The vendored copy then comes from `main` of
assert-spec at 8.0.0.

### JSON in the artifact

The call records and the store need JSON at run time, and the JDK has no
JSON parser. `assert-core` gains a reader and a writer of its own:

```java
/// Reads one JSON value: an object as a `Map<String, Object>` in the order of
/// its keys, an array as a `List<Object>`, a number as a `BigDecimal`, and
/// `null` as null.
///
/// @throws JsonException when text is no JSON, or nests deeper than depth
public static @Nullable Object read(String text, int depth);

/// Writes value as JSON, with the keys of a map in its iteration order.
public static String write(@Nullable Object value);
```

- A number reads as a `BigDecimal`, so an integer beyond the range of
  `long` and a float keep their exact digits.
- The store reads with a depth of 64, as the definition bounds an entry.
- The tests compare the module with Jackson. Jackson is still a test
  dependency only.

### Equality, values and the corpus

The rules that definitions 2.0.0 to 7.1.0 added to the assertions that the
library already has:

- `length` counts the code points of a `String`. A string with a lone
  surrogate has no length, and fails as a value without a length fails.
- A key compares as `equal` compares. The library finds a key with its own
  comparison, because `HashMap` finds a NaN needle under a NaN key and
  does not find `0.0` under `-0.0`.
- `matches` parses the pattern with the parser of the portable subset and
  compiles the parsed pattern for `java.util.regex` with the meaning of
  the subset: `\z` for `$`, ASCII classes, and a class without the five
  line terminators for `.`.
- `isEmpty` states `got` and `length`. `isNotEmpty` states `got`.
- `closeTo` fails for two equal infinities, and `inRange` for a range with
  a NaN bound.
- `completesWithin` fails when the duration has passed, without waiting
  for the subject.
- `Option.BY_IDENTITY` compares a reference with `==`. Every object is a
  reference except a `String` and the box of a primitive.

### The failure record

```java
public record Failure(
    String assertion, String contract, Map<String, @Nullable Object> detail, @Nullable Where where) {

  /// The value of the field want of the detail.
  ///
  /// @throws NoSuchElementException when the assertion does not declare the field want
  public @Nullable Object want();

  /// The value of the field got of the detail.
  ///
  /// @throws NoSuchElementException when the assertion does not declare the field got
  public @Nullable Object got();

  /// The failure record of a failing case of a property.
  ///
  /// @throws NoSuchElementException when the detail does not contain the field failure
  public Failure caseFailure();
}
```

`rejects` returns the failure records of its check, in call order, on
both surfaces:

```java
public static List<Failure> rejects(Seat seat, String msg, Consumer<Recorder> body);
```

### Call records

`DOKIMI_ASSERT_RECORD=1` records every assertion call. The library reads
the variable once per process.

- `SeatExtension` publishes each record through
  `ExtensionContext.publishReportEntry("dokimi.assert.<seq>", json)` when
  the call reports its verdict. Gradle 9.4 and later write each entry as a
  `property` of the test's `testcase` element, as the overlay states.
- A `Recorder` keeps its records whatever the switch states, and
  `Recorder.records()` returns them as JSON lines.
- The encoder states a record as a `record` literal, an enum constant as
  a `variant`, and a value outside the typed literals as `opaque` with its
  `toString`.

### The seat

```java
public interface Seat {
  default void helper() {}
  void fail(String message);
  void record(String message);
  /// The cancellation handle of the test, and a supplier of false for a seat that
  /// states none.
  default Supplier<Boolean> cancelled() { return () -> false; }
}

public final class Recorder implements Seat, Reporter, Clocked {
  /// Makes cancelled the cancellation handle of this recorder, and returns it.
  public Recorder withCancelled(Supplier<Boolean> cancelled);

  /// The call records of the calls on this recorder, in the order of their seq,
  /// as JSON lines.
  public List<String> records();
}
```

`rejects` and `eventually` hand their body a recorder whose handle reads
`true` once the handle of the assertion's seat does, and once the body
ends.

### Relations

The 14 relations are on `Check` and `Soft`:

```java
public static <I> void isIdempotent(
    Seat seat, Consumer<I> call, I input, Supplier<?> observe, String msg, Option... options);

public static <T> void isPermutation(
    Seat seat, Collection<T> got, Collection<T> want, String msg, Option... options);
```

- A callable that throws fails the relation, unless the relation requires
  a failure, as `failsAfterClose` and `isPoisoned` do.
- `isPermutation` pairs elements through a maximum bipartite matching.

### The property engine

```java
public final class Prop {
  public static void forAll(Seat seat, String contract, Consumer<Case> body, Option... options);
  public static Consumer<byte[]> fuzz(Seat seat, String contract, Consumer<Case> body, Option... options);

  public static Generator<Long> integer(long lo, long hi);
  public static Generator<Double> floating(double lo, double hi, FloatOption... options);
  public static Generator<Boolean> bool(BooleanOption... options);
  public static <T> Generator<List<T>> list(Generator<T> of, ListOption... options);
  public static Generator<String> string(StringOption... options);
  public static Generator<Duration> duration(Duration lo, Duration hi);
  // and the other generators, options, forms and registrations of the naming table
}

public final class Case implements Seat {
  public <T> T draw(Generator<T> generator, String label);
  public void assume(boolean condition);
  public void classify(String label);
  public void note(String message);
  public RandomGenerator rand();
  public void observe(long fingerprint);
  public void cleanup(Runnable cleanup);
  public Supplier<Boolean> cancelled();
  public History history();
  public void target(String label, double score);
}
```

- `Prop.integer` has overloads for `int`, `long` and `BigInteger`, and the
  last covers the unsigned 64-bit range.
- The options of a generator are values with the names of Go's options:
  `Prop.minSize`, `Prop.maxSize`, `Prop.unique`, `Prop.allowNan`,
  `Prop.odds`, `Prop.alphabet` and `Prop.maxLeaves`.
- `Case.rand()` returns a `java.util.random.RandomGenerator` whose values
  are choices of the case, as the definition states for the JVM.
- A form reads the type of its input from a `Class` that the caller
  passes, or from `Prop.using`, because Java erases the type of a lambda's
  parameter.

#### Derived inputs

- `Prop.of(Class<T>)` reads records, enums, sealed interfaces and the
  types of the definition's table for Java through reflection. A record's
  components keep their declaration order.
- A plain class fails the read, because reflection does not promise the
  order of its fields.
- The reader reads the annotations of Jakarta Bean Validation by the name
  of their type, such as `jakarta.validation.constraints.Min`, and their
  values through reflection. `assert-core` does not depend on the API.
- `dev.dokimi.assertion.prop.Constraint` declares the annotations for the
  constraints that Bean Validation does not have: `@Unit`, `@Alphabet`,
  `@AllowNan`, `@AllowInfinity` and `@Version`.
- A record is built through its canonical constructor. A constructor that
  throws fails the case.

```java
record Line(
    @Pattern(regexp = "[A-Z]{3}-[0-9]{4}") String sku,
    @Min(1) @Max(99) int quantity,
    @Constraint.Unit("ms") Instant placed) {}
```

- `assert-kotlin` registers a second reader through `ServiceLoader`. It
  reads data classes in constructor order, sealed classes, `Pair` and
  `Triple` as records, `kotlin.time.Duration`, the kotlinx-datetime types
  and `Uuid`, through `kotlin-reflect`.

#### The rules of a run

- **Workers.** `Prop.workers(n)` runs cases on n platform threads, and a
  run reports what a run on one worker reports.
- **The end of a case.** `assume`, a repeated label and a fatal failure
  end the case with an `Error` of the engine, so `catch (Exception e)` in a
  body does not catch it. Every later call of the case throws it again.
- **Numbers.** The source is SFC64 on `long`, with unsigned arithmetic from
  `Long`. A 128-bit integer is a `BigInteger`.
- **Text.** A string generator works in code points and never produces a
  lone surrogate.
- **Dates.** A date or time shape decodes to the `java.time` type of its
  kind. Each of these types has a resolution of one nanosecond.
- **The store.** A run stores its failures under
  `src/test/resources/testdata/prop/<class>/<method>/`, relative to the
  project directory, beside the golden files.
- **Its report.** `Prop.forAll` aborts only, so it reports through `fail`.

`Prop.fuzz` replays the stored cases and the seed files on the seat, and
returns a target for Jazzer. The library does not depend on Jazzer. A
fuzz test passes the target the bytes of `FuzzedDataProvider`.

### Histories

```java
public final class History {
  public History();
  public Call invoke(int client, String operation, List<?> args, Object... keys);
  public List<Event> events();

  public static List<Outcome> concurrently(int clients, Duration within, IntConsumer body);
  public static <S> void isLinearizable(Seat seat, History history, Spec<S> spec, String msg, Option... options);
  public static void isSerializable(Seat seat, History history, String msg);
  public static void hasSnapshotIsolation(Seat seat, History history, String msg);
}
```

- `concurrently` runs one platform thread per client, releases them at
  once through a `CyclicBarrier`, and waits `within` on the platform
  clock.
- The overlay's limit on `history` covers the recorder.

### Machines and the task scheduler

```java
public final class Scheduler implements Executor {
  public Scheduler(Case c, Strategy strategy);
  public void spawn(Runnable task);
  public void yield();
  public void run();
  @Override public void execute(Runnable task);
}
```

- Each task runs on a platform thread of its own, and one task runs at a
  time. A task passes control back at `scheduler.yield()` or when it
  returns, and the strategy chooses the next ready task.
- `execute` spawns its task, so a subject that takes an `Executor` runs on
  the scheduler without a change.
- A Java caller writes `scheduler.yield()` with the qualifier, because an
  unqualified `yield()` does not compile since Java 14.
- `assert-kotlin` declares a Kotlin `Scheduler` that extends
  `CoroutineDispatcher`. A task is a coroutine on it, and
  `kotlinx.coroutines.yield()` ends its turn.

### Trees of files and golden trees

The path assertions take a `java.nio.file.Path`, and the tree is
`FileTree` with its class of helpers `FileTrees`.

- On a platform whose `newDirectoryStream` returns a
  `SecureDirectoryStream`, a write creates each entry relative to the
  open directory with `NOFOLLOW_LINKS`, so it does not follow a link out
  of the tree.
- On another platform, a write reads every parent with `NOFOLLOW_LINKS`
  before it creates an entry.
- A mode is set through `PosixFileAttributeView` after the file is
  written. Without that view, as on Windows, `hasMode` ends with a fault,
  and the conformance driver skips the vectors with a mode.
- `Golden.match` and `Golden.matchTree` resolve a name against
  `src/test/resources/testdata`, relative to the project directory, as
  `Golden.match` does today.

### Bench

```java
/// State iterations that `loop` and `measuring` run before the measured ones.
///
/// @throws IllegalArgumentException when iterations is negative
/// @throws IllegalStateException when the contract has already run its body
public Contract warmup(int iterations);

/// Run the body on the given number of threads, which run the given number of
/// iterations in all, and measure every iteration.
///
/// @throws IllegalArgumentException when workers is below 1 or iterations is negative
public Contract runParallel(int workers, int iterations, Raises.Body body);
```

### Dependencies

| Artifact | Dependency | Why |
|---|---|---|
| `assert-core` | none | JSON, the type reader and the scheduler use the JDK alone |
| `assert-kotlin` | `kotlinx-coroutines-core`, as today | The suspending assertions and the dispatcher scheduler |
| `assert-kotlin` | `kotlin-reflect` | The Kotlin type reader reads constructor order and sealed subclasses |

### Tests and CI

- **Drivers.** A driver for each vector family runs every vector of 8.0.0
  against both surfaces, and states its verdict with JUnit's assertions.
- **The completeness gate.** The gate checks every name of both columns
  and the parameter count of every method.
- **Coverage.** JaCoCo checks 100% of the lines and branches of both
  artifacts.
- **CI.** CI runs the tests on JDK 17 and 25 on Linux, and adds a job each
  on Windows and macOS.

### Bounds

- The search of `isLinearizable` stops at the budget of 10,000,000 steps
  per partition, at its memo limit and at its time limit.
- A property stops shrinking at 2,000 runs or 30 seconds.

## Alternatives considered

### A. Jackson at run time

`assert-core` would read and write JSON with Jackson.

**Why not:** every consumer would depend on Jackson and inherit its version
conflicts. The records and the store need a few hundred lines of JSON.

### B. Bean Validation as a dependency

`assert-core` would depend on the Jakarta Bean Validation API and read its
annotations as types.

**Why not:** a project that does not validate would depend on the API for
the constraints of a test. Reading the annotations by name reads the same
values.

### C. Virtual threads for the tasks

The scheduler would run each task on a virtual thread.

**Why not:** the artifact targets Java 17, which has no virtual threads.
One task runs at a time, so a platform thread per task costs only its
memory.

### D. jqwik as the engine

`Prop` would wrap jqwik.

**Why not:** the definition fixes the source, the decoding, the shrink
passes and the token, and jqwik has its own. It is also under the Eclipse
Public License 2.0, and the library is under MIT.

## Drawbacks

- **Size.** The TypeScript library implements definition 7.2.1 in 33,888
  source lines and 42,997 test lines, measured on 2026-10-11. The JVM work
  is likely of the same order, with the Kotlin reader beside it.
- **JSON.** The artifact contains a JSON module of its own.
- **Kotlin.** `assert-kotlin` gains `kotlin-reflect`, a large library.
- **Changes for callers.** `rejects` returns failure records, `length`
  counts code points, `matches` refuses patterns outside the portable
  subset, and a map finds a key as `equal` compares keys.
- **Mutation testing.** dokimasia has no mutation engine for the JVM, so
  coverage is the only measure of the tests.

## References

| What | Where |
|---|---|
| The definition, its corpus, its vectors and the overlay format | <https://github.com/dokimasia/assert-spec> |
| The table of type readers and the constraint table | assert-spec RFC-0011: <https://github.com/dokimasia/assert-spec/blob/main/docs/rfc/0011-property-forms-and-derived-inputs.md> |
| The overlays of Java and Kotlin at 7.2.1 | <https://github.com/dokimasia/assert-spec/blob/main/overlays/java.json>, <https://github.com/dokimasia/assert-spec/blob/main/overlays/kotlin.json> |
| The cancellation handle of a Java seat, and `withCancelled` | assert-spec RFC-0029: <https://github.com/dokimasia/assert-spec/blob/main/docs/rfc/0029-the-cancellation-handle-of-a-seat.md> |
| `SecureDirectoryStream` | <https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/SecureDirectoryStream.html> |
| The options of Go's generators | <https://github.com/dokimasia/assert-go/tree/main/prop> |
| The TypeScript implementation of the same definition, and its size | <https://github.com/dokimasia/assert-typescript/blob/main/docs/rfc/0003-definition-7-2-in-typescript.md>; `src` and `tests` counted with `wc -l` on 2026-10-11 |
