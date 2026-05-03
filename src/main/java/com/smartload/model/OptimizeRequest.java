package com.smartload.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OptimizeRequest {

    /** Matches optimizer bitmask limits (see assessment max ~22 orders). */
    public static final int MAX_ORDERS = 22;

    @Valid
    @NotNull(message = "truck is required")
    private Truck truck;

    @Valid
    @NotNull(message = "orders is required")
    @Size(max = MAX_ORDERS, message = "orders cannot contain more than {max} items")
    @Builder.Default
    private List<Order> orders = new ArrayList<>();

    @Valid
    @Builder.Default
    private OptimizationPreferences preferences = new OptimizationPreferences();
}
