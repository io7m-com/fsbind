fsbind
===

[![Maven Central](https://img.shields.io/maven-central/v/com.io7m.fsbind/com.io7m.fsbind.svg?style=flat-square)](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.io7m.fsbind%22)
[![Maven Central (snapshot)](https://img.shields.io/nexus/s/com.io7m.fsbind/com.io7m.fsbind?server=https%3A%2F%2Fs01.oss.sonatype.org&style=flat-square)](https://s01.oss.sonatype.org/content/repositories/snapshots/com/io7m/fsbind/)
[![Codecov](https://img.shields.io/codecov/c/github/io7m-com/fsbind.svg?style=flat-square)](https://codecov.io/gh/io7m-com/fsbind)
![Java Version](https://img.shields.io/badge/21-java?label=java&color=e6c35c)

![com.io7m.fsbind](./src/site/resources/fsbind.jpg?raw=true)

| JVM | Platform | Status |
|-----|----------|--------|
| OpenJDK (Temurin) Current | Linux | [![Build (OpenJDK (Temurin) Current, Linux)](https://img.shields.io/github/actions/workflow/status/io7m-com/fsbind/main.linux.temurin.current.yml)](https://www.github.com/io7m-com/fsbind/actions?query=workflow%3Amain.linux.temurin.current)|
| OpenJDK (Temurin) LTS | Linux | [![Build (OpenJDK (Temurin) LTS, Linux)](https://img.shields.io/github/actions/workflow/status/io7m-com/fsbind/main.linux.temurin.lts.yml)](https://www.github.com/io7m-com/fsbind/actions?query=workflow%3Amain.linux.temurin.lts)|
| OpenJDK (Temurin) Current | Windows | [![Build (OpenJDK (Temurin) Current, Windows)](https://img.shields.io/github/actions/workflow/status/io7m-com/fsbind/main.windows.temurin.current.yml)](https://www.github.com/io7m-com/fsbind/actions?query=workflow%3Amain.windows.temurin.current)|
| OpenJDK (Temurin) LTS | Windows | [![Build (OpenJDK (Temurin) LTS, Windows)](https://img.shields.io/github/actions/workflow/status/io7m-com/fsbind/main.windows.temurin.lts.yml)](https://www.github.com/io7m-com/fsbind/actions?query=workflow%3Amain.windows.temurin.lts)|

## fsbind

The `fsbind` package implements a [JSR203](https://jcp.org/en/jsr/detail?id=203)
filesystem that can bind existing JSR203 filesystems into a single unified
namespace. It is similar in design and goals to
[PhysicsFS](https://icculus.org/physfs/).

### Features

  * Bind multiple JSR203 filesystems into a single read-only filesystem.
  * Written in pure Java 21.
  * [OSGi](https://www.osgi.org/) ready.
  * [JPMS](https://en.wikipedia.org/wiki/Java_Platform_Module_System) ready.
  * ISC license.
  * High-coverage automated test suite.

### Usage

The `fsbind` package provides a read-only JSR203 filesystem accessible via an
`fsbind` URI scheme. To create a filesystem named `example`, call:

```
final FBFilesystem filesystem =
  (FBFilesystem) FileSystems.newFileSystem(URI.create("fsbind:example:/"), null);
```

The `fsbind` filesystem exposes a strict, UNIX-like virtual filesystem
consisting of _virtual directories_ and other JSR203 filesystems mounted into
filesystem tree.

For example, to create a directory hierarchy and mount a zip file into it, it's
first necessary to create a directory in the filesystem. This directory is
_virtual_ in the sense that it is a complete in-memory fabrication and does not
actually involve any file I/O:

```
final var dir = filesystem.getPath("/", "archives", "zip0");
Files.createDirectory(dir);
```

Then, we can take an existing [ZipFS](https://docs.oracle.com/en/java/javase/21/docs/api/jdk.zipfs/module-summary.html)
filesystem and mount it into the virtual filesystem:

```
final var zipfs =
  FileSystems.newFileSystem(
    URI.create("/path/to/some/zip_file.zip"), Map.of(), null
  );

filesystem.mount(
  new MountRequest(zipfs.getPath("/"),
  dir
);
```

This will make the contents of `/path/to/some/zip_file.zip` accessible at
`/archives/zip0` inside the virtual filesystem. It is also possible to expose
only part of the archive by using a non-root path as the first argument to
the `MountRequest`. For example, if `zip_file.zip` looks like this:

```
  Length      Date    Time    Name
---------  ---------- -----   ----
        0  2025-04-10 20:16   x/
       10  2025-04-10 20:16   x/a.txt
       10  2025-04-10 20:16   x/c.txt
       10  2025-04-10 20:16   x/b.txt
        0  2025-04-10 20:16   y/
       10  2025-04-10 20:16   y/a.txt
        0  2025-04-10 20:16   z/
---------                     -------
       40                     7 files
```

We may choose to only expose the contents of the `x` directory:

```
filesystem.mount(
  new MountRequest(zipfs.getPath("/x"),
  dir
);
```

We would then be able to access `x/a.txt` by opening `/archives/zip0/a.txt`:

```
Files.readString(dir.resolve("a.txt"));
```

