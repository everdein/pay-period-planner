package com.example.backend.service;

public class AccountAuthenticationException extends RuntimeException {

  public AccountAuthenticationException() {
    super("Email or password was not accepted");
  }
}
