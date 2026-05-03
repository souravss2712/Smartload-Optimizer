package com.smartload.optimizer;

import com.smartload.model.OptimizeResponse;
import com.smartload.model.Order;
import com.smartload.model.Truck;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class LoadOptimizer {

    public OptimizeResponse optimize(Truck truck, List<Order> orders) {
        if (orders == null || orders.isEmpty()) {
            return emptyResponse(truck);
        }

        Result best = Result.empty();
        for (List<Order> compatibleOrders : groupByRouteAndHazmat(orders).values()) {
            Result candidate = optimizeCompatibleGroup(truck, compatibleOrders);
            if (isBetter(candidate, best)) {
                best = candidate;
            }
        }

        return toResponse(truck, best);
    }

    private Map<GroupKey, List<Order>> groupByRouteAndHazmat(List<Order> orders) {
        Map<GroupKey, List<Order>> groups = new LinkedHashMap<>();
        for (Order order : orders) {
            GroupKey key = new GroupKey(
                    normalizeRouteValue(order.getOrigin()),
                    normalizeRouteValue(order.getDestination()),
                    Boolean.TRUE.equals(order.getHazmat()));
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(order);
        }
        return groups;
    }

    private String normalizeRouteValue(String value) {
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private Result optimizeCompatibleGroup(Truck truck, List<Order> orders) {
        int size = orders.size();
        int subsetCount = 1 << size;
        long[] orderWeights = new long[size];
        long[] orderVolumes = new long[size];
        long[] orderPayouts = new long[size];
        long[] orderPickupDays = new long[size];
        long[] orderDeliveryDays = new long[size];

        for (int index = 0; index < size; index++) {
            Order order = orders.get(index);
            orderWeights[index] = order.getWeightLbs();
            orderVolumes[index] = order.getVolumeCuft();
            orderPayouts[index] = order.getPayoutCents();
            orderPickupDays[index] = order.getPickupDate().toEpochDay();
            orderDeliveryDays[index] = order.getDeliveryDate().toEpochDay();
        }

        long[] weights = new long[subsetCount];
        long[] volumes = new long[subsetCount];
        long[] payouts = new long[subsetCount];
        long[] latestPickups = new long[subsetCount];
        long[] earliestDeliveries = new long[subsetCount];
        int bestMask = 0;
        long bestPayout = 0;
        long bestWeight = 0;
        long bestVolume = 0;

        for (int mask = 1; mask < subsetCount; mask++) {
            int leastSignificantBit = mask & -mask;
            int orderIndex = Integer.numberOfTrailingZeros(leastSignificantBit);
            int previousMask = mask ^ leastSignificantBit;

            long latestPickup = latestPickup(previousMask, latestPickups, orderPickupDays[orderIndex]);
            long earliestDelivery = earliestDelivery(previousMask, earliestDeliveries, orderDeliveryDays[orderIndex]);
            if (latestPickup > earliestDelivery) {
                latestPickups[mask] = latestPickup;
                earliestDeliveries[mask] = earliestDelivery;
                continue;
            }

            long weight = weights[previousMask] + orderWeights[orderIndex];
            if (weight > truck.getMaxWeightLbs()) {
                weights[mask] = weight;
                latestPickups[mask] = latestPickup;
                earliestDeliveries[mask] = earliestDelivery;
                continue;
            }

            long volume = volumes[previousMask] + orderVolumes[orderIndex];
            if (volume > truck.getMaxVolumeCuft()) {
                weights[mask] = weight;
                volumes[mask] = volume;
                latestPickups[mask] = latestPickup;
                earliestDeliveries[mask] = earliestDelivery;
                continue;
            }

            long payout = payouts[previousMask] + orderPayouts[orderIndex];
            weights[mask] = weight;
            volumes[mask] = volume;
            payouts[mask] = payout;
            latestPickups[mask] = latestPickup;
            earliestDeliveries[mask] = earliestDelivery;

            if (isBetter(payout, weight, volume, bestPayout, bestWeight, bestVolume)) {
                bestMask = mask;
                bestPayout = payout;
                bestWeight = weight;
                bestVolume = volume;
            }
        }

        return new Result(bestMask, bestPayout, bestWeight, bestVolume, orders);
    }

    private long latestPickup(int previousMask, long[] latestPickups, long pickupEpochDay) {
        return previousMask == 0 ? pickupEpochDay : Math.max(latestPickups[previousMask], pickupEpochDay);
    }

    private long earliestDelivery(int previousMask, long[] earliestDeliveries, long deliveryEpochDay) {
        return previousMask == 0 ? deliveryEpochDay : Math.min(earliestDeliveries[previousMask], deliveryEpochDay);
    }

    private boolean isBetter(Result candidate, Result currentBest) {
        return isBetter(
                candidate.payout(),
                candidate.weight(),
                candidate.volume(),
                currentBest.payout(),
                currentBest.weight(),
                currentBest.volume());
    }

    private boolean isBetter(
            long candidatePayout,
            long candidateWeight,
            long candidateVolume,
            long currentBestPayout,
            long currentBestWeight,
            long currentBestVolume) {
        if (candidatePayout != currentBestPayout) {
            return candidatePayout > currentBestPayout;
        }
        if (candidateWeight != currentBestWeight) {
            return candidateWeight > currentBestWeight;
        }
        return candidateVolume > currentBestVolume;
    }

    private OptimizeResponse emptyResponse(Truck truck) {
        return OptimizeResponse.builder()
                .truckId(truck.getId())
                .selectedOrderIds(List.of())
                .totalPayoutCents(0)
                .totalWeightLbs(0)
                .totalVolumeCuft(0)
                .utilizationWeightPercent(0.0)
                .utilizationVolumePercent(0.0)
                .build();
    }

    private OptimizeResponse toResponse(Truck truck, Result result) {
        return OptimizeResponse.builder()
                .truckId(truck.getId())
                .selectedOrderIds(result.orderIds())
                .totalPayoutCents(result.payout())
                .totalWeightLbs(result.weight())
                .totalVolumeCuft(result.volume())
                .utilizationWeightPercent(percent(result.weight(), truck.getMaxWeightLbs()))
                .utilizationVolumePercent(percent(result.volume(), truck.getMaxVolumeCuft()))
                .build();
    }

    private double percent(long used, long capacity) {
        if (capacity <= 0) {
            return 0.0;
        }
        return BigDecimal.valueOf(used)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(capacity), 2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private record GroupKey(String origin, String destination, boolean hazmat) {
    }

    private record Result(int mask, long payout, long weight, long volume, List<Order> sourceOrders) {

        static Result empty() {
            return new Result(0, 0, 0, 0, List.of());
        }

        List<String> orderIds() {
            if (mask == 0) {
                return List.of();
            }

            List<String> ids = new ArrayList<>();
            for (int index = 0; index < sourceOrders.size(); index++) {
                if ((mask & (1 << index)) != 0) {
                    ids.add(sourceOrders.get(index).getId());
                }
            }
            return ids;
        }
    }
}
