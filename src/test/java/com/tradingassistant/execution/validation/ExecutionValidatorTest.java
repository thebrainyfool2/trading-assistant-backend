package com.tradingassistant.execution.validation;

import com.tradingassistant.execution.ExecutionTestFixtures;
import com.tradingassistant.execution.api.ExecutionRule;
import com.tradingassistant.execution.broker.PaperBrokerAdapter;
import com.tradingassistant.execution.config.ExecutionConfig;
import com.tradingassistant.execution.config.ExecutionMode;
import com.tradingassistant.execution.domain.ExecutionRuleContext;
import com.tradingassistant.execution.domain.ExecutionRuleEvaluation;
import com.tradingassistant.execution.exception.ExecutionValidationException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExecutionValidatorTest {

    private static ExecutionRule rule(String id) {
        return new ExecutionRule() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public ExecutionRuleEvaluation evaluate(ExecutionRuleContext context) {
                return ExecutionRuleEvaluation.passed(id, "ok", Map.of());
            }
        };
    }

    @Test
    void acceptsAUsableRequest() {
        assertThatCode(() -> ExecutionValidator.validateRequest(
                ExecutionTestFixtures.executionRequest())).doesNotThrowAnyException();
    }

    @Test
    void rejectsANullRequest() {
        assertThatThrownBy(() -> ExecutionValidator.validateRequest(null))
                .isInstanceOf(ExecutionValidationException.class);
    }

    @Test
    void rejectsBlankOrderIds() {
        assertThatThrownBy(() -> ExecutionValidator.validateOrderId(null))
                .isInstanceOf(ExecutionValidationException.class);
        assertThatThrownBy(() -> ExecutionValidator.validateOrderId(" "))
                .isInstanceOf(ExecutionValidationException.class);
    }

    @Test
    void acceptsADistinctRuleBatch() {
        assertThatCode(() -> ExecutionValidator.validateRules(
                List.of(rule("A"), rule("B")))).doesNotThrowAnyException();
    }

    @Test
    void rejectsNullOrEmptyRuleBatches() {
        assertThatThrownBy(() -> ExecutionValidator.validateRules(null))
                .isInstanceOf(ExecutionValidationException.class);
        assertThatThrownBy(() -> ExecutionValidator.validateRules(List.of()))
                .isInstanceOf(ExecutionValidationException.class);
    }

    @Test
    void rejectsANullRuleOrBlankIdentifier() {
        assertThatThrownBy(() -> ExecutionValidator.validateRules(
                java.util.Arrays.asList(rule("A"), null)))
                .isInstanceOf(ExecutionValidationException.class);
        assertThatThrownBy(() -> ExecutionValidator.validateRules(List.of(rule(" "))))
                .isInstanceOf(ExecutionValidationException.class);
    }

    @Test
    void rejectsDuplicateRuleIdentifiers() {
        assertThatThrownBy(() -> ExecutionValidator.validateRules(
                List.of(rule("A"), rule("A"))))
                .isInstanceOf(ExecutionValidationException.class)
                .hasMessageContaining("duplicate rule id A");
    }

    @Test
    void liveModeCannotBeWiredToThePaperAdapter() {
        ExecutionConfig live = ExecutionConfig.builder().mode(ExecutionMode.LIVE).build();
        PaperBrokerAdapter paper = new PaperBrokerAdapter(ExecutionTestFixtures.clock());

        assertThatThrownBy(() -> ExecutionValidator.validateMode(live, paper))
                .isInstanceOf(ExecutionValidationException.class)
                .hasMessageContaining("LIVE");
    }

    @Test
    void paperModeAcceptsThePaperAdapter() {
        assertThatCode(() -> ExecutionValidator.validateMode(
                ExecutionConfig.DEFAULT, new PaperBrokerAdapter(
                        ExecutionTestFixtures.clock()))).doesNotThrowAnyException();
    }
}
