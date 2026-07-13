package com.example.backend.dto.financials;

import java.time.Instant;

public record FinancialSnapshotExportResponse(
    String format, Instant exportedAt, ExpenseSnapshotRequest snapshot) {}
