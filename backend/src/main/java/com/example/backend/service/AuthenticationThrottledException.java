package com.example.backend.service;

public class AuthenticationThrottledException extends RuntimeException {
  public AuthenticationThrottledException() {
    super("Too many authentication attempts; try again later");
  }
}
