package com.example.backend.domain.financials;

import java.time.DateTimeException;
import java.time.ZoneId;

public record FinancialPlanningSettings(PayCadence payCadence, String timeZone) {

  public static FinancialPlanningSettings legacyDefaults() {
    return new FinancialPlanningSettings(PayCadence.BIWEEKLY, "UTC");
  }

  public static FinancialPlanningSettings from(String payCadence, String timeZone) {
    if (timeZone == null || timeZone.isBlank()) {
      throw new IllegalArgumentException("Planning time zone is required");
    }

    String normalizedTimeZone = timeZone.trim();
    try {
      ZoneId.of(normalizedTimeZone);
    } catch (DateTimeException exception) {
      throw new IllegalArgumentException("Planning time zone must be a valid IANA zone", exception);
    }

    return new FinancialPlanningSettings(PayCadence.parse(payCadence), normalizedTimeZone);
  }

  public ZoneId zoneId() {
    return ZoneId.of(timeZone);
  }
}
