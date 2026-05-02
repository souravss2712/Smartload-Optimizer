package com.smartload.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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

    @Valid
    @NotNull(message = "truck is required")
    private Truck truck;

    @Valid
    @NotNull(message = "orders is required")
    @Builder.Default
    private List<Order> orders = new ArrayList<>();
}
