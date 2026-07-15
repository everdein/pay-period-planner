package com.example.backend.dto.financials;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record FinancialSnapshotBackup(
    @NotBlank(message = "Backup format is required") String format,
    @NotNull(message = "Backup export timestamp is required") Instant exportedAt,
    @NotNull(message = "Backup snapshot is required") @Valid ExpenseSnapshotRequest snapshot) {

  public static final String FORMAT = "end-to-end-app.financial-snapshot.v1";
}
