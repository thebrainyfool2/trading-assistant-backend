package com.tradingassistant.risk.engine;

import com.tradingassistant.qualification.domain.QualificationKey;
import com.tradingassistant.risk.api.RiskRule;
import com.tradingassistant.risk.config.RiskEngineConfig;
import com.tradingassistant.risk.domain.DailyLossValidation;
import com.tradingassistant.risk.domain.ExposureValidation;
import com.tradingassistant.risk.domain.RiskAssessment;
import com.tradingassistant.risk.domain.RiskContext;
import com.tradingassistant.risk.domain.RiskPlan;
import com.tradingassistant.risk.domain.RiskRewardValidation;
import com.tradingassistant.risk.domain.RiskRuleContext;
import com.tradingassistant.risk.domain.RiskRuleEvaluation;
import com.tradingassistant.risk.domain.RiskRuleStatus;
import com.tradingassistant.risk.domain.RiskStatus;
import com.tradingassistant.risk.exception.RiskEvaluationException;
import com.tradingassistant.risk.math.RiskCalculator;
import com.tradingassistant.risk.math.RiskNumbers;
import com.tradingassistant.risk.registry.RiskRuleRegistry;
import com.tradingassistant.risk.validation.RiskValidator;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

/**
 * Derives the risk boundaries of one qualified trade.
 *
 * <p>The engine is stateless: it holds configuration, a registry, a calculator and
 * an injected executor only. The plan is derived once, every rule checks the same
 * numbers, rules never see each other, and results are returned in registry order
 * even when evaluation runs in parallel.</p>
 *
 * <p>The engine answers "what are the allowable risk parameters?" and never
 * "should this trade be taken?". It places nothing and talks to no broker.</p>
 */
public final class RiskEngine {

    private final RiskEngineConfig config;
    private final RiskRuleRegistry registry;
    private final RiskCalculator calculator;
    private final Executor executor;

    public RiskEngine(RiskEngineConfig config, RiskRuleRegistry registry, Executor executor) {
        this(config, registry, new RiskCalculator(), executor);
    }

    public RiskEngine(RiskEngineConfig config, RiskRuleRegistry registry,
                      RiskCalculator calculator, Executor executor) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.calculator = Objects.requireNonNull(calculator, "calculator must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
    }

    /**
     * Assesses a context with the current registry snapshot.
     */
    public RiskAssessment evaluate(RiskContext context) {
        RiskValidator.validateContext(context);
        return evaluate(context, registry.snapshot());
    }

    /**
     * Assesses a context against an explicit rule set.
     *
     * @throws RiskEvaluationException when fail-fast is configured and a rule throws,
     *                                 or when a parallel task fails
     */
    public RiskAssessment evaluate(RiskContext context, List<RiskRule> rules) {
        RiskValidator.validateContext(context);
        RiskValidator.validateRules(rules, config);

        RiskPlan plan = calculator.calculate(context, config);
        RiskRuleContext ruleContext = new RiskRuleContext(context, plan, config);

        if (config.strictValidation() && !plan.stopAvailable()) {
            List<RiskRuleEvaluation> evaluations = rules.stream()
                    .map(rule -> RiskRuleEvaluation.insufficient(rule.id(),
                            "stop distance could not be derived"))
                    .toList();
            return assessment(context, plan, evaluations, RiskStatus.INSUFFICIENT_DATA,
                    "stop distance could not be derived");
        }

        List<RiskRuleEvaluation> evaluations = config.parallelEvaluation() && rules.size() > 1
                ? evaluateInParallel(ruleContext, rules)
                : evaluateSequentially(ruleContext, rules);
        RiskStatus status = aggregate(evaluations);
        return assessment(context, plan, evaluations, status, summary(evaluations));
    }

    private List<RiskRuleEvaluation> evaluateSequentially(RiskRuleContext context,
                                                          List<RiskRule> rules) {
        List<RiskRuleEvaluation> evaluations = new ArrayList<>(rules.size());
        for (RiskRule rule : rules) {
            evaluations.add(evaluateRule(context, rule));
        }
        return List.copyOf(evaluations);
    }

    private List<RiskRuleEvaluation> evaluateInParallel(RiskRuleContext context,
                                                        List<RiskRule> rules) {
        List<Future<RiskRuleEvaluation>> futures = new ArrayList<>(rules.size());
        for (RiskRule rule : rules) {
            FutureTask<RiskRuleEvaluation> task = new FutureTask<>(
                    () -> evaluateRule(context, rule));
            futures.add(task);
            executor.execute(task);
        }
        List<RiskRuleEvaluation> evaluations = new ArrayList<>(rules.size());
        for (Future<RiskRuleEvaluation> future : futures) {
            try {
                evaluations.add(future.get());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new RiskEvaluationException("parallel risk evaluation was interrupted",
                        interrupted);
            } catch (ExecutionException failure) {
                Throwable cause = failure.getCause();
                if (cause instanceof RiskEvaluationException riskFailure) {
                    throw riskFailure;
                }
                throw new RiskEvaluationException("parallel risk evaluation failed", cause);
            }
        }
        return List.copyOf(evaluations);
    }

    private RiskRuleEvaluation evaluateRule(RiskRuleContext context, RiskRule rule) {
        try {
            RiskRuleEvaluation evaluation = rule.evaluate(context);
            if (evaluation == null) {
                if (config.failFast()) {
                    throw new RiskEvaluationException(
                            "rule " + rule.id() + " returned no evaluation");
                }
                return RiskRuleEvaluation.unknown(rule.id(), "rule returned no evaluation");
            }
            return evaluation;
        } catch (RuntimeException failure) {
            if (config.failFast()) {
                throw new RiskEvaluationException("rule " + rule.id() + " failed", failure);
            }
            return RiskRuleEvaluation.unknown(rule.id(), "rule evaluation failed");
        }
    }

    private RiskStatus aggregate(List<RiskRuleEvaluation> evaluations) {
        long passed = count(evaluations, RiskRuleStatus.PASSED);
        long failed = count(evaluations, RiskRuleStatus.FAILED);
        long total = evaluations.size();
        if (failed > 0) {
            return RiskStatus.LIMIT_BREACHED;
        }
        if (passed == total) {
            return RiskStatus.WITHIN_LIMITS;
        }
        if (passed > 0) {
            return RiskStatus.PARTIAL;
        }
        if (count(evaluations, RiskRuleStatus.UNKNOWN) > 0) {
            return RiskStatus.UNKNOWN;
        }
        return RiskStatus.INSUFFICIENT_DATA;
    }

    private String summary(List<RiskRuleEvaluation> evaluations) {
        long passed = count(evaluations, RiskRuleStatus.PASSED);
        long failed = count(evaluations, RiskRuleStatus.FAILED);
        long undecided = evaluations.size() - passed - failed;
        StringBuilder summary = new StringBuilder()
                .append(passed).append(" of ").append(evaluations.size())
                .append(" boundaries held");
        if (failed > 0) {
            summary.append(", ").append(failed).append(" breached");
        }
        if (undecided > 0) {
            summary.append(", ").append(undecided).append(" undecided");
        }
        return summary.toString();
    }

    private RiskAssessment assessment(RiskContext context, RiskPlan plan,
                                      List<RiskRuleEvaluation> evaluations,
                                      RiskStatus status, String summary) {
        var evaluation = context.strategyEvaluation();
        BigDecimal maximumPermittedLoss = plan.sizingAvailable()
                ? plan.stopDistance().multiply(plan.quantity())
                : plan.riskAmount();

        RiskRewardValidation riskReward = new RiskRewardValidation(
                config.minimumRewardRiskRatio(),
                plan.stopAvailable()
                        ? Optional.of(plan.stopDistance()
                                .multiply(config.minimumRewardRiskRatio()))
                        : Optional.empty(),
                plan.availableReward(),
                plan.stopAvailable() && plan.availableReward().isPresent()
                        ? Optional.of(RiskNumbers.divide(plan.availableReward().get(),
                                plan.stopDistance()))
                        : Optional.empty());

        DailyLossValidation dailyLoss = new DailyLossValidation(
                config.dailyLossLimit(),
                Optional.of(plan.riskAmount()),
                Optional.of(RiskNumbers.percentOf(plan.riskAmount(),
                        config.dailyLossLimit())));

        ExposureValidation exposure = new ExposureValidation(
                config.maximumExposure(),
                plan.sizingAvailable() ? Optional.of(plan.positionValue()) : Optional.empty(),
                plan.sizingAvailable()
                        ? Optional.of(RiskNumbers.percentOf(plan.positionValue(),
                                config.maximumExposure()))
                        : Optional.empty());

        var qualification = context.qualification();
        var qualificationKey = new QualificationKey(qualification.strategyId(),
                qualification.instrument(), qualification.timestamp());
        return new RiskAssessment(evaluation.strategyId(), evaluation.strategyName(),
                evaluation.instrument(), evaluation.interval(), evaluation.timestamp(),
                qualificationKey, qualification.status(), status, plan.riskAmount(),
                maximumPermittedLoss, plan.stop(), plan.atrMultiple(),
                plan.volatilityAdjustment(), plan.sizing().orElse(
                        com.tradingassistant.risk.domain.PositionSizing.none()),
                config.maximumLeverage(), riskReward, dailyLoss, exposure, evaluations,
                summary, diagnostics(context, plan));
    }

    private Map<String, String> diagnostics(RiskContext context, RiskPlan plan) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("capital", RiskNumbers.format(plan.capital()));
        values.put("riskPercent", RiskNumbers.format(plan.riskPercent()));
        values.put("riskAmount", RiskNumbers.format(plan.riskAmount()));
        values.put("strategyStatus", context.strategyEvaluation().status().name());
        values.put("qualificationStatus", context.qualification().status().name());
        values.put("volatilityState",
                context.marketAssessment().volatility().state().name());
        values.put("volatilityAdjustment", RiskNumbers.format(plan.volatilityAdjustment()));
        values.put("atrMultiple", RiskNumbers.format(plan.atrMultiple()));
        values.put("atr", plan.atr().map(RiskNumbers::format).orElse("unavailable"));
        values.put("stopMethodology", plan.stop().methodology().name());
        values.put("stopDistance", RiskNumbers.format(plan.stopDistance()));
        values.put("quantity", RiskNumbers.format(plan.quantity()));
        values.put("positionValue", RiskNumbers.format(plan.positionValue()));
        values.put("requiredLeverage", RiskNumbers.format(plan.requiredLeverage()));
        values.put("fractional", Boolean.toString(plan.fractionalApplied()));
        return Map.copyOf(values);
    }

    private static long count(List<RiskRuleEvaluation> evaluations, RiskRuleStatus status) {
        return evaluations.stream().filter(evaluation -> evaluation.status() == status).count();
    }

    public RiskEngineConfig config() {
        return config;
    }

    public RiskRuleRegistry registry() {
        return registry;
    }
}
