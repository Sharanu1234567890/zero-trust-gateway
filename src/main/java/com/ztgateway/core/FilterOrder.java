package com.ztgateway.core;

public enum FilterOrder {
    GEO_FENCE(1),
    AUTH(2),
    RATE_LIMIT(3),
    PAYLOAD_SCAN(4),
    THREAT(5),
    CIRCUIT_BREAKER(6);

    private final int order;

    FilterOrder(int order) {
        this.order = order;
    }

    public int getOrder() {
        return order;
    }
}
