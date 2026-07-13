package com.example.backend.repository;

public class SnapshotVersionConflictException extends RuntimeException {

  private final long expectedVersion;
  private final long actualVersion;

  public SnapshotVersionConflictException(long expectedVersion, long actualVersion) {
    super(
        "Financial snapshot version "
            + expectedVersion
            + " is stale; current version is "
            + actualVersion);
    this.expectedVersion = expectedVersion;
    this.actualVersion = actualVersion;
  }

  public long expectedVersion() {
    return expectedVersion;
  }

  public long actualVersion() {
    return actualVersion;
  }
}
