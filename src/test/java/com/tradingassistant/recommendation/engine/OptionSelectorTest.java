package com.tradingassistant.recommendation.engine;

import com.tradingassistant.recommendation.RecommendationTestFixtures;
import com.tradingassistant.recommendation.config.ExpirySelection;
import com.tradingassistant.recommendation.config.StrikeMode;
import com.tradingassistant.recommendation.domain.OptionSelection;
import com.tradingassistant.recommendation.domain.RecommendationType;
import com.tradingassistant.recommendation.exception.RecommendationValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OptionSelectorTest {

    private final OptionSelector selector = new OptionSelector();

    @Test
    void selectsTheConfiguredAtmCall() {
        OptionSelection selection = selector.select(RecommendationType.BUY_CALL,
                RecommendationTestFixtures.bullishContext(),
                RecommendationTestFixtures.config()).orElseThrow();

        assertThat(selection.strike()).isEqualByComparingTo("500");
        assertThat(selection.optionType().isCall()).isTrue();
    }

    @Test
    void selectsOneOtmCallAndOneOtmPut() {
        OptionSelection call = selector.select(RecommendationType.BUY_CALL,
                RecommendationTestFixtures.bullishContext(),
                RecommendationTestFixtures.configWithSelection(StrikeMode.OTM, 1,
                        ExpirySelection.NEAREST)).orElseThrow();
        OptionSelection put = selector.select(RecommendationType.BUY_PUT,
                RecommendationTestFixtures.bearishContext(),
                RecommendationTestFixtures.configWithSelection(StrikeMode.OTM, 1,
                        ExpirySelection.NEAREST)).orElseThrow();

        assertThat(call.strike()).isEqualByComparingTo("510");
        assertThat(put.strike()).isEqualByComparingTo("490");
        assertThat(call.offsetSteps()).isEqualTo(1);
    }

    @Test
    void selectsOneItmCallAndOneItmPut() {
        OptionSelection call = selector.select(RecommendationType.BUY_CALL,
                RecommendationTestFixtures.bullishContext(),
                RecommendationTestFixtures.configWithSelection(StrikeMode.ITM, 1,
                        ExpirySelection.NEAREST)).orElseThrow();
        OptionSelection put = selector.select(RecommendationType.BUY_PUT,
                RecommendationTestFixtures.bearishContext(),
                RecommendationTestFixtures.configWithSelection(StrikeMode.ITM, 1,
                        ExpirySelection.NEAREST)).orElseThrow();

        assertThat(call.strike()).isEqualByComparingTo("490");
        assertThat(put.strike()).isEqualByComparingTo("510");
    }

    @Test
    void rejectsAnExpiryThatTheChainCannotSupply() {
        assertThatThrownBy(() -> selector.select(RecommendationType.BUY_CALL,
                RecommendationTestFixtures.bullishContext(),
                RecommendationTestFixtures.configWithSelection(StrikeMode.ATM, 0,
                        ExpirySelection.NEXT)))
                .isInstanceOf(RecommendationValidationException.class)
                .hasMessageContaining("expiry");
    }

    @Test
    void noTradeNeverSelectsAContract() {
        assertThat(selector.select(RecommendationType.NO_TRADE,
                RecommendationTestFixtures.bullishContext(),
                RecommendationTestFixtures.config())).isEmpty();
    }

}
