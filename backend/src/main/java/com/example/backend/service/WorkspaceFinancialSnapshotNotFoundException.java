package com.example.backend.service;

public class WorkspaceFinancialSnapshotNotFoundException extends RuntimeException {

  public WorkspaceFinancialSnapshotNotFoundException(long workspaceId) {
    super("No financial snapshot exists for workspace " + workspaceId);
  }
}
