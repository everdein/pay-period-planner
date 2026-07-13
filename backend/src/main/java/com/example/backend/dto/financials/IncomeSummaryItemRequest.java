package com.example.backend.dto.financials;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record IncomeSummaryItemRequest(
    @NotBlank(message = "Income category is required") String category,
    @NotBlank(message = "Income interval is required") String interval,
    @NotNull(message = "Income amount is required")
        @PositiveOrZero(message = "Income amount must be positive")
        BigDecimal amount) {}
