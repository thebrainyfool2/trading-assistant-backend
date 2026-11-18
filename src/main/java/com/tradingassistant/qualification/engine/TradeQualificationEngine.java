package com.tradingassistant.qualification.engine;

import com.tradingassistant.qualification.api.QualificationRule;
import com.tradingassistant.qualification.config.TradeQualificationConfig;
import com.tradingassistant.qualification.domain.QualificationRuleEvaluation;
import com.tradingassistant.qualification.domain.QualificationStatus;
import com.tradingassistant.qualification.domain.RuleStatus;
import com.tradingassistant.qualification.domain.TradeQualification;
import com.tradingassistant.qualification.domain.TradeQualificationContext;
import com.tradingassistant.qualification.exception.QualificationEvaluationException;
import com.tradingassistant.qualification.registry.QualificationRuleRegistry;
import com.tradingassistant.qualification.validation.QualificationValidator;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

/**
 * Qualifies one strategy evaluation against the registered qualification rules.
 *
 * <p>The engine is stateless: it holds configuration, a registry and an injected
 * executor only. Every rule sees the same immutable context, rules never see each
 * other, and results are returned in registry order even when evaluation runs in
 * parallel.</p>
 *
 * <p>The engine answers one question: is this strategy evaluation currently
 * eligible for downstream consideration? It never recommends, ranks, sizes,
 * allocates or trades.</p>
 */
public final class TradeQualificationEngine {

    private final TradeQualificationConfig config;
    private final QualificationRuleRegistry registry;
    private final Executor executor;

    public TradeQualificationEngine(TradeQualificationConfig config,
                                    QualificationRuleRegistry registry,
                                    Executor executor) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
    }

    /**
     * Qualifies a context with the current registry snapshot.
     */
    public TradeQualification evaluate(TradeQualificationContext context) {
        QualificationValidator.validateContext(context);
        return evaluate(context, registry.snapshot());
    }

    /**
     * Qualifies a context against an explicit rule set.
     *
     * @throws QualificationEvaluationException when fail-fast is configured and a
     *                                          rule throws, or when a parallel task
     *                                          fails
     */
    public TradeQualification evaluate(TradeQualificationContext context,
                                       List<QualificationRule> rules) {
        QualificationValidator.validateContext(context);
        QualificationValidator.validateRules(rules, config);

        if (config.strictValidation() && config.requireOptions()
                && context.optionsAssessment().isEmpty()) {
            List<QualificationRuleEvaluation> evaluations = rules.stream()
                    .map(rule -> QualificationRuleEvaluation.insufficient(rule.id(),
                            "required input [OPTIONS_ASSESSMENT] is unavailable"))
                    .toList();
            return result(context, evaluations, QualificationStatus.INSUFFICIENT_DATA,
                    "required option assessment is unavailable");
        }

        List<QualificationRuleEvaluation> evaluations =
                config.parallelEvaluation() && rules.size() > 1
                        ? evaluateInParallel(context, rules)
                        : evaluateSequentially(context, rules);
        QualificationStatus status = aggregate(evaluations);
        return result(context, evaluations, status, summary(evaluations, status));
    }

    private List<QualificationRuleEvaluation> evaluateSequentially(
            TradeQualificationContext context, List<QualificationRule> rules) {
        List<QualificationRuleEvaluation> evaluations = new ArrayList<>(rules.size());
        for (QualificationRule rule : rules) {
            evaluations.add(evaluateRule(context, rule));
        }
        return List.copyOf(evaluations);
    }

    private List<QualificationRuleEvaluation> evaluateInParallel(
            TradeQualificationContext context, List<QualificationRule> rules) {
        List<Future<QualificationRuleEvaluation>> futures = new ArrayList<>(rules.size());
        for (QualificationRule rule : rules) {
            FutureTask<QualificationRuleEvaluation> task =
                    new FutureTask<>(() -> evaluateRule(context, rule));
            futures.add(task);
            executor.execute(task);
        }
        List<QualificationRuleEvaluation> evaluations = new ArrayList<>(rules.size());
        for (Future<QualificationRuleEvaluation> future : futures) {
            try {
                evaluations.add(future.get());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new QualificationEvaluationException(
                        "parallel qualification was interrupted", interrupted);
            } catch (ExecutionException failure) {
                Throwable cause = failure.getCause();
                if (cause instanceof QualificationEvaluationException qualificationFailure) {
                    throw qualificationFailure;
                }
                throw new QualificationEvaluationException(
                        "parallel qualification failed", cause);
            }
        }
        return List.copyOf(evaluations);
    }

    private QualificationRuleEvaluation evaluateRule(TradeQualificationContext context,
                                                     QualificationRule rule) {
        try {
            QualificationRuleEvaluation evaluation = rule.evaluate(context);
            if (evaluation == null) {
                if (config.failFast()) {
                    throw new QualificationEvaluationException(
                            "rule " + rule.id() + " returned no evaluation");
                }
                return QualificationRuleEvaluation.unknown(rule.id(),
                        "rule returned no evaluation");
            }
            return evaluation;
        } catch (RuntimeException failure) {
            if (config.failFast()) {
                throw new QualificationEvaluationException(
                        "rule " + rule.id() + " failed", failure);
            }
            return QualificationRuleEvaluation.unknown(rule.id(),
                    "rule evaluation failed");
        }
    }

    private QualificationStatus aggregate(List<QualificationRuleEvaluation> evaluations) {
        long passed = count(evaluations, RuleStatus.PASSED);
        long failed = count(evaluations, RuleStatus.FAILED);
        long unknown = count(evaluations, RuleStatus.UNKNOWN);
        long total = evaluations.size();
        if (failed > 0) {
            return QualificationStatus.DISQUALIFIED;
        }
        if (passed == total) {
            return QualificationStatus.QUALIFIED;
        }
        if (passed > 0) {
            return config.allowPartialQualification()
                    ? QualificationStatus.QUALIFIED
                    : QualificationStatus.PARTIAL;
        }
        if (unknown > 0) {
            return QualificationStatus.UNKNOWN;
        }
        if (count(evaluations, RuleStatus.INSUFFICIENT_DATA) > 0) {
            return QualificationStatus.INSUFFICIENT_DATA;
        }
        return QualificationStatus.UNKNOWN;
    }

    private String summary(List<QualificationRuleEvaluation> evaluations,
                           QualificationStatus status) {
        long passed = count(evaluations, RuleStatus.PASSED);
        long failed = count(evaluations, RuleStatus.FAILED);
        long undecided = evaluations.size() - passed - failed;
        StringBuilder summary = new StringBuilder()
                .append(passed).append(" of ").append(evaluations.size())
                .append(" rules passed");
        if (failed > 0) {
            summary.append(", ").append(failed).append(" failed");
        }
        if (undecided > 0) {
            summary.append(", ").append(undecided).append(" undecided");
        }
        if (status == QualificationStatus.QUALIFIED && undecided > 0) {
            summary.append(" (partial qualification accepted)");
        }
        return summary.toString();
    }

    private static long count(List<QualificationRuleEvaluation> evaluations,
                              RuleStatus status) {
        return evaluations.stream().filter(evaluation -> evaluation.status() == status).count();
    }

    private TradeQualification result(TradeQualificationContext context,
                                      List<QualificationRuleEvaluation> evaluations,
                                      QualificationStatus status,
                                      String summary) {
        var evaluation = context.strategyEvaluation();
        return new TradeQualification(evaluation.strategyId(), evaluation.strategyName(),
                evaluation.instrument(), evaluation.interval(), evaluation.timestamp(),
                status, evaluations, summary);
    }

    public TradeQualificationConfig config() {
        return config;
    }

    public QualificationRuleRegistry registry() {
        return registry;
    }
}
