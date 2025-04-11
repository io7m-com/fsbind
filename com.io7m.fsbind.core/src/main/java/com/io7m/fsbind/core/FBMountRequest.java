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

import com.io7m.jaffirm.core.Preconditions;

import java.nio.file.Path;
import java.util.Objects;

/**
 * A request to mount a path within a filesystem into a given fsbind filesystem.
 *
 * @param mountPathWithinFilesystem The path and external filesystem to mount
 * @param mountAt                   The location at which the filesystem will be mounted
 */

public record FBMountRequest(
  Path mountPathWithinFilesystem,
  Path mountAt)
{
  /**
   * A request to mount a path within a filesystem into a given fsbind filesystem.
   *
   * @param mountPathWithinFilesystem The path and external filesystem to mount
   * @param mountAt                   The location at which the filesystem will be mounted
   */

  public FBMountRequest
  {
    Objects.requireNonNull(mountPathWithinFilesystem, "pathWithinFilesystem");
    Objects.requireNonNull(mountAt, "mountAt");

    Preconditions.checkPreconditionV(
      mountPathWithinFilesystem.isAbsolute(),
      "Path %s must be absolute",
      mountPathWithinFilesystem
    );
    Preconditions.checkPreconditionV(
      mountAt.isAbsolute(),
      "Path %s must be absolute",
      mountAt
    );
    Preconditions.checkPreconditionV(
      mountAt.getFileSystem() instanceof FBFilesystem,
      "Path %s must belong to an fsbind filesystem (is actually %s)",
      mountAt,
      mountAt.getFileSystem()
    );
    Preconditions.checkPrecondition(
      mountPathWithinFilesystem.getFileSystem() != mountAt.getFileSystem(),
      "A filesystem cannot be mounted in itself."
    );
  }
}
