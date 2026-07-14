package com.example.backend.service;

public class WorkspaceMigrationRequestException extends RuntimeException {

  public WorkspaceMigrationRequestException(String message) {
    super(message);
  }

  public WorkspaceMigrationRequestException(String message, Throwable cause) {
    super(message, cause);
  }
}
