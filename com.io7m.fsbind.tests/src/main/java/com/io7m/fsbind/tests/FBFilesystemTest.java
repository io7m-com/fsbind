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

import com.io7m.fsbind.core.FBFilesystem;
import com.io7m.fsbind.core.FBFilesystemProvider;
import com.io7m.fsbind.core.FBMountRequest;
import com.io7m.fsbind.core.FBMountedFilesystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileSystemException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.ReadOnlyFileSystemException;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class FBFilesystemTest
{
  private static final Logger LOG =
    LoggerFactory.getLogger(FBFilesystemTest.class);

  public static final URI FSBIND =
    URI.create("fsbind:x:/");
  public static final URI FSBIND_OTHER =
    URI.create("fsbind:y:/");

  private Path directory;

  @BeforeEach
  public void setup(
    final @TempDir Path directory)
  {
    this.directory = directory;
  }

  private Path resource(
    final String name)
    throws IOException
  {
    final var inPath = "/com/io7m/fsbind/tests/" + name;
    try (final var stream = FBFilesystemTest.class.getResourceAsStream(inPath)) {
      final var outPath = this.directory.resolve(name);
      Files.copy(stream, outPath);
      return outPath;
    }
  }

  @Test
  public void testGetNonexistent()
  {
    assertThrows(FileSystemNotFoundException.class, () -> {
      FileSystems.getFileSystem(FSBIND);
    });
  }

  @Test
  public void testCreateClose()
    throws Exception
  {
    try (final var fs = createFS()) {
      assertNotNull(fs.toString());
      assertInstanceOf(FBFilesystem.class, fs);
    }
  }

  private record PathParameters(
    String first,
    String... rest)
  {

  }

  @TestFactory
  public Stream<DynamicTest> testGetPathValid()
  {
    return Stream.of(
      new PathParameters("/"),
      new PathParameters("/", "A"),
      new PathParameters("/", "A", "B"),
      new PathParameters("A", "B")
    ).map(this::testPathValid);
  }

  private DynamicTest testPathValid(
    final PathParameters parameters)
  {
    return DynamicTest.dynamicTest(
      "testPathValid_(%s)".formatted(parameters),
      () -> {
        try (final var fs =
               createFS()) {
          final var p =
            fs.getPath(
              parameters.first,
              parameters.rest
            );
          LOG.trace("Path: {}", p);
        }
      });
  }

  @TestFactory
  public Stream<DynamicTest> testGetPathInvalid()
  {
    return Stream.of(
      new PathParameters(""),
      new PathParameters("/z"),
      new PathParameters("/", "/"),
      new PathParameters("/", "."),
      new PathParameters("/", ".."),
      new PathParameters("/", "..."),
      new PathParameters("."),
      new PathParameters(".."),
      new PathParameters("...")
    ).map(this::testPathInvalid);
  }

  private DynamicTest testPathInvalid(
    final PathParameters parameters)
  {
    return DynamicTest.dynamicTest(
      "testPathInvalid_(%s)".formatted(parameters),
      () -> {
        try (final var fs =
               createFS()) {

          assertThrows(IllegalArgumentException.class, () -> {
            fs.getPath(parameters.first, parameters.rest);
          });
        }
      });
  }

  @Test
  public void testListRootEmpty()
    throws Exception
  {
    try (final var fs = createFS()) {
      assertEquals(
        List.of(),
        Files.list(fs.getPath("/")).toList()
      );
    }
  }

  @Test
  public void testListNonexistentEmpty()
    throws Exception
  {
    try (final var fs = createFS()) {
      assertThrows(
        NoSuchFileException.class, () -> {
          Files.list(fs.getPath("/", "nonexistent"));
        }
      );
    }
  }

  @Test
  public void testListRootCreateDelete()
    throws Exception
  {
    try (final var fs = createFS()) {
      assertEquals(
        List.of(),
        Files.list(fs.getPath("/")).toList()
      );

      Files.createDirectory(fs.getPath("/"));
      Files.createDirectories(fs.getPath("/", "a"));
      Files.createDirectories(fs.getPath("/", "b"));
      Files.createDirectories(fs.getPath("/", "c"));

      assertEquals(
        List.of("a", "b", "c"),
        Files.list(fs.getPath("/"))
          .map(Path::getFileName)
          .map(Path::toString)
          .toList()
      );

      Files.delete(fs.getPath("/", "b"));

      assertEquals(
        List.of("a", "c"),
        Files.list(fs.getPath("/"))
          .map(Path::getFileName)
          .map(Path::toString)
          .toList()
      );
    }
  }

  @Test
  public void testListRootCreateDeleteNotEmpty()
    throws Exception
  {
    try (final var fs = createFS()) {
      Files.createDirectories(fs.getPath("/", "a", "b"));

      assertThrows(DirectoryNotEmptyException.class, () -> {
        Files.delete(fs.getPath("/", "a"));
      });
    }
  }

  @Test
  public void testListRootDeleteRoot()
    throws Exception
  {
    try (final var fs = createFS()) {
      assertThrows(AccessDeniedException.class, () -> {
        Files.delete(fs.getPath("/"));
      });
    }
  }

  @Test
  public void testMountZip()
    throws Exception
  {
    final var zip =
      this.resource("nested.zip");
    final var zipFileUri =
      zip.toUri();
    final var zipUri =
      URI.create("jar:file:" + zipFileUri.getPath());

    try (final var zipfs = FileSystems.newFileSystem(zipUri, Map.of(), null)) {
      try (final var fs = createFS()) {
        final var dir = fs.getPath("/", "a");
        Files.createDirectories(dir);

        fs.mount(new FBMountRequest(
          zipfs.getRootDirectories().iterator().next(),
          dir
        ));

        final var aTxt = dir.resolve("x").resolve("a.txt");
        assertEquals(
          "Hello X A\n",
          Files.readString(aTxt)
        );

        try (final var chan = FileChannel.open(aTxt)) {
          assertEquals(
            10L,
            chan.size()
          );
        }

        assertThrows(NoSuchFileException.class, () -> {
          FileChannel.open(dir.resolve("x"));
        });
        assertThrows(FileSystemException.class, () -> {
          FileChannel.open(dir);
        });
        assertThrows(FileSystemException.class, () -> {
          FileChannel.open(fs.getPath("/"));
        });

        assertThrows(NoSuchFileException.class, () -> {
          Files.newByteChannel(dir.resolve("x"));
        });
        assertThrows(FileSystemException.class, () -> {
          Files.newByteChannel(dir);
        });
        assertThrows(FileSystemException.class, () -> {
          Files.newByteChannel(fs.getPath("/"));
        });

        assertEquals(
          List.of("x", "y", "z"),
          Files.list(dir)
            .map(Path::getFileName)
            .map(Path::toString)
            .sorted()
            .toList()
        );
        assertEquals(
          List.of("a.txt", "b.txt", "c.txt"),
          Files.list(dir.resolve("x"))
            .map(Path::getFileName)
            .map(Path::toString)
            .sorted()
            .toList()
        );
      }
    }
  }

  @Test
  public void testMountZipReadOnly()
    throws Exception
  {
    final var zip =
      this.resource("nested.zip");
    final var zipFileUri =
      zip.toUri();
    final var zipUri =
      URI.create("jar:file:" + zipFileUri.getPath());

    try (final var zipfs = FileSystems.newFileSystem(zipUri, Map.of(), null)) {
      try (final var fs = createFS()) {
        final var dir = fs.getPath("/", "a");
        Files.createDirectories(dir);

        fs.mount(new FBMountRequest(
          zipfs.getRootDirectories().iterator().next(),
          dir
        ));

        assertThrows(ReadOnlyFileSystemException.class, () -> {
          Files.delete(dir.resolve("x"));
        });
        assertThrows(ReadOnlyFileSystemException.class, () -> {
          Files.delete(dir);
        });
        assertThrows(ReadOnlyFileSystemException.class, () -> {
          Files.createDirectory(dir.resolve("x"));
        });
        assertThrows(ReadOnlyFileSystemException.class, () -> {
          Files.createDirectory(dir.resolve("x").resolve("a.txt"));
        });
        assertThrows(ReadOnlyFileSystemException.class, () -> {
          Files.createDirectory(dir.resolve("x").resolve("a.txt").resolve(
            "b.txt"));
        });
        assertThrows(ReadOnlyFileSystemException.class, () -> {
          FileChannel.open(
            dir.resolve("x").resolve("a.txt"),
            StandardOpenOption.WRITE);
        });
      }
    }
  }

  @Test
  public void testMountDirectory(
    final @TempDir Path mountDir)
    throws Exception
  {
    Files.createDirectories(mountDir.resolve("x"));
    Files.createDirectories(mountDir.resolve("y"));
    Files.createDirectories(mountDir.resolve("z"));

    Files.writeString(
      mountDir.resolve("y").resolve("a.txt"),
      "Hello!"
    );

    try (final var fs = createFS()) {
      final var dir = fs.getPath("/", "a");
      Files.createDirectories(dir);

      fs.mount(new FBMountRequest(mountDir, dir));

      assertEquals(
        "Hello!",
        Files.readString(dir.resolve("y").resolve("a.txt"))
      );

      assertThrows(ReadOnlyFileSystemException.class, () -> {
        Files.writeString(dir.resolve("y").resolve("a.txt"), "X!");
      });
    }
  }

  @Test
  public void testMountUnmount()
    throws Exception
  {
    final var zip =
      this.resource("nested.zip");
    final var zipFileUri =
      zip.toUri();
    final var zipUri =
      URI.create("jar:file:" + zipFileUri.getPath());

    try (final var zipfs = FileSystems.newFileSystem(zipUri, Map.of(), null)) {
      try (final var fs = createFS()) {
        final var dir = fs.getPath("/", "a");
        Files.createDirectories(dir);

        assertThrows(FileSystemException.class, () -> {
          fs.unmount(fs.getPath("/"));
        });

        final var root = zipfs.getRootDirectories().iterator().next();
        fs.mount(new FBMountRequest(root, dir));
        assertEquals(
          List.of(new FBMountedFilesystem(dir, root)),
          fs.mountedFilesystems()
        );

        final var aTxt = dir.resolve("x").resolve("a.txt");
        assertEquals("Hello X A\n", Files.readString(aTxt));

        assertThrows(FileSystemException.class, () -> {
          fs.unmount(aTxt);
        });

        fs.unmount(dir);
        assertFalse(Files.exists(aTxt));
        assertEquals(List.of(), fs.mountedFilesystems());
      }
    }
  }

  @Test
  @Timeout(value = 5L, unit = TimeUnit.SECONDS)
  public void testWatchFileModified(
    final @TempDir Path mountDir)
    throws Exception
  {
    Files.writeString(mountDir.resolve("a.txt"), "Hello!");

    try (final var fs = createFS()) {
      final var dir = fs.getPath("/", "z");
      final var f = dir.resolve("a.txt");
      Files.createDirectories(dir);
      fs.mount(new FBMountRequest(mountDir, dir));

      try (final var watch = fs.newWatchService()) {
        f.register(watch, ENTRY_MODIFY);

        Thread.ofVirtual()
          .start(() -> {
            LOG.trace("Waiting to write...");

            try {
              Thread.sleep(1_000L);
            } catch (final InterruptedException e) {
              Thread.currentThread().interrupt();
            }

            LOG.trace("Writing...");

            try {
              Files.writeString(mountDir.resolve("a.txt"), "Goodbye!");
            } catch (final IOException e) {
              throw new RuntimeException(e);
            }

            LOG.trace("Written.");
          });

        while (true) {
          final var wk = watch.take();
          for (final var event : wk.pollEvents()) {
            final var changed = (Path) event.context();
            assertEquals(changed, f);
            return;
          }
          wk.reset();
        }
      }
    }
  }

  @Test
  @Timeout(value = 5L, unit = TimeUnit.SECONDS)
  public void testWatchFileCreated(
    final @TempDir Path mountDir)
    throws Exception
  {
    try (final var fs = createFS()) {
      final var dir = fs.getPath("/", "z");
      final var f = dir.resolve("a.txt");
      Files.createDirectories(dir);
      fs.mount(new FBMountRequest(mountDir, dir));

      try (final var watch = fs.newWatchService()) {
        f.register(watch, ENTRY_CREATE);

        Thread.ofVirtual()
          .start(() -> {
            LOG.trace("Waiting to write...");

            try {
              Thread.sleep(1_000L);
            } catch (final InterruptedException e) {
              Thread.currentThread().interrupt();
            }

            LOG.trace("Writing...");

            try {
              Files.writeString(mountDir.resolve("a.txt"), "Goodbye!");
            } catch (final IOException e) {
              throw new RuntimeException(e);
            }

            LOG.trace("Written.");
          });

        while (true) {
          final var wk = watch.take();
          for (final var event : wk.pollEvents()) {
            final var changed = (Path) event.context();
            assertEquals(changed, f);
            return;
          }
          wk.reset();
        }
      }
    }
  }

  @Test
  // @Timeout(value = 5L, unit = TimeUnit.SECONDS)
  public void testWatchFileDeleted(
    final @TempDir Path mountDir)
    throws Exception
  {
    Files.writeString(mountDir.resolve("a.txt"), "Goodbye!");

    try (final var fs = createFS()) {
      final var dir = fs.getPath("/", "z");
      final var f = dir.resolve("a.txt");
      Files.createDirectories(dir);
      fs.mount(new FBMountRequest(mountDir, dir));

      try (final var watch = fs.newWatchService()) {
        f.register(watch, ENTRY_DELETE);

        Thread.ofVirtual()
          .start(() -> {
            LOG.trace("Waiting to delete...");

            try {
              Thread.sleep(1_000L);
            } catch (final InterruptedException e) {
              Thread.currentThread().interrupt();
            }

            LOG.trace("Deleting...");

            try {
              Files.delete(mountDir.resolve("a.txt"));
            } catch (final IOException e) {
              throw new RuntimeException(e);
            }

            LOG.trace("Deleted.");
          });

        while (true) {
          final var wk = watch.take();
          for (final var event : wk.pollEvents()) {
            final var changed = (Path) event.context();
            assertEquals(changed, f);
            return;
          }
          wk.reset();
        }
      }
    }
  }

  @Test
  public void testDirectoryRename()
    throws Exception
  {
    try (final var fs = createFS()) {
      final var p = fs.getPath("/", "a");
      final var q = fs.getPath("/", "b");
      final var r = fs.getPath("/", "c");

      Files.createDirectory(p);
      Files.createDirectory(r);

      assertTrue(Files.isDirectory(p));
      assertFalse(Files.exists(q));
      assertTrue(Files.isDirectory(r));

      Files.move(p, q);

      assertFalse(Files.exists(p));
      assertTrue(Files.isDirectory(q));
      assertTrue(Files.isDirectory(r));

      assertThrows(FileSystemException.class, () -> {
        Files.move(q, r);
      });
      assertTrue(Files.isDirectory(q));
      assertTrue(Files.isDirectory(r));
    }
  }

  static FBFilesystem createFS()
  {
    try {
      return (FBFilesystem) FileSystems.newFileSystem(
        FSBIND,
        Map.of(
          FBFilesystemProvider.environmentWatchServiceDurationKey(),
          Duration.ofMillis(250L)
        )
      );
    } catch (final IOException e) {
      throw new AssertionError(e);
    }
  }

  static FBFilesystem createFSOther()
  {
    try {
      return (FBFilesystem) FileSystems.newFileSystem(FSBIND_OTHER, null);
    } catch (final IOException e) {
      throw new AssertionError(e);
    }
  }
}
