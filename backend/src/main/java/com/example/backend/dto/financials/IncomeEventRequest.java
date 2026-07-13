package com.example.backend.dto.financials;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record IncomeEventRequest(
    @NotNull(message = "Income date is required") LocalDate date,
    @NotBlank(message = "Income label is required") String label,
    @NotBlank(message = "Income type is required") String type,
    @Min(value = 1, message = "Check number must be greater than zero") Integer checkNumber) {}
