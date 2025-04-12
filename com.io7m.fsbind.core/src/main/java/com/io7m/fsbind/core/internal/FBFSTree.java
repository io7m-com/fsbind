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

import net.jcip.annotations.GuardedBy;
import net.jcip.annotations.ThreadSafe;
import org.jgrapht.graph.DirectedAcyclicGraph;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A tree representing the filesystem.
 */

@ThreadSafe
public final class FBFSTree
{
  private final Object treeLock;
  @GuardedBy("treeLock")
  private final DirectedAcyclicGraph<FBFSObjectType, FBEdge> tree;
  private final FBFSObjectVirtualDirectory root;

  private FBFSTree(
    final FBFSObjectVirtualDirectory inRoot)
  {
    this.root =
      Objects.requireNonNull(inRoot, "root");
    this.treeLock =
      new Object();
    this.tree =
      new DirectedAcyclicGraph<>(FBEdge.class);

    this.tree.addVertex(this.root);
  }

  /**
   * Create a tree with the given root node.
   *
   * @param root The root
   *
   * @return The tree
   */

  public static FBFSTree createWithRoot(
    final FBFSObjectVirtualDirectory root)
  {
    return new FBFSTree(root);
  }

  /**
   * Delete the given virtual directory if it is empty.
   *
   * @param directory The directory
   *
   * @return {@code true} if the directory was deleted
   */

  public boolean deleteIfEmpty(
    final FBFSObjectVirtualDirectory directory)
  {
    Objects.requireNonNull(directory, "directory");

    synchronized (this.treeLock) {
      if (this.tree.outDegreeOf(directory) == 0) {
        this.tree.removeVertex(directory);
        return true;
      }
      return false;
    }
  }

  /**
   * Append the given child node to the given directory node.
   *
   * @param parentNode The directory
   * @param newNode    The child node
   */

  private void append(
    final FBFSObjectVirtualDirectory parentNode,
    final FBFSObjectType newNode)
  {
    Objects.requireNonNull(parentNode, "parentNode");
    Objects.requireNonNull(newNode, "newNode");

    synchronized (this.treeLock) {
      this.tree.addVertex(newNode);
      this.addEdge(parentNode, newNode);
    }
  }

  private void addEdge(
    final FBFSObjectType parent,
    final FBFSObjectType child)
  {
    this.tree.addEdge(parent, child, new FBEdge(parent, child));
  }

  /**
   * Append the given node to the given parent if the parent does not have
   * a child with the same name.
   *
   * @param parentNode The parent node
   * @param newNode    The child node
   *
   * @return {@code true} if a child was added
   */

  public boolean appendIfNameFree(
    final FBFSObjectVirtualDirectory parentNode,
    final FBFSObjectType newNode)
  {
    Objects.requireNonNull(parentNode, "parentNode");
    Objects.requireNonNull(newNode, "newNode");

    final var name = newNode.name();
    synchronized (this.treeLock) {
      final var outgoing = this.tree.outgoingEdgesOf(parentNode);
      for (final var edge : outgoing) {
        final var childName = edge.child().name();
        if (childName.equalsIgnoreCase(name)) {
          return false;
        }
      }
      this.append(parentNode, newNode);
    }
    return true;
  }

  /**
   * @param parent    The parent
   * @param directory The directory
   * @param filter    The filter
   *
   * @return The directory stream
   *
   * @see Files#list(Path)
   */

  public DirectoryStream<Path> list(
    final FBFSPathAbsolute parent,
    final FBFSObjectVirtualDirectory directory,
    final DirectoryStream.Filter<? super Path> filter)
  {
    Objects.requireNonNull(parent, "parent");
    Objects.requireNonNull(directory, "directory");
    Objects.requireNonNull(filter, "filter");

    synchronized (this.treeLock) {
      final var elements =
        this.tree.outgoingEdgesOf(directory)
          .stream()
          .map(FBEdge::child)
          .map(FBFSObjectType::name)
          .map(parent::resolve)
          .map(Path.class::cast)
          .filter(p -> {
            try {
              return filter.accept(p);
            } catch (final IOException e) {
              throw new UncheckedIOException(e);
            }
          })
          .sorted()
          .toList();

      return new FBFSNodeDirectoryStream(elements);
    }
  }

  /**
   * Replace the given node with a different node.
   *
   * @param existing The existing node
   * @param newNode  The new node
   */

  public void replaceWith(
    final FBFSObjectType existing,
    final FBFSObjectType newNode)
  {
    Objects.requireNonNull(existing, "existing");
    Objects.requireNonNull(newNode, "newNode");

    synchronized (this.treeLock) {
      final var incoming =
        this.tree.incomingEdgesOf(existing);
      final var outgoing =
        this.tree.outgoingEdgesOf(existing);

      this.tree.addVertex(newNode);
      for (final var edge : incoming) {
        this.addEdge(edge.parent(), newNode);
      }
      for (final var edge : outgoing) {
        this.addEdge(newNode, edge.child());
      }
      this.tree.removeVertex(existing);
    }
  }

  /**
   * @return The root directory
   */

  public FBFSObjectVirtualDirectory root()
  {
    return this.root;
  }

  /**
   * @param directory The directory
   *
   * @return A read-only snapshot of the outgoing directory edges
   */

  public Set<FBEdge> outgoingEdgesOf(
    final FBFSObjectVirtualDirectory directory)
  {
    synchronized (this.treeLock) {
      return Set.copyOf(this.tree.outgoingEdgesOf(directory));
    }
  }

  /**
   * Rename the given node.
   *
   * @param source The source
   * @param name   The new name
   */

  public void rename(
    final FBFSObjectVirtualDirectory source,
    final String name)
  {
    synchronized (this.treeLock) {
      source.setName(name);
    }
  }

  /**
   * Unmount the given node.
   *
   * @param mount The mount
   */

  public void unmount(
    final FBFSObjectMount mount)
  {
    this.replaceWith(mount, mount.shadowedObject());
  }

  private static final class FBFSNodeDirectoryStream
    implements DirectoryStream<Path>
  {
    private final List<Path> elements;

    FBFSNodeDirectoryStream(
      final List<Path> inElements)
    {
      this.elements =
        Objects.requireNonNull(inElements, "elements");
    }

    @Override
    public Iterator<Path> iterator()
    {
      return this.elements.iterator();
    }

    @Override
    public void close()
    {

    }
  }
}
