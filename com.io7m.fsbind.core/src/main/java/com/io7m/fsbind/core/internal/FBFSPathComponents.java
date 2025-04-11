/*
 * Copyright © 2025 Mark Raynsford <code@io7m.com> https://www.io7m.com
 *
 * Permission to use, copy, modify, and/or distribute this software for any
 * purpose with or without fee is hereby granted, provided that the above
 * copyright notice and this permission notice appear in all copies.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY
 * SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 * ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR
 * IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 */


package com.io7m.fsbind.core.internal;

import java.util.List;
import java.util.Objects;

import static com.io7m.fsbind.core.internal.FBFS.FBFS_SEPARATOR;

/**
 * Functions to validate path components.
 */

public final class FBFSPathComponents
{
  private FBFSPathComponents()
  {

  }

  /**
   * Validate a path component.
   *
   * @param path      The full path, for error messages
   * @param component The path component
   */

  public static void validatePathComponent(
    final List<String> path,
    final String component)
  {
    Objects.requireNonNull(path, "path");
    Objects.requireNonNull(component, "component");

    switch (component) {
      case "" -> throw errorNoEmpty(path);
      case "." -> throw errorNoDot(path);
      case ".." -> throw errorNoDotDot(path);
      case "..." -> throw errorNoDotDotDot(path);
      default -> {
        if (component.contains(FBFS_SEPARATOR)) {
          throw errorNoSlashes(path);
        }
      }
    }
  }

  private static IllegalArgumentException errorNoEmpty(
    final List<String> path)
  {
    return new IllegalArgumentException(
      "fsbind path components cannot be empty (Received: %s)"
        .formatted(path)
    );
  }

  private static IllegalArgumentException errorNoDot(
    final List<String> path)
  {
    return new IllegalArgumentException(
      "fsbind path components cannot equal '.' (Received: %s)"
        .formatted(path)
    );
  }

  private static IllegalArgumentException errorNoDotDot(
    final List<String> path)
  {
    return new IllegalArgumentException(
      "fsbind path components cannot equal '..' (Received: %s)"
        .formatted(path)
    );
  }

  private static IllegalArgumentException errorNoDotDotDot(
    final List<String> path)
  {
    return new IllegalArgumentException(
      "fsbind path components cannot equal '...' (Received: %s)"
        .formatted(path)
    );
  }

  private static IllegalArgumentException errorNoSlashes(
    final List<String> names)
  {
    return new IllegalArgumentException(
      "fsbind path components cannot contain '/' (Received: %s)"
        .formatted(names)
    );
  }
}
