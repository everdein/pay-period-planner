package com.example.backend.domain.financials;

import java.math.BigDecimal;

public record ExpenseBill(
    long id, String bill, int dueDay, BigDecimal amount, String account, boolean paid) {

  public ExpenseBill withId(long id) {
    return new ExpenseBill(id, bill, dueDay, amount, account, paid);
  }
}
