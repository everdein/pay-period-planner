package com.example.backend.dto.financials;

import java.time.LocalDate;

public record IncomeEventSnapshotRequest(
    Long id, LocalDate date, String label, String type, Integer checkNumber) {}
