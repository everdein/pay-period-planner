package com.example.backend.dto.financials;

import jakarta.validation.constraints.NotBlank;

public record FinancialPlanningSettingsRequest(
    @NotBlank(message = "Pay cadence is required") String payCadence,
    @NotBlank(message = "Planning time zone is required") String timeZone) {}
