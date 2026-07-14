package com.example.backend.service;

public class AccountConflictException extends RuntimeException {

  public AccountConflictException() {
    super("An account already exists for that email address");
  }
}
