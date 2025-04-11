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

import net.jcip.annotations.Immutable;

import java.net.URI;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.ProviderMismatchException;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.io7m.fsbind.core.internal.FBFS.FBFS_SEPARATOR;

/**
 * An absolute path.
 */

@Immutable
public final class FBFSPathAbsolute implements FBFSPathType
{
  static final List<String> ROOT_NAME =
    List.of("/");

  private final FBFS filesystem;
  private final List<String> components;

  /**
   * An absolute path.
   *
   * @param inFilesystem The filesystem that owns the path
   * @param inNames      The path components
   */

  public FBFSPathAbsolute(
    final FBFS inFilesystem,
    final List<String> inNames)
  {
    this.filesystem =
      Objects.requireNonNull(inFilesystem, "filesystem");
    this.components =
      validate(inNames);
  }

  private static List<String> validate(
    final List<String> inNames)
  {
    if (inNames.isEmpty()) {
      throw new IllegalArgumentException(
        "fsbind paths cannot be empty");
    }

    if (!Objects.equals(inNames.getFirst(), FBFS_SEPARATOR)) {
      throw new IllegalArgumentException(
        "fsbind absolute paths must have %s as the first element"
          .formatted(FBFS_SEPARATOR)
      );
    }

    for (int index = 1; index < inNames.size(); ++index) {
      FBFSPathComponents.validatePathComponent(inNames, inNames.get(index));
    }

    return List.copyOf(inNames);
  }

  @Override
  public List<String> components()
  {
    return this.components;
  }

  @Override
  public boolean equals(
    final Object o)
  {
    if (this == o) {
      return true;
    }
    if (!(o instanceof final FBFSPathAbsolute paths)) {
      return false;
    }
    return Objects.equals(this.filesystem, paths.filesystem)
           && Objects.equals(this.components, paths.components);
  }

  @Override
  public int hashCode()
  {
    return Objects.hash(this.filesystem, this.components);
  }

  @Override
  public FBFS getFileSystem()
  {
    return this.filesystem;
  }

  @Override
  public boolean isAbsolute()
  {
    return true;
  }

  @Override
  public FBFSPathAbsolute getRoot()
  {
    return new FBFSPathAbsolute(this.filesystem, ROOT_NAME);
  }

  @Override
  public FBFSPathRelative getFileName()
  {
    if (this.isRoot()) {
      return null;
    }

    return new FBFSPathRelative(
      this.filesystem,
      List.of(this.components.getLast())
    );
  }

  @Override
  public FBFSPathAbsolute getParent()
  {
    if (this.isRoot()) {
      return null;
    }

    return new FBFSPathAbsolute(
      this.filesystem,
      this.components.subList(0, this.components.size() - 1)
    );
  }

  @Override
  public int getNameCount()
  {
    return this.components.size() - 1;
  }

  @Override
  public Path getName(
    final int index)
  {
    return new FBFSPathRelative(
      this.filesystem,
      List.of(this.components.get(index + 1))
    );
  }

  @Override
  public Path subpath(
    final int beginIndex,
    final int endIndex)
  {
    return new FBFSPathRelative(
      this.filesystem,
      this.components.subList(beginIndex, endIndex)
    );
  }

  @Override
  public String toString()
  {
    return FBFS_SEPARATOR + String.join(
      FBFS_SEPARATOR,
      this.components.subList(1, this.components.size())
    );
  }

  @Override
  public boolean startsWith(
    final Path prefix)
  {
    final var prefixPath =
      this.checkPathAbsoluteCompatible(prefix);
    final var prefixSize =
      prefixPath.components.size();
    final var thisSize =
      this.components.size();

    if (prefixSize > thisSize) {
      return false;
    }

    return prefixPath.components.equals(this.components.subList(0, prefixSize));
  }

  private FBFSPathAbsolute checkPathAbsoluteCompatible(
    final Path other)
  {
    return switch (other) {
      case final FBFSPathAbsolute otherPath -> {
        if (Objects.equals(otherPath.filesystem, this.filesystem)) {
          yield otherPath;
        }
        throw new ProviderMismatchException(
          "Path '%s' is using a different fsbind filesystem."
            .formatted(other)
        );
      }
      case final FBFSPathRelative ignored -> {
        throw new ProviderMismatchException(
          "Path '%s' is relative but must be absolute."
            .formatted(other)
        );
      }
      default -> throw notAnFSBindPath(other);
    };
  }

  private FBFSPathRelative checkPathRelativeCompatible(
    final Path other)
  {
    return switch (other) {
      case final FBFSPathRelative otherPath -> {
        if (Objects.equals(otherPath.getFileSystem(), this.filesystem)) {
          yield otherPath;
        }
        throw new ProviderMismatchException(
          "Path '%s' is using a different fsbind filesystem."
            .formatted(other)
        );
      }
      case final FBFSPathAbsolute ignored -> {
        throw new ProviderMismatchException(
          "Path '%s' is absolute but must be relative."
            .formatted(other)
        );
      }
      default -> throw notAnFSBindPath(other);
    };
  }

  private static ProviderMismatchException notAnFSBindPath(
    final Path other)
  {
    return new ProviderMismatchException(
      "Path '%s' is not compatible with the fsbind filesystem provider."
        .formatted(other)
    );
  }

  @Override
  public boolean endsWith(
    final Path suffix)
  {
    final var suffixPath =
      this.checkPathRelativeCompatible(suffix);
    final var suffixSize =
      suffixPath.components().size();
    final var thisSize =
      this.components.size();

    if (suffixSize > thisSize) {
      return false;
    }

    return suffixPath.components().equals(
      this.components.subList(thisSize - suffixSize, thisSize)
    );
  }

  @Override
  public Path normalize()
  {
    return this;
  }

  @Override
  public Path resolve(
    final Path other)
  {
    return switch (other) {
      case final FBFSPathType fbOther -> {
        yield switch (fbOther) {
          case final FBFSPathAbsolute otherAbs -> {
            yield otherAbs;
          }
          case final FBFSPathRelative otherRel -> {
            final var size = this.components.size() + otherRel.getNameCount();
            final var newNames = new ArrayList<String>(size);
            newNames.addAll(this.components);
            newNames.addAll(otherRel.components());
            yield new FBFSPathAbsolute(this.filesystem, newNames);
          }
        };
      }
      default -> throw notAnFSBindPath(other);
    };
  }

  @Override
  public FBFSPathRelative relativize(
    final Path other)
  {
    return switch (other) {
      case final FBFSPathType fbOther -> {
        yield switch (fbOther) {
          case final FBFSPathAbsolute fbOtherAbs -> {
            if (fbOtherAbs.startsWith(this)) {
              final var relativeNames =
                fbOtherAbs.components.subList(
                  this.components.size(),
                  fbOtherAbs.components.size()
                );
              yield new FBFSPathRelative(this.filesystem, relativeNames);
            }

            throw new UnsupportedOperationException();
          }
          case final FBFSPathRelative ignored -> {
            throw new UnsupportedOperationException();
          }
        };
      }
      default -> throw notAnFSBindPath(other);
    };
  }

  @Override
  public URI toUri()
  {
    return URI.create(
      "fsbind:%s:%s".formatted(this.filesystem.name(), this)
    );
  }

  @Override
  public Path toAbsolutePath()
  {
    return this;
  }

  @Override
  public Path toRealPath(
    final LinkOption... options)
  {
    return this;
  }

  @Override
  public WatchKey register(
    final WatchService watcher,
    final WatchEvent.Kind<?>[] events,
    final WatchEvent.Modifier... modifiers)
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public int compareTo(
    final Path other)
  {
    return this.toString().compareTo(other.toString());
  }

  /**
   * @return {@code true} if this path is the root path
   */

  public boolean isRoot()
  {
    return this.components.size() == 1;
  }
}
