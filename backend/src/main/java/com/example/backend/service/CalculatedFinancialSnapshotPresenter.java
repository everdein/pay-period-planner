package com.example.backend.service;

import com.example.backend.domain.financials.FinancialSnapshot;
import com.example.backend.dto.financials.ExpenseSnapshotResponse;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CalculatedFinancialSnapshotPresenter implements FinancialSnapshotPresenter {

  private final FinancialSnapshotCalculator calculator;
  private final FinancialSnapshotResponseMapper responseMapper;
  private final Clock clock;

  @Autowired
  public CalculatedFinancialSnapshotPresenter(
      FinancialSnapshotCalculator calculator, FinancialSnapshotResponseMapper responseMapper) {
    this(calculator, responseMapper, Clock.systemDefaultZone());
  }

  CalculatedFinancialSnapshotPresenter(
      FinancialSnapshotCalculator calculator,
      FinancialSnapshotResponseMapper responseMapper,
      Clock clock) {
    this.calculator = calculator;
    this.responseMapper = responseMapper;
    this.clock = clock;
  }

  @Override
  public ExpenseSnapshotResponse present(FinancialSnapshot snapshot) {
    return responseMapper.toResponse(calculator.calculate(snapshot, clock));
  }
}
