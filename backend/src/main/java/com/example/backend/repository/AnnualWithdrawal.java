package com.example.backend.repository;

public record AnnualWithdrawal(
    long id, String bill, int month, int day, double amount, String account, boolean paid) {

  public AnnualWithdrawal withId(long id) {
    return new AnnualWithdrawal(id, bill, month, day, amount, account, paid);
  }
}
