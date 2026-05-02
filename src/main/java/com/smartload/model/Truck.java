package com.smartload.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Truck {

    @NotBlank(message = "truck.id is required")
    private String id;

    @NotNull(message = "truck.max_weight_lbs is required")
    @Positive(message = "truck.max_weight_lbs must be greater than 0")
    @JsonProperty("max_weight_lbs")
    private Long maxWeightLbs;

    @NotNull(message = "truck.max_volume_cuft is required")
    @Positive(message = "truck.max_volume_cuft must be greater than 0")
    @JsonProperty("max_volume_cuft")
    private Long maxVolumeCuft;
}
