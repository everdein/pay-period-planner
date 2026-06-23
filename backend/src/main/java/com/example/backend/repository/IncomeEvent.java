package com.example.backend.repository;

import java.time.LocalDate;

public record IncomeEvent(long id, LocalDate date, String label, String type, Integer checkNumber) {

  public IncomeEvent withId(long replacementId) {
    return new IncomeEvent(replacementId, date, label, type, checkNumber);
  }
}
