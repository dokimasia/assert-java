---
rfc: 0003
title: The foundation of definition 16.4.0 in Java and Kotlin
author: Roy Klopper <roy.klopper@stealthscale.io>
status: Accepted
created: 2026-11-11
updated: 2026-11-11
discussion: none
supersedes: RFC-0002, in part
superseded-by: none
produces-adr: none
---

# RFC-0003: The foundation of definition 16.4.0 in Java and Kotlin

## Summary

The foundation is the code that every subsystem of the library calls: JSON,
typed literals, call records, verdicts, the failure record, the seats and
the relations. This proposal designs it at definition 16.4.0. RFC-0004 to
RFC-0007 design the property engine, the histories, the machines and the
trees of files on top of it.

RFC-0002 proposed the same parts at definition 8.0.0. This proposal takes
over its sections on JSON, equality, the failure record, call records, the
seat, relations and the bench contract, and adds the rows of definitions
9.0.0 to 16.4.0:

| Change | Java API |
|---|---|
| A seat receives a fault as an error | `FaultReporter.reportFault`, `Check.fault` and `Recorder.faults` |
| A seat registers a function that runs when its test ends | `Seat.cleanup` and `Recorder.close` |
| A helper skips its test | `Skipper.skip` and `Check.skip` |
| A seat observes each assertion call | `Observer.observeCall` and `AssertionCall` |
| A test writes a record of another tool | `Check.writeRecord` |
| A test reads whether its run checks allocation ceilings | `Check.allocsChecked` |
| A check compares a type by a transform | `Option.equateBy` |
| A call ceiling on the live heap | `Check.maxLiveBytes`, with the limit of the overlay |

## Motivation

CI has failed since the commit that vendored definition 15.0.0. The test
`DefinitionTest` checks that every surface id of the naming table is
offered or declined, and it fails on each id that the library lacks, from
`action` onwards. The naming table of 16.4.0 has 323 rows and 320 Java
names. The library implements the assertions of definition 1.1.0.

The foundation comes first, at the version that the subsystems target,
because each subsystem reports through it. A property records each call of
its cases, a history check states its detail as typed literals, and a tree
of files compares its entries as `equal` compares values.

## Detailed design

### Components

| Component | Package | Responsibility |
|---|---|---|
| JSON | `dev.dokimi.assertion.json` | Reads and writes JSON for the records, the store and the shape files |
| Typed literals | `dev.dokimi.assertion.literal` | Encodes a value as a typed literal of the definition, and decodes one |
| Call records | `dev.dokimi.assertion.record` | Numbers the calls, writes each record, reads `DOKIMI_ASSERT_RECORD` once per process, and writes the records of other tools |
| Verdicts | `dev.dokimi.assertion.matcher` | Reports a pass, a failure, a fault and a skip, and writes the call record of each |
| Failure record | `dev.dokimi.assertion` | `Failure`, with its accessors |
| Seats | `dev.dokimi.assertion`, `dev.dokimi.assertion.junit` | `Seat` and its interfaces, `Recorder`, `Collector`, `Standard` and the JUnit extension |
| Portable patterns | `dev.dokimi.assertion.pattern` | Parses the portable subset for `matches` and `Prop.stringMatching` |
| Relations | `Check`, `Soft`, `dev.dokimi.assertion.matcher` | The 14 relations on both surfaces |
| Conformance | the test sources | The corpus driver and the completeness gate |

The JSON module, the typed literals, the call records and the patterns
follow the sections of RFC-0002 without a change. `assert-core` keeps no
runtime dependency.

### The seat and its interfaces

```java
public interface Seat {
  default void helper() {}
  void fail(String message);
  void record(String message);
  default Supplier<Boolean> cancelled() { return () -> false; }
  default void cleanup(Runnable cleanup) { /* ends the call with a fault */ }
}

public interface Reporter { void report(Failure failure, boolean aborting); }
public interface Clocked { Clock clock(); }
public interface FaultReporter { void reportFault(Throwable fault, boolean ending); }
public interface Skipper { void skip(String reason); }
public interface Observer { void observeCall(AssertionCall call); }

public record AssertionCall(String assertion, String contract) {}
```

- The naming table states `report`, `clock`, `reportFault`, `skip` and
  `observeCall` on interfaces of their own for Java. A seat implements the
  interfaces of the members that it supports. The library checks each
  interface with `instanceof` where it calls the member.
- `Seat.cleanup` has a default that ends the call with the fault of the
  definition, so a helper calls the member on any seat. The naming table
  declines the helper `cleanup` for Java for that reason.
- `Check.skip(seat, reason)` calls `Skipper.skip` on a seat that implements
  it, and ends the call with a fault on any other seat.
- `Check.fault(seat, error)` ends the call of a helper with an error
  through `FaultReporter.reportFault(error, true)`, or through `fail` with
  the text of the error on a seat that is no `FaultReporter`.

### Collector, Standard and Recorder

| Seat | `Check` | `Soft` | Faults | Skips | Cleanups |
|---|---|---|---|---|---|
| `Collector` | throws | collects, and `flush` throws them | throws | throws the skip of the runner | runs them when `SeatExtension` ends the test |
| `Standard` | throws | throws | throws | throws the skip of the runner | ends the call with the fault of the default |
| `Recorder` | collects | collects | keeps them | keeps the reason | runs them at `close` |

- `Recorder` implements `AutoCloseable`, and `close` runs its cleanups,
  the last registered first. The naming table names `run-cleanups` `close`
  for Java, so a test writes `try (var seat = new Recorder())`.
- `Recorder` adds `failed`, `failures`, `faults`, `helperCalls`, `message`,
  `messages` and `records`.
- A skip on `Collector` and `Standard` throws
  `org.opentest4j.TestAbortedException` when JUnit is on the class path,
  so JUnit reports the test as skipped. Without JUnit it throws an
  `AssertionFailed` with the reason.
- `SeatExtension` implements every interface. It runs the cleanups in
  `afterTestExecution`, the last registered first, and publishes each call
  record as a report entry. A failure or an error in a cleanup fails the
  test, and the later cleanups still run.

### Records of other tools

`Check.writeRecord(seat, kind, json)` writes a record of a kind of another
tool into the artifact of the test, at the place that the overlay of Java
states for the kind. The JUnit extension publishes the record as a report
entry under the key of its kind, as it publishes a call record.

### Relations and options

```java
public final class Option {
  public static final Option EQUATE_EMPTY;
  public static final Option EQUATE_NANS;
  public static final Option BY_IDENTITY;
  public static <T> Option equateBy(Class<T> type, Function<? super T, ?> transform);
}
```

- `equateBy` compares a value of `type` by the result of `transform`, at
  every depth of a comparison. At most one transform applies at a place,
  and a later `equateBy` of one class replaces an earlier one.
- The 14 relations follow RFC-0002. `rejects` returns the failure records
  of its check, and a check that ends with a fault ends the call of
  `rejects` with that fault.

### The live heap of a call

`Check.maxLiveBytes` measures the heap that remains reachable after its
body. It reads `MemoryMXBean.getHeapMemoryUsage` after a call of
`System.gc`, which runs a full collection that has ended only under the
collectors that the overlay of Java names. The overlay states the limit.

### Version

Every call record states the version of the vendored copy. The library
vendors 16.4.0 at the start of the first milestone of the roadmap, so each
milestone runs the vectors of 16.4.0. The completeness gate passes at the
last milestone, once every row has an implementation or a declination.

### Invariants

- `assert-core` does not load a class outside the JDK at run time. JUnit
  and the coroutines runtime are `compileOnly` in `assert-core`.
- A call reports exactly one verdict: a pass, a failure, a fault or a skip.
- A call record states the definition version of the vendored copy.

### Failures

| Event | Result |
|---|---|
| `Seat.cleanup` on a seat that keeps no cleanups | The call ends with the fault of the definition |
| `Check.skip` on a seat that is no `Skipper` | The call ends with the fault of the definition |
| A JSON text deeper than its bound | `JsonException`, which the caller ends its call with |
| `DOKIMI_ASSERT_RECORD` states another value than `0` or `1` | The first call of the process ends with a fault |

## Alternatives considered

### A. Members with defaults on `Seat` in place of the interfaces

Java has default methods, so `report`, `clock`, `reportFault`, `skip` and
`observeCall` could be defaults of `Seat`.

**Why not:** the naming table names `Reporter`, `Clocked`, `FaultReporter`,
`Skipper` and `Observer` for Java, and the completeness gate checks the
names. A change would need a change of the definition first.

### B. Jackson at run time

**Why not:** RFC-0002 rejects it. Every consumer would inherit the version
conflicts of Jackson for a few hundred lines of JSON.

## Drawbacks

- A seat of a caller implements up to six interfaces to support every
  member.
- `Recorder` as an `AutoCloseable` makes a recorder without a
  try-with-resources leave its cleanups unrun.
- The skip of a JUnit test depends on a class of opentest4j, which the
  library loads by name.

## References

| What | Where |
|---|---|
| The definition, its corpus and the overlay of Java | <https://github.com/dokimasia/assert-spec> |
| Faults through the seat | <https://github.com/dokimasia/assert-spec/blob/main/docs/rfc/0033-faults-through-the-seat.md> |
| A seat that observes every call | <https://github.com/dokimasia/assert-spec/blob/main/docs/rfc/0044-a-seat-that-observes-every-call.md> |
| A ceiling on the live heap of a call | <https://github.com/dokimasia/assert-spec/blob/main/docs/rfc/0045-a-ceiling-on-the-live-heap-of-a-call.md> |
| Cleanups on a seat | <https://github.com/dokimasia/assert-spec/blob/main/docs/rfc/0047-cleanups-on-a-seat.md> |
| Writing a record of a kind | <https://github.com/dokimasia/assert-spec/blob/main/docs/rfc/0048-writing-a-record-of-a-kind.md> |
| Comparing a type by a transform | <https://github.com/dokimasia/assert-spec/blob/main/docs/rfc/0053-comparing-a-type-by-a-transform.md> |
| A helper that skips its test | <https://github.com/dokimasia/assert-spec/blob/main/docs/rfc/0058-a-helper-that-skips-its-test.md> |
| The JVM design of definition 8.0.0 | [RFC-0002](0002-definition-8-in-java-and-kotlin.md) |
