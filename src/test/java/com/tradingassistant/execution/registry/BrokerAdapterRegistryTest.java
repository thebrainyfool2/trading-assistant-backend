package com.tradingassistant.execution.registry;

import com.tradingassistant.execution.ExecutionTestFixtures;
import com.tradingassistant.execution.broker.BrokerAdapter;
import com.tradingassistant.execution.broker.BrokerAdapterRegistry;
import com.tradingassistant.execution.broker.PaperBrokerAdapter;
import com.tradingassistant.execution.exception.ExecutionValidationException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrokerAdapterRegistryTest {

    private static PaperBrokerAdapter adapter(String name) {
        return new PaperBrokerAdapter(name, ExecutionTestFixtures.clock());
    }

    @Test
    void registersAndSelectsAnAdapter() {
        BrokerAdapterRegistry registry = new BrokerAdapterRegistry();
        registry.register(adapter("paper"));

        assertThat(registry.select("paper").brokerName()).isEqualTo("paper");
        assertThat(registry.lookup("paper")).isPresent();
        assertThat(registry.size()).isEqualTo(1);
    }

    @Test
    void rejectsADuplicateName() {
        BrokerAdapterRegistry registry = new BrokerAdapterRegistry();
        registry.register(adapter("paper"));

        assertThatThrownBy(() -> registry.register(adapter("paper")))
                .isInstanceOf(ExecutionValidationException.class)
                .hasMessageContaining("duplicate broker");
    }

    @Test
    void selectingAnUnknownAdapterFails() {
        BrokerAdapterRegistry registry = new BrokerAdapterRegistry();

        assertThatThrownBy(() -> registry.select("missing"))
                .isInstanceOf(ExecutionValidationException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void blankAndNullLookupsYieldEmpty() {
        BrokerAdapterRegistry registry = new BrokerAdapterRegistry();

        assertThat(registry.lookup(" ")).isEmpty();
        assertThat(registry.lookup(null)).isEmpty();
    }

    @Test
    void replacesAKnownAdapterAndRejectsAnUnknownOne() {
        BrokerAdapterRegistry registry = new BrokerAdapterRegistry();
        registry.register(adapter("paper"));

        registry.replace(adapter("paper"));
        assertThat(registry.size()).isEqualTo(1);

        assertThatThrownBy(() -> registry.replace(adapter("other")))
                .isInstanceOf(ExecutionValidationException.class)
                .hasMessageContaining("unknown broker");
    }

    @Test
    void unregistersAdapters() {
        BrokerAdapterRegistry registry = new BrokerAdapterRegistry();
        registry.register(adapter("paper"));

        assertThat(registry.unregister("paper")).isTrue();
        assertThat(registry.unregister("paper")).isFalse();
        assertThat(registry.unregister(null)).isFalse();
    }

    @Test
    void snapshotIsImmutableAndSorted() {
        BrokerAdapterRegistry registry = new BrokerAdapterRegistry(
                List.of(adapter("zeta"), adapter("alpha")));

        assertThat(registry.snapshot()).extracting(BrokerAdapter::brokerName)
                .containsExactly("alpha", "zeta");
        assertThatThrownBy(() -> registry.snapshot().add(adapter("other")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsNullAndUnnamedAdapters() {
        BrokerAdapterRegistry registry = new BrokerAdapterRegistry();

        assertThatThrownBy(() -> registry.register(null))
                .isInstanceOf(ExecutionValidationException.class);
        assertThatThrownBy(() -> registry.register(adapter(" ")))
                .isInstanceOf(ExecutionValidationException.class);
    }

    @Test
    void constructsFromAnInitialCollection() {
        BrokerAdapterRegistry registry = new BrokerAdapterRegistry(List.of(adapter("paper")));

        assertThat(registry.size()).isEqualTo(1);
        assertThat(registry.select(" paper ").brokerName()).isEqualTo("paper");
    }
}
