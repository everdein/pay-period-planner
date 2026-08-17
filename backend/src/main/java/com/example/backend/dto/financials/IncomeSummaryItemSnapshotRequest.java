package com.example.backend.dto.financials;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record IncomeSummaryItemSnapshotRequest(
    Long id,
    @NotBlank(message = "Income category is required") String category,
    @NotBlank(message = "Income interval is required") String interval,
    @NotNull(message = "Income amount is required")
        @PositiveOrZero(message = "Income amount must be positive")
        @Digits(
            integer = 12,
            fraction = 2,
            message = "Income amount must have at most 12 integer and 2 fractional digits")
        BigDecimal amount) {}
