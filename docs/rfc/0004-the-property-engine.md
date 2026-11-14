---
rfc: 0004
title: The property engine of definition 16.5.0 in Java and Kotlin
author: Roy Klopper <roy.klopper@stealthscale.io>
status: Accepted
created: 2026-11-11
updated: 2026-11-14
discussion: none
supersedes: RFC-0002, in part
superseded-by: none
produces-adr: none
---

# RFC-0004: The property engine of definition 16.5.0 in Java and Kotlin

## Summary

`Prop` runs a body against generated cases. It shrinks a failing case and
stores it, as the definition fixes. This proposal designs the engine of the
JVM at definition 16.5.0. It takes over the section "The property engine"
of RFC-0002 and adds the rows of definitions 9.0.0 to 16.5.0:

| Change | Java API |
|---|---|
| A wrapper runs each run of the body | `Prop.around` |
| The values of a generator for a seed | `Prop.values` |
| A run gives a type a generator | `Prop.using` |
| A case skips the run of its property | `Case.skip` |
| A case ends with a fault, and registers cleanups | `Case.cleanup` |
| The seed of a run, and the result of each run | `Prop.runSeed`, `Prop.onResult` and `RunResult` |
| A run replays every n-th valid case | `Prop.confirm` |
| A run replays a failing case n times before the shrink | `Prop.replays`, `RunResult.replays` and `RunResult.reproduced` |
| A stored case replays its counterexample, steps included | store entries of format 2 |
| A trace replays a composite after the entries of its draws | `Prop.draws` |
| A trace replays a draw through a composite after the entries of the composite's draws | `Prop.draws` |

The property forms, the generators and the options keep the names of the
naming table. The engine depends on the foundation of RFC-0003 and on the
recording of a history of RFC-0005, because `Case.history` returns one.

## Motivation

Every vector family of the property engine pins a rule that the JVM does not
implement yet: 452 property vectors at 16.5.0, in 14 kinds from `behaviour`
to `token`. The engine also comes before the machines, because a machine's
steps are choices of a case. Its rules are fixed: the source, the decoding
of each generator, the shrink passes, the token and the store are the same
in every language, so one seed gives one sequence of cases everywhere.

## Detailed design

### Components

| Component | Package | Responsibility |
|---|---|---|
| The API | `dev.dokimi.assertion.prop` | `Prop`, `Case`, `Generator`, `RunResult` and `Prop.Option` |
| The source | `dev.dokimi.assertion.prop.engine` | SFC64 on `long`, with unsigned arithmetic from `Long`, and the stream of each case |
| Choices and requests | `dev.dokimi.assertion.prop.engine` | The kinds of choice, their bounds, their targets and their order |
| Providers | `dev.dokimi.assertion.prop.engine` | Generating, replaying, stating values, edges, tracing and the fuzz bridge |
| Generators | `dev.dokimi.assertion.prop.engine.generator` | The decoding and the inverse of each generator of the vocabulary |
| The runner | `dev.dokimi.assertion.prop.engine` | The phases, the tally, the conclusion and the replays |
| The case tree | `dev.dokimi.assertion.prop.engine` | The exhaustion of a domain and the repeats of a case |
| The shrinker and the explainer | `dev.dokimi.assertion.prop.engine` | The passes of the definition, the budget and the explanation of each draw |
| Coverage | `dev.dokimi.assertion.prop.engine` | The Wilson score interval and the checks at 2, 4 and 8 times `cases` |
| Tokens and the store | `dev.dokimi.assertion.prop.engine` | The token `prop1:` and the entries of format 1 and 2 |
| Traces | `dev.dokimi.assertion.prop.engine` | The entries of a trace, and the provider that follows them |
| The campaign | `dev.dokimi.assertion.prop.engine` | The profile `campaign`, its pool and its mutations |
| Types and shapes | `dev.dokimi.assertion.prop.shape` | `Prop.of`, `Prop.ofShape` and the registry |
| Forms | `dev.dokimi.assertion.prop` | The 40 property forms of the naming table |
| Kotlin | `dev.dokimi.assertion.kotlin` | The Kotlin type reader |

### The API

```java
public final class Prop {
  public static void forAll(Seat seat, String contract, Consumer<Case> body, Prop.Option... options);
  public static Consumer<byte[]> fuzz(Seat seat, String contract, Consumer<Case> body, Prop.Option... options);
  public static <T> List<T> values(Generator<T> generator, long seed, int n, Prop.Option... options);
  public static long runSeed(Seat seat, String contract);

  public static Prop.Option cases(int n);
  public static Prop.Option seed(long seed);
  public static Prop.Option replay(String token);
  public static Prop.Option replays(int n);
  public static Prop.Option confirm(int every);
  public static Prop.Option onResult(Consumer<RunResult> f);
  // and the other options, generators, forms and registrations of the naming table
}

public final class Case implements Seat, Reporter, Clocked, FaultReporter, Skipper, Observer {
  public <T> T draw(Generator<T> generator, String label);
  public void assume(boolean condition);
  public void classify(String label);
  public void note(String message);
  public RandomGenerator rand();
  public void observe(long fingerprint);
  public void target(String label, double score);
  public History history();
  @Override public void skip(String reason);
  @Override public void cleanup(Runnable cleanup);
  @Override public Supplier<Boolean> cancelled();
}

public record RunResult(
    String outcome, int cases, int rejected, long seed,
    @Nullable Object counterexample, @Nullable Object failure, @Nullable String choices,
    @Nullable Object others, @Nullable Object divergence, @Nullable Object coverage,
    @Nullable String reason, @Nullable Integer replays, @Nullable Integer reproduced) {}
```

- `Prop.Option` is a value that the static factories of `Prop` return, as
  Go's options are values. A later option of one kind replaces an earlier
  one. A factory refuses a count below its minimum with an
  `IllegalArgumentException` whose message names the call.
- `RunResult` has one component for each of the 13 fields of the detail of
  a run. A field that the outcome does not use is null.
- A form reads the type of its input from a `Class` that the caller passes,
  or from `Prop.using`, because Java erases the type of a lambda's
  parameter.

### The rules of a run

- **Workers.** `Prop.workers(n)` runs the cases of the last three phases on
  n platform threads, and the run reports what a run on one worker reports.
- **The end of a case.** `assume`, a skip, a repeated label and a fatal
  failure end the case with an `Error` of the engine. A `catch (Exception
  e)` in a body does not catch it, and every later call of the case throws
  it again.
- **Cleanups.** A case runs its cleanups when it ends, the last registered
  first, after the body and before the next case. A failure or an error in
  a cleanup is a failure of the case, as one in the body is, and the later
  cleanups still run.
- **Numbers.** A 128-bit integer and the unsigned 64-bit range are a
  `BigInteger`. `Prop.integer` has overloads for `int`, `long` and
  `BigInteger`.
- **Text.** A string generator works in code points and never produces a
  lone surrogate.
- **Examples.** An example whose values the generator inverts runs from its
  choices. An example of a generator without an inverse runs on its values,
  and a run does not replay, shrink or store it.
- **Replays.** A failing case is replayed `replays` times before the
  shrink, once by default. The run shrinks the case when one or more
  replays reproduce the failure, and ends as flaky with the difference of
  the first replay when none does.
- **Confirmation.** Under `Prop.confirm(every)`, a run replays each valid
  case whose count is a multiple of `every`, right after the case. A replay
  that differs ends the run as flaky, with the valid case as its
  counterexample and without a failure.
- **The store.** A run stores its failures under
  `src/test/resources/testdata/prop/<class>/<method>/`, relative to the
  project directory. An entry of a counterexample with a machine step is of
  format 2, and every other entry of format 1. A run skips an entry of a
  later format and notes the file.
- **Stored cases.** A stored case replays its choices first. When they
  decode to other values or steps than its entry records, the run replays
  the recorded counterexample as a trace, and notes the entry.
- **Traces.** `Prop.draws(entries)` runs a case of the stated draws and
  steps first. A draw of a composite takes no entry when it starts. Its
  draws take theirs, and the composite then takes its own entry by its
  label.
- **Draws through a composite.** A draw of a one-of, an optional, a list, a
  dict, a filter or a recursive with a composite inside takes the first
  draw entry with its label up to the next step entry. Its value runs
  backwards with a placeholder for each composite, which does not make a
  choice. When the value runs backwards through a composite, the draws of
  the composite take the entries before it, and the draw takes the entry
  once it decodes. A one-of and a recursive take a branch through a
  composite only when no other branch produces the value. A filter does
  not test the value of a composite, a unique list does not compare it,
  and a dict through a composite refuses its entry.
- **Its report.** `Prop.forAll` aborts only, so it reports through `fail`.

`Prop.fuzz` replays the stored cases and the seed files on the seat, and
returns a target for Jazzer. The library does not depend on Jazzer. A fuzz
test passes the target the bytes of `FuzzedDataProvider`.

### Derived inputs

`Prop.of(Class<T>)` reads records, enums, sealed interfaces and the types of
the definition's table for Java through reflection, as RFC-0002 states. The
reader reads the annotations of Jakarta Bean Validation by the names of
their types, so `assert-core` does not depend on the API. `assert-kotlin`
registers a second reader through `ServiceLoader`, which reads data
classes, sealed classes, `Pair` and `Triple` through `kotlin-reflect`.

### Invariants

- One seed gives one sequence of cases on every worker count.
- A case makes at most `maxChoices` choices, 8,192 by default. A case past
  the cap is rejected.
- A replay of a case's choices requests the same bounds in the same order,
  or the run ends as flaky.
- The store keeps one entry per failure, named by the contract and the
  choices.

### Bounds

| Bound | Default |
|---|---|
| Valid cases of a run | 100 |
| Random cases of a phase | 10 times its valid cases |
| Choices of a case | 8,192 |
| Runs of the shrink and the explanation | 2,000 |
| Time of the shrink and the explanation | 30 s on the platform clock |
| Depth of a store entry | 64 |

### Failures

| Event | Result |
|---|---|
| An entry of `Prop.draws` that the body cannot follow | The call ends with a fault at the entry, before any other case runs |
| A `DOKIMI_ASSERT_PROP_*` variable of no value of its kind | The call ends with a fault before the run |
| A store with a damaged file | The call ends with a fault that names the file |
| Two properties of one test with one contract and one store | The call of the second ends with a fault |
| A wrapper of `Prop.around` that does not call the run, or calls it twice | The call ends with a fault |

## Alternatives considered

### A. jqwik as the engine

**Why not:** RFC-0002 rejects it. The definition fixes the source, the
decoding, the shrink passes and the token, and jqwik has its own. Its
licence, the Eclipse Public License 2.0, also differs from the MIT licence
of the library.

### B. Virtual threads for the workers

**Why not:** the artifact targets Java 17, which has no virtual threads.
A worker runs one case at a time, so a platform thread per worker costs
only its stack.

### C. A builder of the settings in place of varargs of options

`Prop.forAll` would take a `Settings` that a builder returns.

**Why not:** the naming table states each option as a helper of `Prop`, as
Go's `prop.Cases` is a helper. Varargs keep a call of one option short.

## Drawbacks

- **Size.** The TypeScript library implements this engine in 18,772 source
  lines under `src/prop`, measured on 2026-11-11. We expect the JVM engine
  to be of the same order, which is an estimate.
- **Reflection.** `Prop.of` reads records and sealed interfaces through
  reflection. A named module that does not open the package of the type
  refuses that reflection, and the caller then opens the package to the
  module of the library.
- **Erasure.** A form takes its input type as a `Class`, which a generic
  input type such as `List<Line>` cannot state without `Prop.using`.

## References

| What | Where |
|---|---|
| Properties over generated inputs | <https://github.com/dokimasia/assert-spec/blob/main/docs/rfc/0010-properties-over-generated-inputs.md> |
| Property forms and derived inputs | <https://github.com/dokimasia/assert-spec/blob/main/docs/rfc/0011-property-forms-and-derived-inputs.md> |
| Machines, simulation and campaigns | <https://github.com/dokimasia/assert-spec/blob/main/docs/rfc/0012-machines-simulation-and-campaigns.md> |
| A case that skips the run of its property | <https://github.com/dokimasia/assert-spec/blob/main/docs/rfc/0038-a-case-that-skips-the-run-of-its-property.md> |
| The result of a run | <https://github.com/dokimasia/assert-spec/blob/main/docs/rfc/0050-the-result-of-a-run.md> |
| The replay of valid cases | <https://github.com/dokimasia/assert-spec/blob/main/docs/rfc/0052-the-replay-of-valid-cases.md> |
| The replays of a failing case | <https://github.com/dokimasia/assert-spec/blob/main/docs/rfc/0057-the-replays-of-a-failing-case.md> |
| The steps of a stored case | <https://github.com/dokimasia/assert-spec/blob/main/docs/rfc/0060-the-steps-of-a-stored-case.md> |
| A composite in a trace | <https://github.com/dokimasia/assert-spec/blob/main/docs/rfc/0061-a-composite-in-a-trace.md> |
| A draw through a composite in a trace | <https://github.com/dokimasia/assert-spec/blob/main/docs/rfc/0062-a-draw-through-a-composite-in-a-trace.md> |
| The JVM design of definition 8.0.0 | [RFC-0002](0002-definition-8-in-java-and-kotlin.md) |
