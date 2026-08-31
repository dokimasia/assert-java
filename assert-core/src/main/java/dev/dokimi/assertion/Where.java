package dev.dokimi.assertion;

import org.jspecify.annotations.NullMarked;

/// The call site a failure came from.
///
/// @param file the file the assertion was called from
/// @param line the line within it
@NullMarked
public record Where(String file, int line) {}
