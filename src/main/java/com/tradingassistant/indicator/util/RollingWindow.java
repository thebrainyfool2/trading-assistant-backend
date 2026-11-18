package com.tradingassistant.indicator.util;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Fixed-size rolling value window with an exact running sum.
 */
public final class RollingWindow {

    private final int capacity;
    private final Deque<BigDecimal> values = new ArrayDeque<>();
    private BigDecimal sum = BigDecimal.ZERO;

    public RollingWindow(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
    }

    public void add(BigDecimal value) {
        values.addLast(value);
        sum = sum.add(value);
        if (values.size() > capacity) {
            sum = sum.subtract(values.removeFirst());
        }
    }

    public int size() {
        return values.size();
    }

    public int capacity() {
        return capacity;
    }

    public boolean isFull() {
        return values.size() == capacity;
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public BigDecimal sum() {
        return sum;
    }

    public List<BigDecimal> values() {
        return List.copyOf(new ArrayList<>(values));
    }

    public void clear() {
        values.clear();
        sum = BigDecimal.ZERO;
    }
}
