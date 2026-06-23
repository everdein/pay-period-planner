package com.example.backend.repository;

public record DebtAccount(long id, String account, String company, double amount) {

  public DebtAccount withId(long id) {
    return new DebtAccount(id, account, company, amount);
  }
}
