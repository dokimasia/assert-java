---
rfc: 0007
title: Trees of files of definition 16.5.0 in Java and Kotlin
author: Roy Klopper <roy.klopper@stealthscale.io>
status: Accepted
created: 2026-11-11
updated: 2026-11-14
discussion: none
supersedes: RFC-0002, in part
superseded-by: none
produces-adr: none
---

# RFC-0007: Trees of files of definition 16.5.0 in Java and Kotlin

## Summary

A test states a tree of files as a value and writes it into a directory of
its own. It then compares a directory with a stated tree or a golden tree.
This proposal designs the files package of the JVM at definition 16.5.0. It
takes over the section "Trees of files and golden trees" of RFC-0002:

| Change | Java API |
|---|---|
| A tree and its entries | `FileTree`, `FileTree.Entry` and `withMode` |
| The helpers of a tree | `FileTrees.text`, `bytes`, `executable`, `directory`, `link`, `workspace`, `write` and `read` |
| The tree assertions | `FileTrees.equal`, `contains` and `unchanged`, and `Golden.matchTree` |
| The path assertions | `FileTrees.absent`, `isFile`, `isDir`, `linksTo`, `hasContent` and `hasMode` |
| A tree written into a directory that exists | `FileTrees.write` |

The package depends on the foundation of RFC-0003 alone.

## Motivation

The definition pins the ten file assertions with 57 file vectors in ten
kinds, one per assertion that reads files. A vector states a tree as a
typed literal of the type `tree`, so the driver of the vectors needs the
tree, the workspace and the reader of a tree before it can run one. Each
suite of the library builds its trees by hand today, with `Files.write`
and `Files.createDirectories`.

## Detailed design

### Components

| Component | Package | Responsibility |
|---|---|---|
| The tree | `dev.dokimi.assertion.files` | `FileTree` and its entries, the rules of a path, and the literal of a tree |
| The writer | `dev.dokimi.assertion.files` | `workspace` and `write`, without a write through a link |
| The reader | `dev.dokimi.assertion.files` | Reads a tree from a directory, and follows no link |
| The comparison | `dev.dokimi.assertion.files` | The differences of two trees, and the fields of a failure |
| The assertions | `dev.dokimi.assertion.files`, `Golden` | The tree assertions, the path assertions and `Golden.matchTree` |

### The API

```java
public final class FileTree {
  public static FileTree of(Map<String, FileTree.Entry> entries);
  public SortedMap<String, FileTree.Entry> entries();

  public sealed interface Entry permits File, Directory, Link {
    FileTree.Entry withMode(int mode);
  }
  public record File(byte[] content, boolean executable, OptionalInt mode) implements Entry {}
  public record Directory(OptionalInt mode) implements Entry {}
  public record Link(String target) implements Entry {}
}

public final class FileTrees {
  public static FileTree.Entry text(String content);
  public static FileTree.Entry bytes(byte[] content);
  public static FileTree.Entry executable(String content);
  public static FileTree.Entry directory();
  public static FileTree.Entry link(String target);

  public static Path workspace(Seat seat, FileTree tree);
  public static void write(Seat seat, Path dir, FileTree tree);
  public static String read(Seat seat, Path path);

  public static void equal(Seat seat, Path dir, FileTree want, String msg);
  public static void contains(Seat seat, Path dir, FileTree want, String msg);
  public static void unchanged(Seat seat, Path dir, Runnable call, String msg);
  public static void absent(Seat seat, Path path, String msg);
  public static void isFile(Seat seat, Path path, String msg);
  public static void isDir(Seat seat, Path path, String msg);
  public static void linksTo(Seat seat, Path path, String target, String msg);
  public static void hasContent(Seat seat, Path path, String content, String msg);
  public static void hasMode(Seat seat, Path path, int mode, String msg);
}
```

- `FileTree.of` refuses a tree that breaks a rule of the definition with an
  `IllegalArgumentException` that states the path: a name that is empty,
  `.` or `..`, a name with `/`, `\` or NUL, an entry below a file or a
  link, and a mode on a link.
- The entries of a tree order by the bytes of the UTF-8 encoding of their
  paths, through a comparator of the tree, because `String.compareTo`
  orders UTF-16 units.
- A path assertion takes a `java.nio.file.Path`, as the definition states
  for Java. `hasContent` has an overload for `byte[]`.
- `read` returns the content of a file as text, and ends the call with a
  fault when no file is at the path or the file is no valid UTF-8.

### The writer

- `workspace` creates a directory of the test's own under the temporary
  directory of the JVM, and registers a cleanup of the seat that removes
  it. The removal first sets the owner's write bit of each directory that
  lacks it.
- `write` writes a tree into a directory that exists. It creates each
  entry where nothing is, and replaces the content of a file, the target of
  a link and the mode of a stated entry where an entry of the same kind is.
  It keeps every entry that the tree does not state. An entry of another
  kind at a stated path ends the call with a fault before any write.
- On a platform whose `newDirectoryStream` returns a
  `SecureDirectoryStream`, the writer creates each entry relative to the
  open directory with `NOFOLLOW_LINKS`, so it does not follow a link out of
  the tree. On another platform, it reads every parent with
  `NOFOLLOW_LINKS` before it creates an entry.
- The writer sets 0644 on each file without a mode and 0755 on each
  executable file and each directory without one, through
  `PosixFileAttributeView`. It sets each mode after the entry is written,
  and the mode of a directory after every entry below it.
- Where the file system has no `PosixFileAttributeView`, as on Windows, the
  writer does not set a bit. The reader then reads every entry without a
  mode.
- A link that the platform refuses to create, a name that the platform
  reserves, and a second path that a file system maps to an entry already
  written end the call with a fault.

### The reader and the comparison

- The reader walks a directory from its root with `NOFOLLOW_LINKS`, and
  reads a link as an entry with its target through
  `Files.readSymbolicLink`. A device, a socket and a pipe end the call with
  a fault.
- Entries at one path differ as the definition states: by kind, by
  content, by target, by a mode that the wanted entry states, or by the
  execute bit of a wanted file without a mode.
- A failure states `want`, `got` and `differences`, with at most the first
  64 paths that differ, in path order. A file longer than 65,536 bytes
  states its digest and its size in a record.

### Golden trees

`Golden.matchTree(seat, name, dir, update, scrubbers)` takes its parameters
in the order of `Golden.match`. It resolves `name` against
`src/test/resources/testdata`, as `Golden.match` resolves a name today. It
compares execute bits, and it does not read a mode of the golden tree. With
`update`
true, it makes the golden directory equal the output and passes. It
removes a link there without a removal of the entry that the link points
to. A test passes `Golden.shouldUpdate()` as `update`.

### Invariants

- A write never follows a link.
- A tree that a workspace writes reads back as the same tree under any
  umask.
- A comparison does not read a timestamp, an owner, a group or an extended
  attribute.

### Failures

| Event | Result |
|---|---|
| A tree that breaks a rule of a path | `FileTree.of` throws an `IllegalArgumentException` |
| An entry that the platform cannot store as stated | The call ends with a fault, and writes over no entry |
| `write` of an entry where an entry of another kind is | The call ends with a fault before any write |
| `hasMode` on a file system without permission bits | The call ends with a fault |
| A device, a socket or a pipe in a tree that a call reads | The call ends with a fault |
| A golden directory that cannot be written under update | The call ends with a fault |

## Alternatives considered

### A. Jimfs, a file system in memory

**Why not:** the definition compares real directories, and a subject under
test writes through `java.nio.file` to the default file system. A tree in
memory would test another file system than the one that the subject uses.

### B. `Files.walkFileTree` for the reader

**Why not:** the reader needs the entries in the byte order of their UTF-8
paths. A walk returns them in the order of the directory. The reader sorts
the entries of each directory, and a walk of its own then costs no more.

## Drawbacks

- On a Windows runner without the privilege to create a symbolic link,
  each call of a tree with a link ends with a fault. The conformance driver
  skips those vectors there.
- The fallback without `SecureDirectoryStream` checks each parent before
  each write. A concurrent change of the directory can still race that
  check.

## References

| What | Where |
|---|---|
| Trees of files | <https://github.com/dokimasia/assert-spec/blob/main/docs/rfc/0021-trees-of-files.md> |
| Writing a tree into a directory that exists | <https://github.com/dokimasia/assert-spec/blob/main/docs/rfc/0028-writing-a-tree-into-a-directory-that-exists.md> |
| `SecureDirectoryStream` | <https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/file/SecureDirectoryStream.html> |
| The JVM design of definition 8.0.0 | [RFC-0002](0002-definition-8-in-java-and-kotlin.md) |
