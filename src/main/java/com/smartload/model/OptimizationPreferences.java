package com.smartload.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OptimizationPreferences {

    @JsonProperty("algorithm")
    private OptimizationAlgorithm algorithm;

    @JsonProperty("include_pareto_optimal_solutions")
    private Boolean includeParetoOptimalSolutions;

    @DecimalMin(value = "0.0", message = "preferences.revenue_weight cannot be negative")
    @JsonProperty("revenue_weight")
    private Double revenueWeight;

    @DecimalMin(value = "0.0", message = "preferences.weight_utilization_weight cannot be negative")
    @JsonProperty("weight_utilization_weight")
    private Double weightUtilizationWeight;

    @DecimalMin(value = "0.0", message = "preferences.volume_utilization_weight cannot be negative")
    @JsonProperty("volume_utilization_weight")
    private Double volumeUtilizationWeight;

    public OptimizationAlgorithm algorithmOrDefault() {
        return algorithm == null ? OptimizationAlgorithm.BITMASK_DP : algorithm;
    }

    public boolean includeParetoOptimalSolutionsOrDefault() {
        return Boolean.TRUE.equals(includeParetoOptimalSolutions);
    }

    public double revenueWeightOrDefault() {
        return revenueWeight == null ? 1.0 : revenueWeight;
    }

    public double weightUtilizationWeightOrDefault() {
        return weightUtilizationWeight == null ? 0.0 : weightUtilizationWeight;
    }

    public double volumeUtilizationWeightOrDefault() {
        return volumeUtilizationWeight == null ? 0.0 : volumeUtilizationWeight;
    }

    @AssertTrue(message = "at least one optimization weight must be greater than 0")
    public boolean isAtLeastOneOptimizationWeightPositive() {
        return revenueWeightOrDefault() > 0.0
                || weightUtilizationWeightOrDefault() > 0.0
                || volumeUtilizationWeightOrDefault() > 0.0;
    }
}
