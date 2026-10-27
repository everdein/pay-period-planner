package com.example.backend.service;

public class RegistrationClosedException extends RuntimeException {
  public RegistrationClosedException() {
    super("Account registration is unavailable");
  }
}
