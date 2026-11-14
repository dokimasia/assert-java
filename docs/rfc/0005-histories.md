---
rfc: 0005
title: Histories of definition 16.5.0 in Java and Kotlin
author: Roy Klopper <roy.klopper@stealthscale.io>
status: Accepted
created: 2026-11-11
updated: 2026-11-14
discussion: none
supersedes: RFC-0002, in part
superseded-by: none
produces-adr: none
---

# RFC-0005: Histories of definition 16.5.0 in Java and Kotlin

## Summary

A test records the calls of concurrent clients in a `History`, and three
checks decide whether the history is linearizable, serializable or has
snapshot isolation. This proposal designs the history package of the JVM at
definition 16.5.0. It takes over the section "Histories" of RFC-0002 and
adds the rows and the rules of definitions 9.0.0 to 16.5.0:

| Change | Java API |
|---|---|
| The sequential specification of a check | `Spec`, `History.specFrom` |
| The limits of a search | `History.budget`, `History.memoLimit`, `History.timeLimit` and `History.workers` |
| A history from a log of timed calls | `History.fromIntervals` |
| Isolation checks over register histories | `History.isSerializable` and `History.hasSnapshotIsolation` |
| A configuration that the memo covers | the search of `History.isLinearizable` |
| A lost update | `History.isSerializable` and `History.hasSnapshotIsolation` |

The package depends on the foundation of RFC-0003. The property engine of
RFC-0004 depends on it, because `Case.history` returns a `History`.

## Motivation

The definition pins each check with 112 history vectors at 16.5.0, in the
kinds `linearizable`, `serializable`, `snapshot-isolation` and `seam`. The
search of a linearizability check is fixed step by step, so the vectors
state the steps of a check, its frontier and its candidates. Without the
package, the JVM cannot run a machine either, because a machine checks the
history of its steps after each step.

## Detailed design

### Components

| Component | Package | Responsibility |
|---|---|---|
| Recording | `dev.dokimi.assertion.history` | `History`, `Call`, `Event`, `Operation` and `Outcome` |
| Specifications | `dev.dokimi.assertion.history` | `Spec` and `History.specFrom` |
| Partitions | `dev.dokimi.assertion.history.check` | The partitions by key, in the order of their first invocation |
| The search | `dev.dokimi.assertion.history.check` | Wing and Gong's scan with the memo of the definition |
| Transactions | `dev.dokimi.assertion.history.check` | The micro-operations of list-append and register transactions |
| The derivation | `dev.dokimi.assertion.history.check` | The committed transactions, the version orders, the dependencies and the anomalies |
| Records and sentences | `dev.dokimi.assertion.history.check` | The detail and the sentence of each check |
| Concurrency | `dev.dokimi.assertion.history` | `History.concurrently` and `History.fromIntervals` |

### The API

```java
public final class History {
  public History();
  public Call invoke(int client, String operation, List<?> args, Object... keys);
  public List<Event> events();

  public static List<Outcome> concurrently(int clients, Duration within, IntConsumer body);
  public static History fromIntervals(List<History.Interval> entries);
  public static Spec<List<Operation>> specFrom(Supplier<BiFunction<String, List<?>, ?>> factory);

  public static <S> void isLinearizable(Seat seat, History history, Spec<S> spec, String msg, History.Option... options);
  public static void isSerializable(Seat seat, History history, String msg);
  public static void hasSnapshotIsolation(Seat seat, History history, String msg);

  public static History.Option budget(long steps);
  public static History.Option memoLimit(long bits);
  public static History.Option timeLimit(Duration limit);
  public static History.Option workers(int n);
}

public final class Call {
  public void ok(@Nullable Object output);
  public void fail(String error);
  public void unknown(String error);
}

public interface Spec<S> {
  S initial();
  List<S> next(S state, Operation operation);
  default boolean equal(S a, S b) { /* the equality of the definition */ }
}
```

- `invoke` records an invocation and returns its `Call`. A client with an
  open call cannot invoke again, and an `IllegalStateException` says so.
- A call that its client does not complete is pending. A call of unknown
  outcome may take effect at some point after its invocation, or never.
- A key and a value compare as `equal` compares them, through the typed
  literals of the foundation.
- `specFrom` returns the spec of a subject that `factory` builds anew. The
  state of the spec is the list of the operations that took effect, and
  `next` applies them to a new subject before it compares the output of
  the operation.
- `History.Interval` is a record of a timed call: its client, its
  operation, its arguments, its keys, its start, its end, which is null for
  a pending call, and its outcome. `fromIntervals` refuses an entry that
  starts before an earlier call of its client ends, with an
  `IllegalArgumentException` that states the position of the entry.

### The search

- The search scans the candidates, restarts from the first entry after an
  accepted call, and reports the frontier of the configuration with the
  most linearized calls, as the definition fixes.
- The memo of a configuration is its set of linearized calls with a
  completion entry, and the sorted hashes of its states. A configuration in
  the memo covers a new one when both have the same states and the same
  calls with a completion entry, and its calls without a completion entry
  are among those of the new one. The search skips a covered configuration.
- A set of calls is a `long[]` of one bit per position, as the memo counts
  one bit per call of the partition for each configuration.
- A state hashes with `Spec.hash`, a default member that hashes the typed
  literal of the state. A spec whose states have no typed literal states
  `hash` and `equal` together.
- The search stops as undecided before a step past its budget, before it
  stores a configuration past its memo limit, and once its time has passed
  on the platform clock.

### Isolation checks

- A history whose micro-operations write is a register history, and every
  other history is a list-append history. A history that both appends and
  writes ends the call with a fault.
- The derivation reports the twelve kinds of the definition in their
  order: garbage-read, duplicate-append, internal-inconsistency,
  incompatible-order, aborted-read, intermediate-read, lost-update, G0,
  G1c, G-single, G-nonadjacent and G2. Snapshot isolation forbids every
  kind but G2.
- A lost update is two ok transactions that read one list or value of a
  key by their first micro-operation on the key, and both append or write
  to the key.

### Concurrency

- `concurrently` runs one platform thread per client, starts them at once
  through a `CyclicBarrier`, and waits `within` on the platform clock.
- The overlay of Java states a limit on `history`: each increment of the
  counter of the recording orders the memory operations of the recording
  client against every other client, and that order can supply a barrier
  that the subject lacks.

### Invariants

- The events of a history are in the order of their indices, and every
  completion follows its invocation.
- The search of one partition is a function of its calls and its spec: two
  runs of one check take the same steps.
- A check reports the first violated partition, and otherwise the first
  undecided one.

### Bounds

| Bound | Default |
|---|---|
| Steps of the search of a partition | 10,000,000 |
| The memo of a partition | the limit of the definition, one bit per call for each configuration |
| Time of a check | none, unless `timeLimit` states one |

### Failures

| Event | Result |
|---|---|
| A function of the spec throws | The call ends with a fault that names the call that the search stepped |
| A key with no typed literal | `invoke` throws an `IllegalArgumentException` |
| A call of an isolation check that is no `txn` | The call ends with a fault that names the call |
| An append and a write in one history | The call ends with a fault |

## Alternatives considered

### A. Knossos or Elle through a JVM process

The checks would hand the history to Jepsen's checkers, which run on the
JVM.

**Why not:** the definition fixes the search, its steps and its records,
and Knossos and Elle have their own. Both also need Clojure at run time,
and `assert-core` has no runtime dependency.

### B. `Object.hashCode` for the memo

**Why not:** `hashCode` follows `Object.equals`, which treats NaN, signed
zero and arrays as the definition does not. A hash of the typed literal is
consistent with the equality of the definition.

## Drawbacks

- `Spec.hash` of a state without a typed literal needs a member of the
  caller's spec.
- A recording client takes a lock of the history for each event, so the
  recording orders the clients, as the limit of the overlay states.

## References

| What | Where |
|---|---|
| The history checker | <https://github.com/dokimasia/assert-spec/blob/main/docs/rfc/0004-the-history-checker.md> |
| Isolation checks over transaction histories | <https://github.com/dokimasia/assert-spec/blob/main/docs/rfc/0015-isolation-checks-over-transaction-histories.md> |
| The sequential specification of a history check | <https://github.com/dokimasia/assert-spec/blob/main/docs/rfc/0026-the-sequential-specification-of-a-history-check.md> |
| Isolation checks over register histories | <https://github.com/dokimasia/assert-spec/blob/main/docs/rfc/0046-isolation-checks-over-register-histories.md> |
| Calls without a completion in the memo | <https://github.com/dokimasia/assert-spec/blob/main/docs/rfc/0051-calls-without-a-completion-in-the-memo.md> |
| The JVM design of definition 8.0.0 | [RFC-0002](0002-definition-8-in-java-and-kotlin.md) |
