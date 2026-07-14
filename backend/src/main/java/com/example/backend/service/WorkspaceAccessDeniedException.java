package com.example.backend.service;

public class WorkspaceAccessDeniedException extends RuntimeException {

  public WorkspaceAccessDeniedException(String message) {
    super(message);
  }
}
