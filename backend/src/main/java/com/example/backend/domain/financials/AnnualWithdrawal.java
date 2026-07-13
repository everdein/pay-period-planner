package com.example.backend.domain.financials;

import java.math.BigDecimal;

public record AnnualWithdrawal(
    long id, String bill, int month, int day, BigDecimal amount, String account, boolean paid) {

  public AnnualWithdrawal withId(long id) {
    return new AnnualWithdrawal(id, bill, month, day, amount, account, paid);
  }
}
