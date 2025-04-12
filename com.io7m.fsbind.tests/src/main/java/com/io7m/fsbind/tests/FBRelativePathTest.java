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


package com.io7m.fsbind.tests;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static com.io7m.fsbind.tests.FBFilesystemTest.createFS;
import static com.io7m.fsbind.tests.FBFilesystemTest.createFSOther;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FBRelativePathTest
{
  @Test
  public void testRelativeOrdered()
    throws Exception
  {
    try (final var fs = createFS()) {
      final var a = fs.getPath("a");
      final var b = fs.getPath("b");
      final var c = fs.getPath("c");

      assertEquals(
        List.of(a, b, c),
        Stream.of(c, b, a).sorted().toList()
      );
    }
  }

  @Test
  public void testRelativeEqual()
    throws Exception
  {
    try (final var fs = createFS()) {
      assertEquals(
        fs.getPath("a", "b", "c"),
        fs.getPath("a", "b", "c")
      );
    }
  }

  @Test
  public void testRelativeEqualFalse0()
    throws Exception
  {
    try (final var fs = createFS()) {
      assertNotEquals(
        fs.getPath("a", "b", "c"),
        fs.getPath("a", "b", "d")
      );
    }
  }

  @Test
  public void testRelativeEqualFalse1()
    throws Exception
  {
    try (final var fs = createFS()) {
      assertNotEquals(
        fs.getPath("a", "b", "c"),
        fs.getPath("/", "a", "b", "c")
      );
    }
  }

  @Test
  public void testRelativeEqualFalse2()
    throws Exception
  {
    try (final var fs0 = createFS(); final var fs1 = createFSOther()) {
      assertNotEquals(
        fs0.getPath("a", "b", "c"),
        fs1.getPath("a", "b", "c")
      );
    }
  }

  @Test
  public void testRelativeRoot()
    throws Exception
  {
    try (final var fs = createFS()) {
      final var p = fs.getPath("a", "b", "c");
      final var r = fs.getPath(fs.getSeparator());
      assertEquals(
        r,
        p.getRoot(),
        "%s's root is %s".formatted(p, r)
      );
    }
  }

  @Test
  public void testRelativeFilesystem()
    throws Exception
  {
    try (final var fs = createFS()) {
      final var p = fs.getPath("a", "b", "c");
      assertEquals(
        fs,
        p.getFileSystem(),
        "%s's filesystem is %s".formatted(p, fs)
      );
    }
  }

  @Test
  public void testRelativeNameCount()
    throws Exception
  {
    try (final var fs = createFS()) {
      final var p = fs.getPath("a", "b", "c");
      assertEquals(
        3,
        p.getNameCount(),
        "%s count is %d".formatted(p, p.getNameCount())
      );
    }
  }

  @Test
  public void testRelativeParentStartsFalse()
    throws Exception
  {
    try (final var fs = createFS()) {
      final var p = fs.getPath("a", "b", "c");
      assertFalse(
        p.getParent().startsWith(p),
        "%s does not start with %s".formatted(p.getParent(), p)
      );
    }
  }

  @Test
  public void testRelativeParentStarts()
    throws Exception
  {
    try (final var fs = createFS()) {
      final var p = fs.getPath("a", "b", "c");
      assertTrue(
        p.startsWith(p.getParent()),
        "%s starts with %s".formatted(p, p.getParent())
      );
    }
  }

  @Test
  public void testRelativeParent()
    throws Exception
  {
    try (final var fs = createFS()) {
      final var p = fs.getPath("a", "b", "c");
      final var q = fs.getPath("a", "b");
      assertEquals(
        q,
        p.getParent(),
        "%s's parent is %s".formatted(p, q)
      );
    }
  }

  @Test
  public void testRelativeAncestor()
    throws Exception
  {
    try (final var fs = createFS()) {
      final var p = fs.getPath("a", "b", "c");
      assertNull(
        p.getParent().getParent().getParent(),
        "%s has no third ancestor".formatted(p)
      );
    }
  }

  @Test
  public void testRelativeName()
    throws Exception
  {
    try (final var fs = createFS()) {
      final var p = fs.getPath("a", "b", "c");
      final var q = fs.getPath("c");
      assertEquals(
        q,
        p.getFileName(),
        "%s's name is %s".formatted(p, q)
      );
    }
  }

  @Test
  public void testRelativeResolved()
    throws Exception
  {
    try (final var fs = createFS()) {
      final var p = fs.getPath("a", "b", "c");
      final var q = fs.getPath("a", "b", "c", "d");
      assertEquals(
        q,
        p.resolve(fs.getPath("d")),
        "%s resolved is %s".formatted(p, q)
      );
    }
  }

  @Test
  public void testRelativeSubpath()
    throws Exception
  {
    try (final var fs = createFS()) {
      final var p = fs.getPath("a", "b", "c");
      final var q = fs.getPath("b", "c");
      assertEquals(
        q,
        p.subpath(1, 3),
        "%s subpath is %s".formatted(p, q)
      );
    }
  }

  @Test
  public void testRelativeNotAbsolute()
    throws Exception
  {
    try (final var fs = createFS()) {
      final var p = fs.getPath("a", "b", "c");
      assertFalse(
        p.isAbsolute(),
        "%s is not absolute".formatted(p)
      );
    }
  }

  @Test
  public void testRelativeNormalize()
    throws Exception
  {
    try (final var fs = createFS()) {
      final var p = fs.getPath("a", "b", "c");
      assertEquals(
        p,
        p.normalize(),
        "%s normalized is %s".formatted(p, p)
      );
    }
  }

  @Test
  public void testRelativeRelativizeUnsupported()
    throws Exception
  {
    try (final var fs = createFS()) {
      final var p = fs.getPath("a", "b", "c");
      final var q = fs.getPath("a", "b");

      assertThrows(
        UnsupportedOperationException.class,
        () -> {
          p.relativize(q);
        }
      );
    }
  }

  @Test
  public void testRelativeAbsoluteUnsupported()
    throws Exception
  {
    try (final var fs = createFS()) {
      final var p = fs.getPath("a", "b", "c");

      assertThrows(
        UnsupportedOperationException.class,
        () -> {
          p.toAbsolutePath();
        }
      );
    }
  }

  @Test
  public void testRelativeRealUnsupported()
    throws Exception
  {
    try (final var fs = createFS()) {
      final var p = fs.getPath("a", "b", "c");

      assertThrows(
        UnsupportedOperationException.class,
        () -> {
          p.toRealPath();
        }
      );
    }
  }

  @Test
  public void testRelativeURIUnsupported()
    throws Exception
  {
    try (final var fs = createFS()) {
      final var p = fs.getPath("a", "b", "c");

      assertThrows(
        UnsupportedOperationException.class,
        () -> {
          p.toUri();
        }
      );
    }
  }

  @Test
  public void testRelativeRegisterUnsupported()
    throws Exception
  {
    try (final var fs = createFS()) {
      final var p = fs.getPath("a", "b", "c");

      assertThrows(
        UnsupportedOperationException.class,
        () -> {
          p.register(null, null);
        }
      );
    }
  }

  @Test
  public void testRelativeEndsFalse()
    throws Exception
  {
    try (final var fs = createFS()) {
      final var p = fs.getPath("a", "b", "c");
      final var q = fs.getPath("b", "d");
      assertFalse(
        p.endsWith(q),
        "%s does not end with %s".formatted(p, q)
      );
    }
  }

  @Test
  public void testRelativeEndsLong()
    throws Exception
  {
    try (final var fs = createFS()) {
      final var p = fs.getPath("a", "b", "c");
      final var q = fs.getPath("b", "c", "e", "f");
      assertFalse(
        p.endsWith(q),
        "%s does not end with %s".formatted(p, q)
      );
    }
  }

  @Test
  public void testRelativeEnds0()
    throws Exception
  {
    try (final var fs = createFS()) {
      final var p = fs.getPath("a", "b", "c");
      final var q = fs.getPath("b", "c");
      assertTrue(
        p.endsWith(q),
        "%s ends with %s".formatted(p, q)
      );
    }
  }

  @Test
  public void testRelativeEnds1()
    throws Exception
  {
    try (final var fs = createFS()) {
      final var p = fs.getPath("a", "b", "c");
      final var q = fs.getPath("B", "C");
      assertTrue(
        p.endsWith(q),
        "%s ends with %s".formatted(p, q)
      );
    }
  }

  @Test
  public void testRelativeStarts0()
    throws Exception
  {
    try (final var fs = createFS()) {
      final var p = fs.getPath("a", "b", "c");
      final var q = fs.getPath("a", "b");
      assertTrue(
        p.startsWith(q),
        "%s starts with %s".formatted(p, q)
      );
    }
  }

  @Test
  public void testRelativeStarts1()
    throws Exception
  {
    try (final var fs = createFS()) {
      final var p = fs.getPath("a", "b", "c");
      final var q = fs.getPath("A", "B");
      assertTrue(
        p.startsWith(q),
        "%s starts with %s".formatted(p, q)
      );
    }
  }
}
