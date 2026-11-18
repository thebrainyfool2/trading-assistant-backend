package com.tradingassistant.execution.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BoundedCacheTest {

    @Test
    void storesAndReadsValues() {
        BoundedCache<String, String> cache = new BoundedCache<>(2);
        cache.put("a", "1");

        assertThat(cache.get("a")).contains("1");
        assertThat(cache.get("b")).isEmpty();
        assertThat(cache.size()).isEqualTo(1);
    }

    @Test
    void evictsTheLeastRecentlyUsedEntry() {
        BoundedCache<String, String> cache = new BoundedCache<>(2);
        cache.put("a", "1");
        cache.put("b", "2");
        cache.get("a");
        cache.put("c", "3");

        assertThat(cache.get("b")).isEmpty();
        assertThat(cache.get("a")).isPresent();
        assertThat(cache.get("c")).isPresent();
    }

    @Test
    void ignoresNullKeysAndValues() {
        BoundedCache<String, String> cache = new BoundedCache<>(2);
        cache.put(null, "1");
        cache.put("a", null);

        assertThat(cache.size()).isZero();
        assertThat(cache.get(null)).isEmpty();
    }

    @Test
    void snapshotIsImmutable() {
        BoundedCache<String, String> cache = new BoundedCache<>(2);
        cache.put("a", "1");

        assertThat(cache.snapshot()).containsExactly("1");
        assertThatThrownBy(() -> cache.snapshot().add("2"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void clearRemovesEverything() {
        BoundedCache<String, String> cache = new BoundedCache<>(2);
        cache.put("a", "1");
        cache.clear();

        assertThat(cache.size()).isZero();
    }

    @Test
    void rejectsAnUnusableCapacity() {
        assertThatThrownBy(() -> new BoundedCache<String, String>(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void exposesItsCapacity() {
        assertThat(new BoundedCache<String, String>(7).capacity()).isEqualTo(7);
    }
}
