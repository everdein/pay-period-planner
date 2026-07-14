package com.example.backend.service;

public class WorkspaceMigrationNotFoundException extends RuntimeException {

  public WorkspaceMigrationNotFoundException(String message) {
    super(message);
  }
}
