package com.example.backend.domain.financials;

import java.time.LocalDate;

public record ImportantDate(long id, LocalDate date, String event, String type) {

  public ImportantDate withId(long replacementId) {
    return new ImportantDate(replacementId, date, event, type);
  }
}
