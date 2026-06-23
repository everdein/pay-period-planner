package com.example.backend.repository;

public record IncomeSummaryItem(long id, String category, String interval, double amount) {

  public IncomeSummaryItem withId(long id) {
    return new IncomeSummaryItem(id, category, interval, amount);
  }
}
