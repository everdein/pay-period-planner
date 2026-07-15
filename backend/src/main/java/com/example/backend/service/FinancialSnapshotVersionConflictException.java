package com.example.backend.service;

public class FinancialSnapshotVersionConflictException extends RuntimeException {

  public FinancialSnapshotVersionConflictException(String message, Throwable cause) {
    super(message, cause);
  }
}
