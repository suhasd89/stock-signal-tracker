package com.suhas.stocktracker.model;

public enum StrategyType {
    SMA("sma", "SMA Strategy: 20/50/200"),
    V20("v20", "V20");

    private final String slug;
    private final String displayName;

    StrategyType(String slug, String displayName) {
        this.slug = slug;
        this.displayName = displayName;
    }

    public String slug() {
        return slug;
    }

    public String displayName() {
        return displayName;
    }

    public static StrategyType fromSlug(String raw) {
        if (raw == null || raw.isBlank()) {
            return SMA;
        }
        for (StrategyType strategyType : values()) {
            if (strategyType.slug.equalsIgnoreCase(raw)) {
                return strategyType;
            }
        }
        throw new IllegalArgumentException("Unknown strategy: " + raw);
    }
}
