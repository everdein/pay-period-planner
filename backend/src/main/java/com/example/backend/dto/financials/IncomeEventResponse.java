package com.example.backend.dto.financials;

import java.time.LocalDate;

public record IncomeEventResponse(
    long id, LocalDate date, String label, String type, Integer checkNumber, int checksInMonth) {}
