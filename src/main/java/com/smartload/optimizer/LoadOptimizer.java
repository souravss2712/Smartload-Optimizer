package com.smartload.optimizer;

import com.smartload.model.OptimizeResponse;
import com.smartload.model.Order;
import com.smartload.model.Truck;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
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
                    order.getOrigin(),
                    order.getDestination(),
                    Boolean.TRUE.equals(order.getHazmat()));
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(order);
        }
        return groups;
    }

    private Result optimizeCompatibleGroup(Truck truck, List<Order> orders) {
        int size = orders.size();
        int subsetCount = 1 << size;

        long[] weights = new long[subsetCount];
        long[] volumes = new long[subsetCount];
        long[] payouts = new long[subsetCount];
        long[] latestPickups = new long[subsetCount];
        long[] earliestDeliveries = new long[subsetCount];
        Result best = Result.empty();

        for (int mask = 1; mask < subsetCount; mask++) {
            int leastSignificantBit = mask & -mask;
            int orderIndex = Integer.numberOfTrailingZeros(leastSignificantBit);
            int previousMask = mask ^ leastSignificantBit;
            Order order = orders.get(orderIndex);

            long latestPickup = latestPickup(previousMask, latestPickups, order.getPickupDate());
            long earliestDelivery = earliestDelivery(previousMask, earliestDeliveries, order.getDeliveryDate());
            if (latestPickup > earliestDelivery) {
                latestPickups[mask] = latestPickup;
                earliestDeliveries[mask] = earliestDelivery;
                continue;
            }

            long weight = weights[previousMask] + order.getWeightLbs();
            if (weight > truck.getMaxWeightLbs()) {
                weights[mask] = weight;
                latestPickups[mask] = latestPickup;
                earliestDeliveries[mask] = earliestDelivery;
                continue;
            }

            long volume = volumes[previousMask] + order.getVolumeCuft();
            if (volume > truck.getMaxVolumeCuft()) {
                weights[mask] = weight;
                volumes[mask] = volume;
                latestPickups[mask] = latestPickup;
                earliestDeliveries[mask] = earliestDelivery;
                continue;
            }

            long payout = payouts[previousMask] + order.getPayoutCents();
            weights[mask] = weight;
            volumes[mask] = volume;
            payouts[mask] = payout;
            latestPickups[mask] = latestPickup;
            earliestDeliveries[mask] = earliestDelivery;

            Result candidate = new Result(mask, payout, weight, volume, orders);
            if (isBetter(candidate, best)) {
                best = candidate;
            }
        }

        return best;
    }

    private long latestPickup(int previousMask, long[] latestPickups, LocalDate pickupDate) {
        long pickupEpochDay = pickupDate.toEpochDay();
        return previousMask == 0 ? pickupEpochDay : Math.max(latestPickups[previousMask], pickupEpochDay);
    }

    private long earliestDelivery(int previousMask, long[] earliestDeliveries, LocalDate deliveryDate) {
        long deliveryEpochDay = deliveryDate.toEpochDay();
        return previousMask == 0 ? deliveryEpochDay : Math.min(earliestDeliveries[previousMask], deliveryEpochDay);
    }

    private boolean isBetter(Result candidate, Result currentBest) {
        return Comparator
                .comparingLong(Result::payout)
                .thenComparingLong(Result::weight)
                .thenComparingLong(Result::volume)
                .compare(candidate, currentBest) > 0;
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
