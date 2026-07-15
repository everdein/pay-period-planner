package com.example.backend.dto.financials;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record PayPeriodRequest(
    @NotNull(message = "Pay period start date is required") LocalDate startDate,
    @NotNull(message = "Pay period end date is required") LocalDate endDate,
    @jakarta.validation.Valid FinancialPlanningSettingsRequest planningSettings) {

  public PayPeriodRequest(LocalDate startDate, LocalDate endDate) {
    this(startDate, endDate, null);
  }
}
