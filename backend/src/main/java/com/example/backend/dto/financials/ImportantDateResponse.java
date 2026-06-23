package com.example.backend.dto.financials;

import java.time.LocalDate;

public record ImportantDateResponse(long id, LocalDate date, String event, String type) {}
