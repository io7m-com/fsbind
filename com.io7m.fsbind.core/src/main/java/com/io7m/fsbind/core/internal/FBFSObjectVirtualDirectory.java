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

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * An object in the filesystem tree that represents a virtual directory.
 */

public final class FBFSObjectVirtualDirectory
  implements FBFSObjectType
{
  private final AtomicReference<String> nameRef;
  private final FBVirtualDirectoryAttributes attributes;
  private final Optional<FBFSObjectType> shadowed;

  /**
   * An object in the filesystem tree that represents a virtual directory.
   *
   * @param inAttributes The attributes
   * @param name         The name
   * @param inShadowed   The shadowed node
   */

  public FBFSObjectVirtualDirectory(
    final FBVirtualDirectoryAttributes inAttributes,
    final String name,
    final Optional<FBFSObjectType> inShadowed)
  {
    this.attributes =
      Objects.requireNonNull(inAttributes, "attributes");
    this.nameRef =
      new AtomicReference<>(name);
    this.shadowed =
      Objects.requireNonNull(inShadowed, "shadowed");
  }

  /**
   * Set the name.
   *
   * @param name The name
   */

  public void setName(
    final String name)
  {
    this.nameRef.set(Objects.requireNonNull(name, "name"));
  }

  @Override
  public boolean equals(
    final Object o)
  {
    if (this == o) {
      return true;
    }
    if (!(o instanceof final FBFSObjectVirtualDirectory that)) {
      return false;
    }
    return this.nameRef.get().equalsIgnoreCase(that.nameRef.get())
           && Objects.equals(this.attributes, that.attributes)
           && Objects.equals(this.shadowed, that.shadowed);
  }

  @Override
  public int hashCode()
  {
    return Objects.hash(this.nameRef, this.attributes, this.shadowed);
  }

  /**
   * @return The attributes
   */

  public FBVirtualDirectoryAttributes attributes()
  {
    return this.attributes;
  }

  @Override
  public String name()
  {
    return this.nameRef.get();
  }

  @Override
  public Optional<FBFSObjectType> shadowed()
  {
    return this.shadowed;
  }
}

