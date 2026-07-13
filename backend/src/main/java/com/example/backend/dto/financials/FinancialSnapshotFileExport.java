package com.example.backend.dto.financials;

public record FinancialSnapshotFileExport(long version, byte[] content) {}
