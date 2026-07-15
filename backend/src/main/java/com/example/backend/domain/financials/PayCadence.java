package com.example.backend.domain.financials;

import java.util.Locale;

public enum PayCadence {
  WEEKLY(52),
  BIWEEKLY(26),
  SEMIMONTHLY(24),
  MONTHLY(12);

  private final int periodsPerYear;

  PayCadence(int periodsPerYear) {
    this.periodsPerYear = periodsPerYear;
  }

  public int periodsPerYear() {
    return periodsPerYear;
  }

  public static PayCadence parse(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Pay cadence is required");
    }

    try {
      return valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException(
          "Pay cadence must be WEEKLY, BIWEEKLY, SEMIMONTHLY, or MONTHLY", exception);
    }
  }
}
