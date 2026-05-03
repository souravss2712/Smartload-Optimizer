package com.smartload.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum OptimizationAlgorithm {
    BITMASK_DP("bitmask_dp"),
    BACKTRACKING("backtracking");

    private final String jsonValue;

    OptimizationAlgorithm(String jsonValue) {
        this.jsonValue = jsonValue;
    }

    @JsonValue
    public String getJsonValue() {
        return jsonValue;
    }

    @JsonCreator
    public static OptimizationAlgorithm fromJson(String value) {
        if (value == null || value.isBlank()) {
            return BITMASK_DP;
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (OptimizationAlgorithm algorithm : values()) {
            if (algorithm.jsonValue.equals(normalized)
                    || algorithm.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return algorithm;
            }
        }

        throw new IllegalArgumentException("Unsupported optimization algorithm: " + value);
    }
}
