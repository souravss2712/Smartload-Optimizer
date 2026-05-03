package com.smartload.model;

import com.fasterxml.jackson.annotation.JsonProperty;
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
public class LoadSolution {

    @JsonProperty("selected_order_ids")
    @Builder.Default
    private List<String> selectedOrderIds = new ArrayList<>();

    @JsonProperty("total_payout_cents")
    private long totalPayoutCents;

    @JsonProperty("total_weight_lbs")
    private long totalWeightLbs;

    @JsonProperty("total_volume_cuft")
    private long totalVolumeCuft;

    @JsonProperty("utilization_weight_percent")
    private double utilizationWeightPercent;

    @JsonProperty("utilization_volume_percent")
    private double utilizationVolumePercent;

    @JsonProperty("utilization_score_percent")
    private double utilizationScorePercent;
}
