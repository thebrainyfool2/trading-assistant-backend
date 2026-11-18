package com.tradingassistant.execution.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable outcome of one execution attempt.
 *
 * <p>The result describes what happened: the order state, the affected position,
 * the portfolio snapshot, the rule outcomes and the broker report. It is not a
 * recommendation and not an instruction.</p>
 *
 * @param status          outcome of the attempt
 * @param order           order state, empty when no order was created
 * @param position        affected position, empty when nothing was filled
 * @param portfolio       portfolio snapshot after the attempt
 * @param ruleEvaluations rule outcomes in registry order
 * @param report          broker report, empty when the broker was not reached
 * @param reason          reason for a rejection or failure, when known
 * @param diagnostics     descriptive attempt details
 */
public record ExecutionResult(
        ExecutionStatus status,
        Optional<TradeOrder> order,
        Optional<Position> position,
        PortfolioSnapshot portfolio,
        List<ExecutionRuleEvaluation> ruleEvaluations,
        Optional<ExecutionReport> report,
        Optional<String> reason,
        ExecutionDiagnostics diagnostics) {

    public ExecutionResult {
        status = Objects.requireNonNull(status, "status must not be null");
        order = order == null ? Optional.empty() : order;
        position = position == null ? Optional.empty() : position;
        portfolio = Objects.requireNonNull(portfolio, "portfolio must not be null");
        if (ruleEvaluations == null || ruleEvaluations.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("ruleEvaluations must not contain null entries");
        }
        ruleEvaluations = List.copyOf(ruleEvaluations);
        report = report == null ? Optional.empty() : report;
        reason = reason == null ? Optional.empty() : reason;
        diagnostics = Objects.requireNonNull(diagnostics, "diagnostics must not be null");
    }

    public boolean accepted() {
        return status == ExecutionStatus.COMPLETED || status == ExecutionStatus.PARTIAL;
    }

    /**
     * Quantity the broker filled during this attempt, zero when none was.
     */
    public java.math.BigDecimal filledQuantity() {
        return order.map(TradeOrder::filledQuantity).orElse(java.math.BigDecimal.ZERO);
    }
}
