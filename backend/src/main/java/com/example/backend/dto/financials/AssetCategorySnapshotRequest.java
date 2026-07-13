package com.example.backend.dto.financials;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record AssetCategorySnapshotRequest(
    @NotBlank(message = "Asset category key is required") String key,
    @NotBlank(message = "Asset category label is required") String label,
    @NotNull(message = "Asset accounts are required")
        List<
                @NotNull(message = "Asset account record is required") @Valid
                AssetAccountSnapshotRequest>
            accounts) {}
