package com.example.backend.service;

public class WorkspaceMigrationConflictException extends RuntimeException {

  public WorkspaceMigrationConflictException(String message) {
    super(message);
  }
}
