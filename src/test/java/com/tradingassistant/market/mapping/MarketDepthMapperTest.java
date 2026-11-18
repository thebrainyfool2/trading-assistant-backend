package com.tradingassistant.market.mapping;

import com.tradingassistant.market.domain.MarketDepth;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MarketDepthMapperTest {

    private final MarketDepthMapper mapper = new MarketDepthMapper();

    @Test
    void mapsBothSidesAndPreservesOptionalFields() {
        MarketDepth depth = mapper.toDomain(new com.tradingassistant.broker.model.MarketDepth(
                List.of(new com.tradingassistant.broker.model.DepthLevel(
                        new BigDecimal("101.20"), 5L, null)),
                List.of(new com.tradingassistant.broker.model.DepthLevel(
                        new BigDecimal("101.30"), null, 3L))));

        assertThat(depth.buy()).hasSize(1);
        assertThat(depth.buy().getFirst().price()).isEqualByComparingTo("101.20");
        assertThat(depth.buy().getFirst().orders()).isNull();
        assertThat(depth.sell().getFirst().quantity()).isNull();
        assertThat(depth.sell().getFirst().orders()).isEqualTo(3L);
    }

    @Test
    void nullDepthAndNullSidesBecomeEmpty() {
        assertThat(mapper.toDomain(null)).isEqualTo(MarketDepth.empty());
        assertThat(mapper.toDomain(new com.tradingassistant.broker.model.MarketDepth(null, null)))
                .isEqualTo(MarketDepth.empty());
    }

}
