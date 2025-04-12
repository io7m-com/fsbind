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

import java.net.URI;
import java.nio.file.Path;
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

public class FBAbsolutePathTest
{
  @Test
  public void testAbsoluteOrdered()
    throws Exception
  {
    try (final var fs = createFS()) {
      final var a = fs.getPath(fs.getSeparator(), "a");
      final var b = fs.getPath(fs.getSeparator(),"b");
      final var c = fs.getPath(fs.getSeparator(),"c");

      assertEquals(
        List.of(a, b, c),
        Stream.of(c, b, a).sorted().toList()
      );
    }
  }

  @Test
  public void testAbsoluteEqual0()
    throws Exception
  {
    try (final var fs = createFS()) {
      assertEquals(
        fs.getPath(fs.getSeparator(),"a", "b", "c"),
        fs.getPath(fs.getSeparator(),"a", "b", "c")
      );
    }
  }

  @Test
  public void testAbsoluteEqual1()
    throws Exception
  {
    try (final var fs = createFS()) {
      final var p = fs.getPath(fs.getSeparator(), "a", "b", "c");
      final var q = fs.getPath(fs.getSeparator(), "A", "B", "C");
      assertEquals(0, p.compareTo(q));
    }
  }

  @Test
  public void testAbsoluteEqualFalse0()
    throws Exception
  {
    try (final var fs = createFS()) {
      assertNotEquals(
        fs.getPath(fs.getSeparator(),"a", "b", "c"),
        fs.getPath(fs.getSeparator(),"a", "b", "d")
      );
    }
  }

  @Test
  public void testAbsoluteEqualFalse1()
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
  public void testAbsoluteEqualFalse2()
    throws Exception
  {
    try (final var fs0 = createFS(); final var fs1 = createFSOther()) {
      assertNotEquals(
        fs0.getPath(fs0.getSeparator(),"a", "b", "c"),
        fs1.getPath(fs1.getSeparator(),"a", "b", "c")
      );
    }
  }

  @Test
  public void testAbsoluteRoot()
    throws Exception
  {
    try (final var fs = createFS()) {
      final var p = fs.getPath(fs.getSeparator(), "a", "b", "c");
      final var r = fs.getPath(fs.getSeparator());
      assertEquals(
        r,
        p.getRoot(),
        "%s's root is %s".formatted(p, r)
      );
    }
  }

  @Test
  public void testAbsoluteFilesystem()
    throws Exception
  {
    try (final var fs = createFS()) {
      final var p = fs.getPath(fs.getSeparator(), "a", "b", "c");
      assertEquals(
        fs,
        p.getFileSystem(),
        "%s's filesystem is %s".formatted(p, fs)
      );
    }
  }

  @Test
  public void testAbsoluteNameCount()
    throws Exception
  {
    try (final var fs = createFS()) {
      final var p = fs.getPath(fs.getSeparator(), "a", "b", "c");
      assertEquals(
        3,
        p.getNameCount(),
        "%s count is %d".formatted(p, p.getNameCount())
      );
    }
  }

  @Test
  public void testAbsoluteParentStartsFalse()
    throws Exception
  {
    try (final var fs = createFS()) {
      final var p = fs.getPath(fs.getSeparator(), "a", "b", "c");
      assertFalse(
        p.getParent().startsWith(p),
        "%s does not start with %s".formatted(p.getParent(), p)
      );
    }
  }

  @Test
  public void testAbsoluteParentStarts()
    throws Exception
  {
    try (final var fs = createFS()) {
      final var p = fs.getPath(fs.getSeparator(), "a", "b", "c");
      assertTrue(
        p.startsWith(p.getParent()),
        "%s starts with %s".formatted(p, p.getParent())
      );
    }
  }

  @Test
  public void testAbsoluteParent()
    throws Exception
  {
    try (final var fs = createFS()) {
      final var p = fs.getPath(fs.getSeparator(), "a", "b", "c");
      final var q = fs.getPath(fs.getSeparator(), "a", "b");
      assertEquals(
        q,
        p.getParent(),
        "%s's parent is %s".formatted(p, q)
      );
    }
  }

  @Test
  public void testAbsoluteAncestor()
    throws Exception
  {
    try (final var fs = createFS()) {
      final var p = fs.getPath(fs.getSeparator(), "a", "b", "c");
      assertNull(
        p.getParent().getParent().getParent().getParent(),
        "%s has no fourth ancestor".formatted(p)
      );
    }
  }

  @Test
  public void testAbsoluteName()
    throws Exception
  {
    try (final var fs = createFS()) {
      final var p = fs.getPath(fs.getSeparator(), "a", "b", "c");
      final var q = fs.getPath("c");
      assertEquals(
        q,
        p.getFileName(),
        "%s's name is %s".formatted(p, q)
      );
    }
  }

  @Test
  public void testAbsoluteRootName()
    throws Exception
  {
    try (final var fs = createFS()) {
      final var p = fs.getPath(fs.getSeparator());
      assertNull(p.getFileName(), "%s's name is null".formatted(p));
    }
  }

  @Test
  public void testAbsoluteResolved()
    throws Exception
  {
    try (final var fs = createFS()) {
      final var p = fs.getPath(fs.getSeparator(), "a", "b", "c");
      final var q = fs.getPath(fs.getSeparator(), "a", "b", "c", "d");
      assertEquals(
        q,
        p.resolve(fs.getPath("d")),
        "%s resolved is %s".formatted(p, q)
      );
    }
  }

  @Test
  public void testAbsoluteNameIndex()
    throws Exception
  {
    try (final var fs = createFS()) {
      final var p = fs.getPath(fs.getSeparator(), "a", "b", "c");
      assertEquals(fs.getPath("a"), p.getName(0));
      assertEquals(fs.getPath("b"), p.getName(1));
      assertEquals(fs.getPath("c"), p.getName(2));
    }
  }

  @Test
  public void testAbsoluteSubpath()
    throws Exception
  {
    try (final var fs = createFS()) {
      final var p = fs.getPath(fs.getSeparator(), "a", "b", "c");
      final var q = fs.getPath("b", "c");
      assertEquals(
        q,
        p.subpath(2, 4),
        "%s subpath is %s".formatted(p, q)
      );
    }
  }

  @Test
  public void testAbsoluteNotAbsolute()
    throws Exception
  {
    try (final var fs = createFS()) {
      final var p = fs.getPath(fs.getSeparator(), "a", "b", "c");
      assertTrue(
        p.isAbsolute(),
        "%s is absolute".formatted(p)
      );
    }
  }

  @Test
  public void testAbsoluteNormalize()
    throws Exception
  {
    try (final var fs = createFS()) {
      final var p = fs.getPath(fs.getSeparator(), "a", "b", "c");
      assertEquals(
        p,
        p.normalize(),
        "%s normalized is %s".formatted(p, p)
      );
    }
  }

  @Test
  public void testAbsoluteRelativizeUnsupported()
    throws Exception
  {
    try (final var fs = createFS()) {
      final var p = fs.getPath(fs.getSeparator(), "a", "b", "c");
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
  public void testAbsoluteAbsolute()
    throws Exception
  {
    try (final var fs = createFS()) {
      final var p = fs.getPath(fs.getSeparator(), "a", "b", "c");
      assertEquals(p, p.toAbsolutePath());
    }
  }

  @Test
  public void testAbsoluteReal()
    throws Exception
  {
    try (final var fs = createFS()) {
      final var p = fs.getPath(fs.getSeparator(), "a", "b", "c");
      assertEquals(p, p.toRealPath());
    }
  }

  @Test
  public void testAbsoluteURIUnsupported()
    throws Exception
  {
    try (final var fs = createFS()) {
      final var p = fs.getPath(fs.getSeparator(), "a", "b", "c");
      assertEquals(URI.create("fsbind:x:/a/b/c"), p.toUri());
    }
  }

  @Test
  public void testAbsoluteEndsFalse()
    throws Exception
  {
    try (final var fs = createFS()) {
      final var p = fs.getPath(fs.getSeparator(), "a", "b", "c");
      final var q = fs.getPath("b", "d");
      assertFalse(
        p.endsWith(q),
        "%s does not end with %s".formatted(p, q)
      );
    }
  }

  @Test
  public void testAbsoluteEndsLong()
    throws Exception
  {
    try (final var fs = createFS()) {
      final var p = fs.getPath(fs.getSeparator(), "a", "b", "c");
      final var q = fs.getPath("b", "c", "e", "f");
      assertFalse(
        p.endsWith(q),
        "%s does not end with %s".formatted(p, q)
      );
    }
  }

  @Test
  public void testAbsoluteEnds0()
    throws Exception
  {
    try (final var fs = createFS()) {
      final var p = fs.getPath(fs.getSeparator(), "a", "b", "c");
      final var q = fs.getPath("b", "c");
      assertTrue(
        p.endsWith(q),
        "%s ends with %s".formatted(p, q)
      );
    }
  }

  @Test
  public void testAbsoluteEnds1()
    throws Exception
  {
    try (final var fs = createFS()) {
      final var p = fs.getPath(fs.getSeparator(), "a", "b", "c");
      final var q = fs.getPath("B", "C");
      assertTrue(
        p.endsWith(q),
        "%s ends with %s".formatted(p, q)
      );
    }
  }

  @Test
  public void testAbsoluteStarts0()
    throws Exception
  {
    try (final var fs = createFS()) {
      final var p = fs.getPath(fs.getSeparator(), "a", "b", "c");
      final var q = fs.getPath(fs.getSeparator(),"A", "B");
      assertTrue(
        p.startsWith(q),
        "%s starts with %s".formatted(p, q)
      );
    }
  }

  @Test
  public void testAbsoluteStarts1()
    throws Exception
  {
    try (final var fs = createFS()) {
      final var p = fs.getPath(fs.getSeparator(), "a", "b", "c");
      final var q = fs.getPath(fs.getSeparator(),"a", "b");
      assertTrue(
        p.startsWith(q),
        "%s ends with %s".formatted(p, q)
      );
    }
  }
}
