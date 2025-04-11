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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.util.LinkedList;
import java.util.Objects;

final class Lookup
{
  private static final Logger LOG =
    LoggerFactory.getLogger(Lookup.class);

  private final FBFSPathAbsolute target;
  private final LinkedList<String> pathNow;
  private final LinkedList<String> pathPartsRemaining;
  private final FBFSTree tree;
  private FBFSObjectType nodeNow;

  Lookup(
    final FBFSPathAbsolute inTarget)
  {
    this.target =
      Objects.requireNonNull(inTarget, "target");
    this.pathNow =
      new LinkedList<>();
    this.pathPartsRemaining =
      new LinkedList<>(this.target.components());
    this.tree =
      inTarget.getFileSystem()
        .tree();
    this.nodeNow =
      this.tree.root();

    this.pathNow.add(this.pathPartsRemaining.pop());
  }

  public FBFSObjectType lookup()
    throws NoSuchFileException, AccessDeniedException
  {
    LOG.trace("Lookup: {}", this.pathNow);

    if (this.pathPartsRemaining.isEmpty()) {
      return this.nodeNow;
    }

    final var name = this.pathPartsRemaining.pop();
    this.pathNow.add(name);

    return switch (this.nodeNow) {
      case final FBFSObjectMount mount -> {
        final var mountRec =
          mount.mount();
        final var mountBase =
          mountRec.basePath();
        final var path =
          mountBase.resolve(name);

        if (!path.startsWith(mountBase)) {
          throw new AccessDeniedException("Path traversal prevented.");
        }

        if (Files.exists(path)) {
          this.nodeNow = new FBFSObjectReal(name, path);
          yield this.lookup();
        }

        throw new NoSuchFileException(this.target.toString());
      }

      case final FBFSObjectReal real -> {
        final var newPath = real.path().resolve(name);
        if (Files.exists(newPath)) {
          this.nodeNow = new FBFSObjectReal(name, newPath);
          yield this.lookup();
        }

        throw new NoSuchFileException(this.target.toString());
      }

      case final FBFSObjectVirtualDirectory directory -> {
        final var outgoing = this.tree.outgoingEdgesOf(directory);
        for (final var edge : outgoing) {
          final var childName = edge.child().name();
          if (childName.equalsIgnoreCase(name)) {
            this.nodeNow = edge.child();
            yield this.lookup();
          }
        }
        throw new NoSuchFileException(this.target.toString());
      }
    };
  }
}
