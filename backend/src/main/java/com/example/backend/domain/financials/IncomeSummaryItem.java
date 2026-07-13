package com.example.backend.domain.financials;

import java.math.BigDecimal;

public record IncomeSummaryItem(long id, String category, String interval, BigDecimal amount) {

  public IncomeSummaryItem withId(long id) {
    return new IncomeSummaryItem(id, category, interval, amount);
  }
}
