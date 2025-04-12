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

import com.io7m.jmulticlose.core.CloseableCollection;
import com.io7m.jmulticlose.core.CloseableCollectionType;
import com.io7m.jmulticlose.core.ClosingResourceFailedException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.Watchable;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

/**
 * A primitive watch service.
 */

public final class FBFSWatchService implements WatchService
{
  private static final AtomicLong ID_POOL =
    new AtomicLong(0L);

  private final ConcurrentHashMap.KeySetView<WatchedObject, Boolean> watched;
  private final CloseableCollectionType<ClosingResourceFailedException> resources;
  private final AtomicBoolean closed;
  private final LinkedBlockingQueue<WatchedObject> ready;
  private final ExecutorService executor;
  private final Duration watchDuration;

  FBFSWatchService(
    final Duration inWatchDuration)
  {
    this.watchDuration =
      Objects.requireNonNull(inWatchDuration, "watchDuration");
    this.watched =
      ConcurrentHashMap.newKeySet();
    this.resources =
      CloseableCollection.create();
    this.closed =
      new AtomicBoolean(false);
    this.ready =
      new LinkedBlockingQueue<>();
    this.executor =
      this.resources.add(
        Executors.newThreadPerTaskExecutor(r -> {
          final var nextId =
            ID_POOL.getAndIncrement();
          final var name =
            String.format(
              "com.io7m.fsbind.core.internal.FBFSWatchService[%s]",
              nextId
            );

          final var thread = Thread.ofVirtual().unstarted(r);
          thread.setName(name);
          return thread;
        })
      );

    this.resources.add(() -> {
      this.watched.forEach(WatchedObject::cancel);
    });
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
  public WatchKey poll()
  {
    return this.ready.poll();
  }

  @Override
  public WatchKey poll(
    final long timeout,
    final TimeUnit unit)
    throws InterruptedException
  {
    return this.ready.poll(timeout, unit);
  }

  @Override
  public WatchKey take()
    throws InterruptedException
  {
    return this.ready.take();
  }

  /**
   * Register a path to be watched.
   *
   * @param path   The path
   * @param events The events
   *
   * @return The watch key
   */

  public WatchKey register(
    final FBFSPathAbsolute path,
    final WatchEvent.Kind<?>[] events)
  {
    return this.enqueue(new WatchedObject(this, path, events));
  }

  private WatchedObject enqueue(
    final WatchedObject object)
  {
    this.watched.add(object);
    this.executor.execute(object::run);
    return object;
  }

  private void dequeue(
    final WatchedObject watchedObject)
  {
    this.watched.remove(watchedObject);
  }

  private record WatchedEvent(
    Path context,
    int count,
    Kind<Path> kind)
    implements WatchEvent<Path>
  {

  }

  private static final class WatchedObject implements WatchKey
  {
    private final FBFSWatchService service;
    private final FBFSPathAbsolute path;
    private final AtomicBoolean done;
    private final ArrayBlockingQueue<Object> shutDown;
    private final AtomicReference<List<WatchedEvent>> eventOutbox;
    private final Set<WatchEvent.Kind<?>> watchingFor;
    private final ArrayList<WatchedEvent> eventReady;
    private boolean valid;
    private FileTime timeThen;
    private FileTime timeNow;

    private WatchedObject(
      final FBFSWatchService inService,
      final FBFSPathAbsolute inPath,
      final WatchEvent.Kind<?>[] inEvents)
    {
      this.service =
        Objects.requireNonNull(inService, "service");
      this.path =
        Objects.requireNonNull(inPath, "inPath");
      this.valid =
        true;
      this.done =
        new AtomicBoolean(false);
      this.shutDown =
        new ArrayBlockingQueue<>(1);
      this.eventOutbox =
        new AtomicReference<>(List.of());
      this.watchingFor =
        Set.of(inEvents);
      this.eventReady =
        new ArrayList<>(3);
    }

    @Override
    public boolean isValid()
    {
      return this.valid;
    }

    @Override
    public List<WatchEvent<?>> pollEvents()
    {
      return List.copyOf(this.eventOutbox.getAndSet(List.of()));
    }

    @Override
    public boolean reset()
    {
      if (this.done.compareAndSet(true, false)) {
        this.shutDown.clear();
        this.eventOutbox.set(null);
        this.service.enqueue(this);
        return true;
      }
      return false;
    }

    @Override
    public void cancel()
    {
      if (this.done.compareAndSet(false, true)) {
        this.shutDown.add(new Object());
      }
    }

    @Override
    public Watchable watchable()
    {
      return this.path;
    }

    public void check()
    {
      this.eventReady.clear();

      try {
        this.timeNow = Files.getLastModifiedTime(this.path);
      } catch (final NoSuchFileException e) {
        this.timeNow = null;
      } catch (final IOException e) {
        // Nothing we can do about it.
      }

      if (this.watchingForEvent(ENTRY_CREATE)) {
        this.checkCreated();
      }
      if (this.watchingForEvent(ENTRY_DELETE)) {
        this.checkDeleted();
      }
      if (this.watchingForEvent(ENTRY_MODIFY)) {
        this.checkModified();
      }

      this.send();
      this.timeThen = this.timeNow;
      this.pause();
    }

    private void send()
    {
      if (!this.eventReady.isEmpty()) {
        this.eventOutbox.set(List.copyOf(this.eventReady));
        this.eventReady.clear();
        this.done.set(true);
        this.service.ready.add(this);
        this.service.dequeue(this);
      }
    }

    private void pause()
    {
      try {
        if (this.shouldContinue()) {
          this.shutDown.poll(
            this.service.watchDuration.toNanos(),
            TimeUnit.NANOSECONDS
          );
        }
      } catch (final InterruptedException e) {
        // Ignored
      }
    }

    private void checkModified()
    {
      if (this.timeNow != null && this.timeThen != null) {
        if (!Objects.equals(this.timeNow, this.timeThen)) {
          this.fileModified();
        }
      }
    }

    private void checkDeleted()
    {
      if (this.timeNow == null && this.timeThen != null) {
        this.fileDeleted();
      }
    }

    private void checkCreated()
    {
      if (this.timeNow != null && this.timeThen == null) {
        this.fileCreated();
      }
    }

    private boolean watchingForEvent(
      final WatchEvent.Kind<Path> kind)
    {
      return this.watchingFor.contains(kind);
    }

    private void fileCreated()
    {
      this.publish(new WatchedEvent(
        this.path,
        1,
        ENTRY_CREATE
      ));
    }

    private void fileModified()
    {
      this.publish(new WatchedEvent(
        this.path,
        1,
        StandardWatchEventKinds.ENTRY_MODIFY
      ));
    }

    private void fileDeleted()
    {
      this.publish(new WatchedEvent(
        this.path,
        1,
        StandardWatchEventKinds.ENTRY_DELETE
      ));
    }

    private void publish(
      final WatchedEvent event)
    {
      this.eventReady.add(event);
    }

    private boolean shouldContinue()
    {
      return this.serviceStillOpen() && this.notCancelled();
    }

    private boolean notCancelled()
    {
      return !this.done.get();
    }

    private boolean serviceStillOpen()
    {
      return !this.service.closed.get();
    }

    public void run()
    {
      try {
        this.timeThen = Files.getLastModifiedTime(this.path);
      } catch (final IOException e) {
        // Nothing we can do about it.
      }

      while (this.shouldContinue()) {
        this.check();
      }
    }
  }
}
