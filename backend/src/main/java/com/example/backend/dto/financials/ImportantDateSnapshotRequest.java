package com.example.backend.dto.financials;

import java.time.LocalDate;

public record ImportantDateSnapshotRequest(Long id, LocalDate date, String event, String type) {}
