package com.tradingassistant.strategy.engine;

import com.tradingassistant.strategy.api.StrategyRule;
import com.tradingassistant.strategy.condition.ConditionSupport;
import com.tradingassistant.strategy.config.StrategyEngineConfig;
import com.tradingassistant.strategy.domain.EvaluationStatus;
import com.tradingassistant.strategy.domain.RuleEvaluation;
import com.tradingassistant.strategy.domain.StrategyDefinition;
import com.tradingassistant.strategy.domain.StrategyEvaluation;
import com.tradingassistant.strategy.domain.StrategyEvaluationContext;
import com.tradingassistant.strategy.domain.StrategyInput;
import com.tradingassistant.strategy.exception.StrategyEvaluationException;
import com.tradingassistant.strategy.validation.StrategyValidator;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

/**
 * Evaluates immutable strategy definitions against one immutable context.
 *
 * <p>The engine is stateless: it holds configuration and an injected executor
 * only. Strategies never see each other, every rule is evaluated against the
 * same context, and results are returned in the input order even when evaluation
 * runs in parallel.</p>
 *
 * <p>The engine answers one question per strategy: are its predefined conditions
 * currently true? It never ranks, scores, recommends, sizes or qualifies.</p>
 */
public final class StrategyEvaluationEngine {

    private final StrategyEngineConfig config;
    private final Executor executor;

    public StrategyEvaluationEngine(StrategyEngineConfig config, Executor executor) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
    }

    /**
     * Evaluates every enabled definition in the supplied list.
     *
     * @throws StrategyEvaluationException when fail-fast is configured and a rule
     *                                     throws, or when a parallel task fails
     */
    public List<StrategyEvaluation> evaluate(StrategyEvaluationContext context,
                                             List<StrategyDefinition> strategies) {
        StrategyValidator.validateContext(context);
        StrategyValidator.validateBatch(strategies, config);
        List<StrategyDefinition> enabled = strategies.stream()
                .filter(StrategyDefinition::enabled)
                .toList();
        if (enabled.isEmpty()) {
            return List.of();
        }
        if (config.parallelEvaluation() && enabled.size() > 1) {
            return evaluateInParallel(context, enabled);
        }
        List<StrategyEvaluation> evaluations = new ArrayList<>(enabled.size());
        for (StrategyDefinition definition : enabled) {
            evaluations.add(evaluateOne(context, definition));
        }
        return List.copyOf(evaluations);
    }

    /**
     * Evaluates a single definition.
     */
    public StrategyEvaluation evaluate(StrategyEvaluationContext context,
                                       StrategyDefinition definition) {
        StrategyValidator.validateContext(context);
        Objects.requireNonNull(definition, "definition must not be null");
        StrategyValidator.validateDefinition(definition, config);
        return evaluateOne(context, definition);
    }

    private List<StrategyEvaluation> evaluateInParallel(StrategyEvaluationContext context,
                                                        List<StrategyDefinition> definitions) {
        List<Future<StrategyEvaluation>> futures = new ArrayList<>(definitions.size());
        for (StrategyDefinition definition : definitions) {
            FutureTask<StrategyEvaluation> task = new FutureTask<>(
                    () -> evaluateOne(context, definition));
            futures.add(task);
            executor.execute(task);
        }
        List<StrategyEvaluation> evaluations = new ArrayList<>(definitions.size());
        for (Future<StrategyEvaluation> future : futures) {
            try {
                evaluations.add(future.get());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new StrategyEvaluationException(
                        "parallel strategy evaluation was interrupted", interrupted);
            } catch (ExecutionException failure) {
                Throwable cause = failure.getCause();
                if (cause instanceof StrategyEvaluationException strategyFailure) {
                    throw strategyFailure;
                }
                throw new StrategyEvaluationException(
                        "parallel strategy evaluation failed", cause);
            }
        }
        return List.copyOf(evaluations);
    }

    private StrategyEvaluation evaluateOne(StrategyEvaluationContext context,
                                           StrategyDefinition definition) {
        List<StrategyInput> missing = definition.requiredInputs().stream()
                .filter(input -> !ConditionSupport.available(context, input))
                .toList();
        if (config.strictValidation() && !missing.isEmpty()) {
            List<RuleEvaluation> evaluations = definition.rules().stream()
                    .map(rule -> RuleEvaluation.insufficient(rule.id(),
                            "declared input " + missing + " is unavailable"))
                    .toList();
            return new StrategyEvaluation(definition.id(), definition.name(),
                    context.marketAssessment().timestamp(),
                    context.marketAssessment().instrument(),
                    context.marketAssessment().interval(),
                    EvaluationStatus.INSUFFICIENT_DATA, evaluations,
                    "missing declared inputs " + missing);
        }

        List<RuleEvaluation> evaluations = new ArrayList<>(definition.rules().size());
        for (StrategyRule rule : definition.rules()) {
            evaluations.add(evaluateRule(context, rule));
        }
        EvaluationStatus status = aggregate(evaluations);
        long passed = evaluations.stream().filter(RuleEvaluation::passed).count();
        return new StrategyEvaluation(definition.id(), definition.name(),
                context.marketAssessment().timestamp(),
                context.marketAssessment().instrument(),
                context.marketAssessment().interval(),
                status, evaluations,
                passed + " of " + evaluations.size() + " rules passed");
    }

    private RuleEvaluation evaluateRule(StrategyEvaluationContext context, StrategyRule rule) {
        try {
            return rule.evaluate(context);
        } catch (RuntimeException failure) {
            if (config.failFast()) {
                throw new StrategyEvaluationException(
                        "rule " + rule.id() + " failed", failure);
            }
            return RuleEvaluation.unknown(rule.id(), "rule evaluation failed");
        }
    }

    private static EvaluationStatus aggregate(List<RuleEvaluation> evaluations) {
        long passed = evaluations.stream()
                .filter(rule -> rule.status() == EvaluationStatus.PASSED)
                .count();
        long failed = evaluations.stream()
                .filter(rule -> rule.status() == EvaluationStatus.FAILED)
                .count();
        long insufficient = evaluations.stream()
                .filter(rule -> rule.status() == EvaluationStatus.INSUFFICIENT_DATA)
                .count();
        long total = evaluations.size();
        if (failed > 0) {
            return EvaluationStatus.FAILED;
        }
        if (passed == total) {
            return EvaluationStatus.PASSED;
        }
        if (insufficient == total) {
            return EvaluationStatus.INSUFFICIENT_DATA;
        }
        if (passed > 0) {
            return EvaluationStatus.PARTIAL;
        }
        return EvaluationStatus.UNKNOWN;
    }

    public StrategyEngineConfig config() {
        return config;
    }
}
