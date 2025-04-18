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

import com.io7m.fsbind.core.FBFilesystem;
import com.io7m.fsbind.core.FBFilesystemProvider;
import com.io7m.fsbind.core.FBMountRequest;
import com.io7m.fsbind.core.FBMountedFilesystem;
import com.io7m.jaffirm.core.Preconditions;
import com.io7m.jmulticlose.core.CloseableCollection;
import com.io7m.jmulticlose.core.CloseableCollectionType;
import com.io7m.jmulticlose.core.ClosingResourceFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileStore;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.ProviderMismatchException;
import java.nio.file.ReadOnlyFileSystemException;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The {@code fsbind} filesystem.
 */

public final class FBFS
  extends FBFilesystem
{
  static final String FBFS_SEPARATOR = "/";

  private static final Logger LOG =
    LoggerFactory.getLogger(FBFS.class);

  private static final String IS_DIRECTORY =
    "The target path is a directory";
  private static final String NOT_A_FILESYSTEM_MOUNT =
    "Not a filesystem mount.";

  private final FBFilesystemProvider provider;
  private final String name;
  private final AtomicBoolean closed;
  private final CloseableCollectionType<ClosingResourceFailedException> resources;
  private final CopyOnWriteArrayList<FBMount> mounts;
  private final FBFSTree tree;
  private final FBFSPathAbsolute rootPath;
  private final Map<String, ?> environment;

  /**
   * The {@code fsbind} filesystem.
   *
   * @param inProvider The provider
   * @param inName     The filesystem name
   * @param env        The environment
   */

  public FBFS(
    final FBFilesystemProvider inProvider,
    final String inName,
    final Map<String, ?> env)
  {
    this.provider =
      Objects.requireNonNull(inProvider, "provider");
    this.name =
      Objects.requireNonNull(inName, "name");
    this.closed =
      new AtomicBoolean(false);

    final var rootNode =
      new FBFSObjectVirtualDirectory(
        new FBVirtualDirectoryAttributes(),
        "/",
        Optional.empty()
      );

    final var now = FileTime.from(Instant.now());
    rootNode.attributes().setCreationTime(now);
    rootNode.attributes().setLastAccessTime(now);
    rootNode.attributes().setLastModifiedTime(now);

    this.tree =
      FBFSTree.createWithRoot(rootNode);
    this.environment =
      Map.copyOf(env);
    this.mounts =
      new CopyOnWriteArrayList<>();
    this.rootPath =
      new FBFSPathAbsolute(this, List.of(FBFS_SEPARATOR));
    this.resources =
      CloseableCollection.create();
  }

  @Override
  public FBFilesystemProvider provider()
  {
    return this.provider;
  }

  @Override
  public void close()
    throws IOException
  {
    if (this.closed.compareAndSet(false, true)) {
      try {
        this.resources.close();
      } catch (final ClosingResourceFailedException e) {
        throw new IOException(e);
      }
    }
  }

  @Override
  public String toString()
  {
    return "[FSBind %s 0x%s]".formatted(
      this.name,
      Integer.toUnsignedString(this.hashCode(), 16)
    );
  }

  @Override
  public boolean isOpen()
  {
    return !this.closed.get();
  }

  @Override
  public boolean isReadOnly()
  {
    return true;
  }

  @Override
  public String getSeparator()
  {
    return FBFS_SEPARATOR;
  }

  @Override
  public Iterable<Path> getRootDirectories()
  {
    this.checkNotClosed();
    return List.of(this.rootPath);
  }

  @Override
  public Iterable<FileStore> getFileStores()
  {
    this.checkNotClosed();
    return List.of();
  }

  @Override
  public Set<String> supportedFileAttributeViews()
  {
    this.checkNotClosed();
    return Set.of("basic");
  }

  @Override
  public Path getPath(
    final String first,
    final String... more)
  {
    this.checkNotClosed();

    if (Objects.equals(first, FBFS_SEPARATOR)) {
      final var names = new ArrayList<String>(1 + more.length);
      names.add(first);
      Collections.addAll(names, more);
      return new FBFSPathAbsolute(this, names);
    }

    final var names = new ArrayList<String>(1 + more.length);
    names.add(first);
    Collections.addAll(names, more);
    return new FBFSPathRelative(this, names);
  }

  @Override
  public PathMatcher getPathMatcher(
    final String syntaxAndPattern)
  {
    this.checkNotClosed();

    throw new UnsupportedOperationException();
  }

  @Override
  public UserPrincipalLookupService getUserPrincipalLookupService()
  {
    this.checkNotClosed();

    throw new UnsupportedOperationException();
  }

  @Override
  public WatchService newWatchService()
  {
    this.checkNotClosed();

    return new FBFSWatchService(
      (Duration) this.environment.get(
        FBFilesystemProvider.environmentWatchServiceDurationKey()
      )
    );
  }

  /**
   * Set the operation to be executed when this filesystem is closed.
   *
   * @param newOnClose The operation
   */

  public void setOnClose(
    final Runnable newOnClose)
  {
    this.resources.add(newOnClose::run);
  }

  /**
   * @param path   The path
   * @param filter The filter
   *
   * @return A new directory stream
   *
   * @throws IOException On errors
   * @see Files#newDirectoryStream(Path, DirectoryStream.Filter)
   */

  @FSOp
  public DirectoryStream<Path> opNewDirectoryStream(
    final FBFSPathAbsolute path,
    final DirectoryStream.Filter<? super Path> filter)
    throws IOException
  {
    this.checkNotClosed();
    this.checkPathBelongs(path);

    return switch (new FBLookup(path).lookup()) {
      case final FBFSObjectMount mount -> {
        yield Files.newDirectoryStream(mount.mount().basePath(), filter);
      }
      case final FBFSObjectReal real -> {
        yield Files.newDirectoryStream(real.path(), filter);
      }
      case final FBFSObjectVirtualDirectory directory -> {
        yield this.tree.list(path, directory, filter);
      }
    };
  }

  /**
   * @param path The file
   *
   * @throws FileSystemException On errors
   * @see Files#createDirectory(Path, FileAttribute[])
   */

  @FSOp
  public void opCreateDirectory(
    final FBFSPathAbsolute path)
    throws FileSystemException
  {
    if (LOG.isTraceEnabled()) {
      LOG.trace("CreateDirectory: {}", path);
    }

    this.checkNotClosed();
    this.checkPathBelongs(path);

    if (path.isRoot()) {
      return;
    }

    switch (new FBLookup(path.getParent()).lookup()) {
      case final FBFSObjectMount ignored -> {
        throw new ReadOnlyFileSystemException();
      }
      case final FBFSObjectReal ignored -> {
        throw new ReadOnlyFileSystemException();
      }
      case final FBFSObjectVirtualDirectory parentNode -> {
        final var newNode =
          new FBFSObjectVirtualDirectory(
            new FBVirtualDirectoryAttributes(),
            path.components().getLast(),
            Optional.empty()
          );

        final var now = FileTime.from(Instant.now());
        newNode.attributes().setCreationTime(now);
        newNode.attributes().setLastAccessTime(now);
        newNode.attributes().setLastModifiedTime(now);

        if (this.tree.appendIfNameFree(parentNode, newNode)) {
          if (LOG.isTraceEnabled()) {
            LOG.trace("CreatedDirectory: {}", path);
          }
        }
      }
    }
  }

  private void checkNotClosed()
  {
    Preconditions.checkPrecondition(
      this.isOpen(),
      "Filesystem must be open."
    );
  }

  /**
   * @param path The file
   *
   * @throws FileSystemException On errors
   * @see Files#delete(Path)
   */

  @FSOp
  public void opDelete(
    final FBFSPathAbsolute path)
    throws FileSystemException
  {
    if (LOG.isTraceEnabled()) {
      LOG.trace("DeleteDirectory: {}", path);
    }

    this.checkNotClosed();
    this.checkPathBelongs(path);

    if (path.isRoot()) {
      throw new AccessDeniedException("Cannot delete the root directory");
    }

    final var lookup = new FBLookup(path);
    switch (lookup.lookup()) {
      case final FBFSObjectMount ignored -> {
        throw new ReadOnlyFileSystemException();
      }
      case final FBFSObjectReal ignored -> {
        throw new ReadOnlyFileSystemException();
      }
      case final FBFSObjectVirtualDirectory virtual -> {
        if (this.tree.deleteIfEmpty(virtual)) {
          if (LOG.isTraceEnabled()) {
            LOG.trace("DeletedDirectory: {}", path);
          }
        } else {
          throw new DirectoryNotEmptyException(path.toString());
        }
      }
    }
  }

  private void checkPathBelongs(
    final FBFSPathAbsolute path)
  {
    Preconditions.checkPrecondition(
      Objects.equals(path.getFileSystem(), this),
      "Path belongs to this filesystem."
    );
  }

  /**
   * Check access for a file.
   *
   * @param path  The file
   * @param modes The modes
   *
   * @throws FileSystemException On errors
   */

  @FSOp
  public void opCheckAccess(
    final FBFSPathAbsolute path,
    final AccessMode[] modes)
    throws FileSystemException
  {
    final var modeList = List.of(modes);
    if (LOG.isTraceEnabled()) {
      LOG.trace("CheckAccess: {} {}", path, modeList);
    }

    this.checkNotClosed();
    this.checkPathBelongs(path);

    new FBLookup(path).lookup();
    if (modeList.contains(AccessMode.WRITE)) {
      throw new ReadOnlyFileSystemException();
    }
  }

  @Override
  public List<FBMountedFilesystem> mountedFilesystems()
  {
    return this.mounts.stream()
      .map(m -> new FBMountedFilesystem(m.mountPoint(), m.basePath()))
      .toList();
  }

  @Override
  public void mount(
    final FBMountRequest mount)
    throws FileSystemException
  {
    this.checkNotClosed();

    if (mount.mountAt() instanceof final FBFSPathAbsolute mountAt) {
      this.checkPathBelongs(mountAt);
      this.opMount(
        mount.mountPathWithinFilesystem(),
        mountAt
      );
    } else {
      throw new ProviderMismatchException();
    }
  }

  @Override
  public void unmount(
    final Path path)
    throws FileSystemException
  {
    this.checkNotClosed();

    if (path instanceof final FBFSPathAbsolute mountAt) {
      this.checkPathBelongs(mountAt);
      this.opUnmount(mountAt);
    } else {
      throw new ProviderMismatchException();
    }
  }

  @FSOp
  private void opUnmount(
    final FBFSPathAbsolute path)
    throws FileSystemException
  {
    if (LOG.isTraceEnabled()) {
      LOG.trace("Unmount {}", path);
    }

    switch (new FBLookup(path).lookup()) {
      case final FBFSObjectMount mount -> {
        this.tree.unmount(mount);
        this.mounts.remove(mount.mount());
        if (LOG.isTraceEnabled()) {
          LOG.trace("Unmounted {}", path);
        }
      }
      case final FBFSObjectReal ignored -> {
        throw new FileSystemException(
          path.toString(),
          null,
          NOT_A_FILESYSTEM_MOUNT
        );
      }
      case final FBFSObjectVirtualDirectory ignored -> {
        throw new FileSystemException(
          path.toString(),
          null,
          NOT_A_FILESYSTEM_MOUNT
        );
      }
    }
  }

  @FSOp
  private void opMount(
    final Path path,
    final FBFSPathAbsolute mountAt)
    throws FileSystemException
  {
    if (LOG.isTraceEnabled()) {
      LOG.trace("Mount {} ({}) -> {}", path, path.getFileSystem(), mountAt);
    }

    this.checkNotClosed();
    this.checkPathBelongs(mountAt);

    final var mount =
      new FBMount(mountAt, path);
    final var existing =
      new FBLookup(mountAt).lookup();

    final var newNode =
      new FBFSObjectMount(
        mountAt.getFileName().toString(),
        mount,
        new FBVirtualDirectoryAttributes(),
        existing
      );

    final var now = FileTime.from(Instant.now());
    newNode.attributes().setCreationTime(now);
    newNode.attributes().setLastAccessTime(now);
    newNode.attributes().setLastModifiedTime(now);

    this.tree.replaceWith(existing, newNode);
    this.mounts.add(mount);
    if (LOG.isTraceEnabled()) {
      LOG.trace("Mounted {} ({}) -> {}", path, path.getFileSystem(), mountAt);
    }
  }

  /**
   * @param path    The file
   * @param options The options
   *
   * @return A file channel
   *
   * @throws IOException On errors
   * @see Files#newByteChannel(Path, OpenOption...)
   */

  @FSOp
  public SeekableByteChannel opNewByteChannel(
    final FBFSPathAbsolute path,
    final Set<? extends OpenOption> options)
    throws IOException
  {
    if (LOG.isTraceEnabled()) {
      LOG.trace("NewByteChannel {} ({})", path, options);
    }

    this.checkNotClosed();
    this.checkPathBelongs(path);

    final var lookup =
      new FBLookup(path);
    final var node =
      lookup.lookup();

    if (!options.isEmpty()) {
      throw new ReadOnlyFileSystemException();
    }

    return switch (node) {
      case final FBFSObjectReal real -> {
        yield Files.newByteChannel(real.path(), Set.of());
      }
      case final FBFSObjectMount ignored -> {
        throw new FileSystemException(path.toString(), null, IS_DIRECTORY);
      }
      case final FBFSObjectVirtualDirectory ignored -> {
        throw new FileSystemException(path.toString(), null, IS_DIRECTORY);
      }
    };
  }

  /**
   * @return The filesystem tree
   */

  public FBFSTree tree()
  {
    return this.tree;
  }

  /**
   * @param path    The path
   * @param options The options
   *
   * @return A file channel
   *
   * @throws IOException On errors
   * @see FileChannel#open(Path, OpenOption...)
   */

  @FSOp
  public FileChannel opNewFileChannel(
    final FBFSPathAbsolute path,
    final Set<? extends OpenOption> options)
    throws IOException
  {
    if (LOG.isTraceEnabled()) {
      LOG.trace("NewFileChannel {} ({})", path, options);
    }

    this.checkNotClosed();
    this.checkPathBelongs(path);

    final var lookup =
      new FBLookup(path);
    final var node =
      lookup.lookup();

    if (!options.isEmpty()) {
      throw new ReadOnlyFileSystemException();
    }

    return switch (node) {
      case final FBFSObjectReal real -> {
        yield FileChannel.open(real.path(), Set.of());
      }
      case final FBFSObjectMount ignored -> {
        throw new FileSystemException(path.toString(), null, IS_DIRECTORY);
      }
      case final FBFSObjectVirtualDirectory ignored -> {
        throw new FileSystemException(path.toString(), null, IS_DIRECTORY);
      }
    };
  }

  /**
   * @return The filesystem instance name
   */

  public String name()
  {
    return this.name;
  }

  /**
   * @param path    The path
   * @param type    The type
   * @param options The options
   * @param <A>     The type of attributes
   *
   * @return The attributes
   *
   * @throws IOException On errors
   * @see Files#getAttribute(Path, String, LinkOption...)
   */

  @FSOp
  public <A extends BasicFileAttributes> A opReadAttributes(
    final FBFSPathAbsolute path,
    final Class<A> type,
    final LinkOption[] options)
    throws IOException
  {
    if (LOG.isTraceEnabled()) {
      LOG.trace("ReadAttributes {} ({})", path, options);
    }

    this.checkNotClosed();
    this.checkPathBelongs(path);

    if (Objects.equals(type, BasicFileAttributes.class)) {
      return switch (new FBLookup(path).lookup()) {
        case final FBFSObjectMount v -> {
          yield (A) v.attributes();
        }
        case final FBFSObjectVirtualDirectory v -> {
          yield (A) v.attributes();
        }
        case final FBFSObjectReal real -> {
          yield Files.readAttributes(real.path(), type, options);
        }
      };
    } else {
      throw new UnsupportedOperationException(
        "Unsupported attribute type: %s".formatted(type)
      );
    }
  }

  /**
   * @param pSource The source
   * @param pTarget The target
   * @param options The options
   *
   * @see Files#move(Path, Path, CopyOption...)
   */

  @FSOp
  public void opMove(
    final FBFSPathAbsolute pSource,
    final FBFSPathAbsolute pTarget,
    final CopyOption[] options)
    throws FileSystemException
  {
    if (LOG.isTraceEnabled()) {
      LOG.trace("Move {} -> {} ({})", pSource, pTarget, options);
    }

    this.checkNotClosed();
    this.checkPathBelongs(pSource);
    this.checkPathBelongs(pTarget);

    if (pSource.isRoot()) {
      throw new IllegalArgumentException(
        "Cannot rename the root directory."
      );
    }
    if (pTarget.isRoot()) {
      throw new IllegalArgumentException(
        "Cannot rename to the root directory."
      );
    }

    final var sourceLookup =
      new FBLookup(pSource);
    final var targetLookup =
      new FBLookup(pTarget);

    switch (sourceLookup.lookup()) {
      case final FBFSObjectMount ignored -> {
        throw new UnsupportedOperationException();
      }

      case final FBFSObjectReal ignored -> {
        throw new ReadOnlyFileSystemException();
      }

      case final FBFSObjectVirtualDirectory source -> {
        final var targetOpt = targetLookup.lookupOrMissing();
        if (targetOpt.isPresent()) {
          throw new FileAlreadyExistsException(pTarget.toString());
        }

        this.tree.rename(
          source,
          pTarget.getFileName().toString()
        );
        if (LOG.isTraceEnabled()) {
          LOG.trace("Moved {} -> {}", pSource, pTarget);
        }
      }
    }
  }

  /**
   * An annotation that indicates that a method is a fundamental filesystem
   * operation. For documentation purposes only.
   */

  @Retention(value = RetentionPolicy.SOURCE)
  @Target(ElementType.METHOD)
  public @interface FSOp
  {
  }

}
