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


package com.io7m.fsbind.core;

import com.io7m.fsbind.core.internal.FBFS;
import com.io7m.fsbind.core.internal.FBFSPathAbsolute;
import com.io7m.fsbind.core.internal.FBFSPathRelative;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.FileStore;
import java.nio.file.FileSystemException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.ProviderMismatchException;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.spi.FileSystemProvider;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * The {@code fsbind} filesystem provider.
 */

public final class FBFilesystemProvider
  extends FileSystemProvider
{
  private static final Logger LOG =
    LoggerFactory.getLogger(FBFilesystemProvider.class);

  private final Object filesystemsLock;
  private final HashMap<String, FBFS> filesystems;

  /**
   * The {@code fsbind} filesystem provider.
   */

  public FBFilesystemProvider()
  {
    this.filesystems = new HashMap<>();
    this.filesystemsLock = new Object();
  }

  @Override
  public String getScheme()
  {
    return "fsbind";
  }

  @Override
  public FBFilesystem newFileSystem(
    final URI uri,
    final Map<String, ?> env)
  {
    return this.filesystemOf(uri, true);
  }

  private record FBFilesystemURI(
    String scheme,
    String name,
    String path)
  {

  }

  private static FBFilesystemURI filesystemURIOf(
    final URI uri)
  {
    final var scheme =
      uri.getSchemeSpecificPart();
    final var nameColon =
      scheme.indexOf(':');

    if (nameColon == -1) {
      throw new IllegalArgumentException(
        "fsbind URI '%s' must be of the form 'fsbind:<name>:<path>'"
          .formatted(uri)
      );
    }

    final var name =
      scheme.substring(0, nameColon);
    final var rest =
      scheme.substring(nameColon);

    return new FBFilesystemURI(
      "fsbind",
      name,
      rest
    );
  }

  private FBFilesystem filesystemOf(
    final URI uri,
    final boolean create)
  {
    final var fsuri = filesystemURIOf(uri);

    synchronized (this.filesystemsLock) {
      final var existing = this.filesystems.get(fsuri.name);
      if (existing != null) {
        return existing;
      }

      if (create) {
        final var newFs = new FBFS(this, fsuri.name);
        newFs.setOnClose(() -> {
          synchronized (this.filesystemsLock) {
            this.filesystems.remove(fsuri.name);
          }
        });

        LOG.trace("CreateFilesystem: {}", fsuri.name);
        this.filesystems.put(fsuri.name, newFs);
        return newFs;
      }
      throw new FileSystemNotFoundException(uri.toString());
    }
  }

  @Override
  public FBFilesystem getFileSystem(
    final URI uri)
  {
    return this.filesystemOf(uri, false);
  }

  @Override
  public Path getPath(
    final URI uri)
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public SeekableByteChannel newByteChannel(
    final Path path,
    final Set<? extends OpenOption> options,
    final FileAttribute<?>... attrs)
    throws IOException
  {
    if (path instanceof final FBFSPathAbsolute absPath) {
      return absPath.getFileSystem().opNewByteChannel(absPath, options);
    } else {
      throw absolutePathRequired(path);
    }
  }

  @Override
  public DirectoryStream<Path> newDirectoryStream(
    final Path dir,
    final DirectoryStream.Filter<? super Path> filter)
    throws IOException
  {
    if (dir instanceof final FBFSPathAbsolute path) {
      return path.getFileSystem().opNewDirectoryStream(path, filter);
    } else {
      throw absolutePathRequired(dir);
    }
  }

  private static RuntimeException absolutePathRequired(
    final Path dir)
  {
    if (dir instanceof FBFSPathRelative) {
      return new IllegalArgumentException(
        "Path '%s' must be an absolute fsbind path."
          .formatted(dir)
      );
    }

    return new ProviderMismatchException(
      "Path '%s' must be an absolute fsbind path (but is of type %s)."
        .formatted(dir, dir.getClass().getName())
    );
  }

  @Override
  public void createDirectory(
    final Path dir,
    final FileAttribute<?>... attrs)
    throws FileSystemException
  {
    if (dir instanceof final FBFSPathAbsolute path) {
      path.getFileSystem().opCreateDirectory(path);
    } else {
      throw absolutePathRequired(dir);
    }
  }

  @Override
  public FileChannel newFileChannel(
    final Path path,
    final Set<? extends OpenOption> options,
    final FileAttribute<?>... attrs)
    throws IOException
  {
    if (path instanceof final FBFSPathAbsolute absPath) {
      return absPath.getFileSystem().opNewFileChannel(absPath, options);
    } else {
      throw absolutePathRequired(path);
    }
  }

  @Override
  public void delete(
    final Path path)
    throws FileSystemException
  {
    if (path instanceof final FBFSPathAbsolute absPath) {
      absPath.getFileSystem().opDelete(absPath);
    } else {
      throw absolutePathRequired(path);
    }
  }

  @Override
  public void copy(
    final Path source,
    final Path target,
    final CopyOption... options)
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public void move(
    final Path source,
    final Path target,
    final CopyOption... options)
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isSameFile(
    final Path path,
    final Path path2)
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isHidden(
    final Path path)
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public FileStore getFileStore(
    final Path path)
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public void checkAccess(
    final Path path,
    final AccessMode... modes)
    throws FileSystemException
  {
    if (path instanceof final FBFSPathAbsolute absPath) {
      absPath.getFileSystem().opCheckAccess(absPath, modes);
    } else {
      throw absolutePathRequired(path);
    }
  }

  @Override
  public <V extends FileAttributeView> V getFileAttributeView(
    final Path path,
    final Class<V> type,
    final LinkOption... options)
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public <A extends BasicFileAttributes> A readAttributes(
    final Path path,
    final Class<A> type,
    final LinkOption... options)
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public Map<String, Object> readAttributes(
    final Path path,
    final String attributes,
    final LinkOption... options)
  {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setAttribute(
    final Path path,
    final String attribute,
    final Object value,
    final LinkOption... options)
  {
    throw new UnsupportedOperationException();
  }
}
