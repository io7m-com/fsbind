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

import static com.io7m.fsbind.core.internal.FBFSPathAbsolute.ROOT_NAME;
import static com.io7m.fsbind.core.internal.FBFSPathComponents.componentsEquals;

/**
 * A relative path.
 */

@Immutable
public final class FBFSPathRelative implements FBFSPathType
{
  private final FBFS filesystem;
  private final List<String> components;

  /**
   * A relative path.
   *
   * @param inFilesystem The filesystem that owns the path
   * @param inNames      The path components
   */

  public FBFSPathRelative(
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

    for (int index = 0; index < inNames.size(); ++index) {
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
    if (!(o instanceof final FBFSPathRelative paths)) {
      return false;
    }
    return Objects.equals(this.filesystem, paths.filesystem)
           && componentsEquals(this.components, paths.components);
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
    return false;
  }

  @Override
  public FBFSPathAbsolute getRoot()
  {
    return new FBFSPathAbsolute(this.filesystem, ROOT_NAME);
  }

  @Override
  public FBFSPathRelative getFileName()
  {
    return new FBFSPathRelative(
      this.filesystem,
      List.of(this.components.getLast())
    );
  }

  @Override
  public FBFSPathRelative getParent()
  {
    final var sub =
      this.components.subList(0, this.components.size() - 1);

    if (sub.isEmpty()) {
      return null;
    }

    return new FBFSPathRelative(
      this.filesystem,
      sub
    );
  }

  @Override
  public int getNameCount()
  {
    return this.components.size();
  }

  @Override
  public FBFSPathRelative getName(
    final int index)
  {
    return new FBFSPathRelative(
      this.filesystem,
      List.of(this.components.get(index))
    );
  }

  @Override
  public FBFSPathRelative subpath(
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
    return String.join("/", this.components);
  }

  @Override
  public boolean startsWith(
    final Path prefix)
  {
    final var prefixPath =
      this.checkPath(prefix);
    final var prefixSize =
      prefixPath.components.size();
    final var thisSize =
      this.components.size();

    if (prefixSize > thisSize) {
      return false;
    }

    return componentsEquals(
      prefixPath.components,
      this.components.subList(0, prefixSize)
    );
  }

  private FBFSPathRelative checkPath(
    final Path other)
  {
    if (other instanceof final FBFSPathRelative otherPath) {
      if (Objects.equals(otherPath.filesystem, this.filesystem)) {
        return otherPath;
      }
      throw new ProviderMismatchException(
        "Path '%s' is using a different fsbind filesystem."
          .formatted(other)
      );
    }
    throw new ProviderMismatchException(
      "Path '%s' is not compatible with the fsbind filesystem provider."
        .formatted(other)
    );
  }

  @Override
  public boolean endsWith(
    final Path suffix)
  {
    final var suffixPath =
      this.checkPath(suffix);
    final var suffixSize =
      suffixPath.components.size();
    final var thisSize =
      this.components.size();

    if (suffixSize > thisSize) {
      return false;
    }

    return componentsEquals(
      suffixPath.components,
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
    final var otherRel =
      this.checkPath(other);

    final var names = new ArrayList<String>(
      this.getNameCount() + otherRel.getNameCount()
    );
    names.addAll(this.components);
    names.addAll(otherRel.components);

    return new FBFSPathRelative(
      this.filesystem,
      names
    );
  }

  @Override
  public Path relativize(
    final Path other)
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public URI toUri()
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public Path toAbsolutePath()
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public Path toRealPath(
    final LinkOption... options)
  {
    throw new UnsupportedOperationException();
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
    return this.toString().compareToIgnoreCase(other.toString());
  }
}
