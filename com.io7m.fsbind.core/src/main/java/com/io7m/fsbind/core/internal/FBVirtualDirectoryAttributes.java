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

import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Objects;

final class FBVirtualDirectoryAttributes
  implements BasicFileAttributes
{
  private volatile FileTime lastModifiedTime =
    FileTime.fromMillis(0L);
  private volatile FileTime lastAccessTime =
    FileTime.fromMillis(0L);
  private volatile FileTime creationTime =
    FileTime.fromMillis(0L);

  FBVirtualDirectoryAttributes()
  {

  }

  void setCreationTime(
    final FileTime t)
  {
    this.creationTime =
      Objects.requireNonNull(t, "creationTime");
  }

  void setLastModifiedTime(
    final FileTime t)
  {
    this.lastModifiedTime =
      Objects.requireNonNull(t, "lastModifiedTime");
  }

  void setLastAccessTime(
    final FileTime t)
  {
    this.lastAccessTime =
      Objects.requireNonNull(t, "lastAccessTime");
  }

  @Override
  public FileTime lastModifiedTime()
  {
    return this.lastModifiedTime;
  }

  @Override
  public FileTime lastAccessTime()
  {
    return this.lastAccessTime;
  }

  @Override
  public FileTime creationTime()
  {
    return this.creationTime;
  }

  @Override
  public boolean isRegularFile()
  {
    return false;
  }

  @Override
  public boolean isDirectory()
  {
    return true;
  }

  @Override
  public boolean isSymbolicLink()
  {
    return false;
  }

  @Override
  public boolean isOther()
  {
    return false;
  }

  @Override
  public long size()
  {
    return 0;
  }

  @Override
  public Object fileKey()
  {
    return null;
  }
}
