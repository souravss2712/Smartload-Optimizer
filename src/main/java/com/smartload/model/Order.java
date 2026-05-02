package com.smartload.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    @NotBlank(message = "order.id is required")
    private String id;

    @NotNull(message = "order.payout_cents is required")
    @Min(value = 0, message = "order.payout_cents cannot be negative")
    @JsonProperty("payout_cents")
    private Long payoutCents;

    @NotNull(message = "order.weight_lbs is required")
    @Positive(message = "order.weight_lbs must be greater than 0")
    @JsonProperty("weight_lbs")
    private Long weightLbs;

    @NotNull(message = "order.volume_cuft is required")
    @Positive(message = "order.volume_cuft must be greater than 0")
    @JsonProperty("volume_cuft")
    private Long volumeCuft;

    @NotBlank(message = "order.origin is required")
    private String origin;

    @NotBlank(message = "order.destination is required")
    private String destination;

    @NotNull(message = "order.pickup_date is required")
    @JsonProperty("pickup_date")
    private LocalDate pickupDate;

    @NotNull(message = "order.delivery_date is required")
    @JsonProperty("delivery_date")
    private LocalDate deliveryDate;

    @JsonProperty("is_hazmat")
    @NotNull(message = "order.is_hazmat is required")
    private Boolean hazmat;

    @AssertTrue(message = "order.pickup_date must be on or before delivery_date")
    public boolean isPickupDateOnOrBeforeDeliveryDate() {
        return pickupDate == null || deliveryDate == null || !pickupDate.isAfter(deliveryDate);
    }
}
