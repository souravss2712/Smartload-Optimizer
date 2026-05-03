package com.smartload.optimizer;

import com.smartload.model.LoadSolution;
import com.smartload.model.OptimizationPreferences;
import com.smartload.model.OptimizeResponse;
import com.smartload.model.Order;
import com.smartload.model.Truck;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class LoadOptimizer {

    private static final double EPSILON = 0.000000001;

    public OptimizeResponse optimize(Truck truck, List<Order> orders) {
        return optimize(truck, orders, new OptimizationPreferences());
    }

    public OptimizeResponse optimize(Truck truck, List<Order> orders, OptimizationPreferences preferences) {
        OptimizationPreferences resolvedPreferences = preferences == null ? new OptimizationPreferences() : preferences;
        if (orders == null || orders.isEmpty()) {
            return emptyResponse(truck);
        }

        List<Result> paretoFrontier = resolvedPreferences.includeParetoOptimalSolutionsOrDefault()
                ? new ArrayList<>()
                : null;
        Objective objective = Objective.from(truck, orders, resolvedPreferences);
        Result best = Result.empty();

        for (List<Order> compatibleOrders : groupByRouteAndHazmat(orders).values()) {
            Result candidate = switch (resolvedPreferences.algorithmOrDefault()) {
                case BITMASK_DP -> optimizeCompatibleGroupWithBitmaskDp(
                        truck,
                        compatibleOrders,
                        objective,
                        paretoFrontier);
                case BACKTRACKING -> optimizeCompatibleGroupWithBacktracking(
                        truck,
                        compatibleOrders,
                        objective,
                        paretoFrontier);
            };

            if (isBetter(candidate, best)) {
                best = candidate;
            }
        }

        List<LoadSolution> paretoSolutions = paretoFrontier == null
                ? List.of()
                : paretoFrontier.stream()
                        .sorted((left, right) -> comparePareto(right, left, truck))
                        .map(result -> toLoadSolution(truck, result))
                        .toList();

        return toResponse(truck, best, paretoSolutions);
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

    private Result optimizeCompatibleGroupWithBitmaskDp(
            Truck truck,
            List<Order> orders,
            Objective objective,
            List<Result> paretoFrontier) {
        PreparedOrders prepared = PreparedOrders.from(orders);
        int subsetCount = 1 << prepared.size();

        long[] weights = new long[subsetCount];
        long[] volumes = new long[subsetCount];
        long[] payouts = new long[subsetCount];
        long[] latestPickups = new long[subsetCount];
        long[] earliestDeliveries = new long[subsetCount];
        int bestMask = 0;
        long bestPayout = 0;
        long bestWeight = 0;
        long bestVolume = 0;
        double bestScore = objective.score(0, 0, 0);

        for (int mask = 1; mask < subsetCount; mask++) {
            int leastSignificantBit = mask & -mask;
            int orderIndex = Integer.numberOfTrailingZeros(leastSignificantBit);
            int previousMask = mask ^ leastSignificantBit;

            long latestPickup = latestPickup(previousMask, latestPickups, prepared.pickupDays()[orderIndex]);
            long earliestDelivery = earliestDelivery(previousMask, earliestDeliveries, prepared.deliveryDays()[orderIndex]);
            if (latestPickup > earliestDelivery) {
                latestPickups[mask] = latestPickup;
                earliestDeliveries[mask] = earliestDelivery;
                continue;
            }

            long weight = safeAdd(weights[previousMask], prepared.weights()[orderIndex], "weight_lbs");
            if (weight > truck.getMaxWeightLbs()) {
                weights[mask] = weight;
                latestPickups[mask] = latestPickup;
                earliestDeliveries[mask] = earliestDelivery;
                continue;
            }

            long volume = safeAdd(volumes[previousMask], prepared.volumes()[orderIndex], "volume_cuft");
            if (volume > truck.getMaxVolumeCuft()) {
                weights[mask] = weight;
                volumes[mask] = volume;
                latestPickups[mask] = latestPickup;
                earliestDeliveries[mask] = earliestDelivery;
                continue;
            }

            long payout = safeAdd(payouts[previousMask], prepared.payouts()[orderIndex], "payout_cents");
            weights[mask] = weight;
            volumes[mask] = volume;
            payouts[mask] = payout;
            latestPickups[mask] = latestPickup;
            earliestDeliveries[mask] = earliestDelivery;

            double score = objective.score(payout, weight, volume);
            if (isBetter(score, payout, weight, volume, bestScore, bestPayout, bestWeight, bestVolume)) {
                bestMask = mask;
                bestPayout = payout;
                bestWeight = weight;
                bestVolume = volume;
                bestScore = score;
            }

            if (paretoFrontier != null) {
                addParetoCandidate(
                        new Result(mask, payout, weight, volume, score, prepared.orders()),
                        paretoFrontier,
                        truck);
            }
        }

        return new Result(bestMask, bestPayout, bestWeight, bestVolume, bestScore, prepared.orders());
    }

    private Result optimizeCompatibleGroupWithBacktracking(
            Truck truck,
            List<Order> orders,
            Objective objective,
            List<Result> paretoFrontier) {
        PreparedOrders prepared = PreparedOrders.from(orders);
        SearchState searchState = new SearchState(Result.empty(), objective.score(0, 0, 0));
        backtrack(
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                false,
                prepared,
                truck,
                objective,
                searchState,
                paretoFrontier);
        return searchState.best();
    }

    private void backtrack(
            int index,
            int mask,
            long payout,
            long weight,
            long volume,
            long latestPickup,
            long earliestDelivery,
            boolean hasOrders,
            PreparedOrders prepared,
            Truck truck,
            Objective objective,
            SearchState searchState,
            List<Result> paretoFrontier) {
        if (hasOrders) {
            double score = objective.score(payout, weight, volume);
            Result candidate = new Result(mask, payout, weight, volume, score, prepared.orders());
            if (isBetter(
                    score,
                    payout,
                    weight,
                    volume,
                    searchState.bestScore(),
                    searchState.best().payout(),
                    searchState.best().weight(),
                    searchState.best().volume())) {
                searchState.update(candidate, score);
            }

            if (paretoFrontier != null) {
                addParetoCandidate(candidate, paretoFrontier, truck);
            }
        }

        if (index == prepared.size()) {
            return;
        }

        long maxPossiblePayout = safeAdd(payout, prepared.remainingPayouts()[index], "payout_cents");
        double upperBoundScore = objective.score(
                maxPossiblePayout,
                truck.getMaxWeightLbs(),
                truck.getMaxVolumeCuft());
        if (upperBoundScore + EPSILON < searchState.bestScore()) {
            return;
        }

        long includedWeight = safeAdd(weight, prepared.weights()[index], "weight_lbs");
        long includedVolume = safeAdd(volume, prepared.volumes()[index], "volume_cuft");
        long includedLatestPickup = hasOrders
                ? Math.max(latestPickup, prepared.pickupDays()[index])
                : prepared.pickupDays()[index];
        long includedEarliestDelivery = hasOrders
                ? Math.min(earliestDelivery, prepared.deliveryDays()[index])
                : prepared.deliveryDays()[index];

        if (includedWeight <= truck.getMaxWeightLbs()
                && includedVolume <= truck.getMaxVolumeCuft()
                && includedLatestPickup <= includedEarliestDelivery) {
            backtrack(
                    index + 1,
                    mask | (1 << index),
                    safeAdd(payout, prepared.payouts()[index], "payout_cents"),
                    includedWeight,
                    includedVolume,
                    includedLatestPickup,
                    includedEarliestDelivery,
                    true,
                    prepared,
                    truck,
                    objective,
                    searchState,
                    paretoFrontier);
        }

        backtrack(
                index + 1,
                mask,
                payout,
                weight,
                volume,
                latestPickup,
                earliestDelivery,
                hasOrders,
                prepared,
                truck,
                objective,
                searchState,
                paretoFrontier);
    }

    private long latestPickup(int previousMask, long[] latestPickups, long pickupEpochDay) {
        return previousMask == 0 ? pickupEpochDay : Math.max(latestPickups[previousMask], pickupEpochDay);
    }

    private long earliestDelivery(int previousMask, long[] earliestDeliveries, long deliveryEpochDay) {
        return previousMask == 0 ? deliveryEpochDay : Math.min(earliestDeliveries[previousMask], deliveryEpochDay);
    }

    private boolean isBetter(Result candidate, Result currentBest) {
        return isBetter(
                candidate.score(),
                candidate.payout(),
                candidate.weight(),
                candidate.volume(),
                currentBest.score(),
                currentBest.payout(),
                currentBest.weight(),
                currentBest.volume());
    }

    private boolean isBetter(
            double candidateScore,
            long candidatePayout,
            long candidateWeight,
            long candidateVolume,
            double currentBestScore,
            long currentBestPayout,
            long currentBestWeight,
            long currentBestVolume) {
        if (candidateScore > currentBestScore + EPSILON) {
            return true;
        }
        if (candidateScore + EPSILON < currentBestScore) {
            return false;
        }
        if (candidatePayout != currentBestPayout) {
            return candidatePayout > currentBestPayout;
        }
        if (candidateWeight != currentBestWeight) {
            return candidateWeight > currentBestWeight;
        }
        return candidateVolume > currentBestVolume;
    }

    private void addParetoCandidate(Result candidate, List<Result> paretoFrontier, Truck truck) {
        Iterator<Result> iterator = paretoFrontier.iterator();
        while (iterator.hasNext()) {
            Result existing = iterator.next();
            if (dominatesOrEquals(existing, candidate, truck)) {
                return;
            }
            if (dominates(candidate, existing, truck)) {
                iterator.remove();
            }
        }
        paretoFrontier.add(candidate);
    }

    private boolean dominatesOrEquals(Result left, Result right, Truck truck) {
        double leftUtilization = utilizationScore(left, truck);
        double rightUtilization = utilizationScore(right, truck);
        return dominates(left, right, truck)
                || (left.payout() == right.payout() && Math.abs(leftUtilization - rightUtilization) <= EPSILON);
    }

    private boolean dominates(Result left, Result right, Truck truck) {
        double leftUtilization = utilizationScore(left, truck);
        double rightUtilization = utilizationScore(right, truck);
        return left.payout() >= right.payout()
                && leftUtilization + EPSILON >= rightUtilization
                && (left.payout() > right.payout() || leftUtilization > rightUtilization + EPSILON);
    }

    private int comparePareto(Result left, Result right, Truck truck) {
        int payoutCompare = Long.compare(left.payout(), right.payout());
        if (payoutCompare != 0) {
            return payoutCompare;
        }
        return Double.compare(utilizationScore(left, truck), utilizationScore(right, truck));
    }

    private double utilizationScore(Result result, Truck truck) {
        double weightUtilization = (double) result.weight() / truck.getMaxWeightLbs();
        double volumeUtilization = (double) result.volume() / truck.getMaxVolumeCuft();
        return (weightUtilization + volumeUtilization) / 2.0;
    }

    private static long safeAdd(long left, long right, String fieldName) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException exception) {
            throw new ArithmeticException("Numeric overflow while adding " + fieldName);
        }
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

    private OptimizeResponse toResponse(Truck truck, Result result, List<LoadSolution> paretoSolutions) {
        return OptimizeResponse.builder()
                .truckId(truck.getId())
                .selectedOrderIds(result.orderIds())
                .totalPayoutCents(result.payout())
                .totalWeightLbs(result.weight())
                .totalVolumeCuft(result.volume())
                .utilizationWeightPercent(percent(result.weight(), truck.getMaxWeightLbs()))
                .utilizationVolumePercent(percent(result.volume(), truck.getMaxVolumeCuft()))
                .paretoOptimalSolutions(paretoSolutions)
                .build();
    }

    private LoadSolution toLoadSolution(Truck truck, Result result) {
        return LoadSolution.builder()
                .selectedOrderIds(result.orderIds())
                .totalPayoutCents(result.payout())
                .totalWeightLbs(result.weight())
                .totalVolumeCuft(result.volume())
                .utilizationWeightPercent(percent(result.weight(), truck.getMaxWeightLbs()))
                .utilizationVolumePercent(percent(result.volume(), truck.getMaxVolumeCuft()))
                .utilizationScorePercent((percent(result.weight(), truck.getMaxWeightLbs())
                        + percent(result.volume(), truck.getMaxVolumeCuft())) / 2.0)
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

    private record PreparedOrders(
            List<Order> orders,
            long[] weights,
            long[] volumes,
            long[] payouts,
            long[] pickupDays,
            long[] deliveryDays,
            long[] remainingPayouts) {

        static PreparedOrders from(List<Order> orders) {
            int size = orders.size();
            long[] weights = new long[size];
            long[] volumes = new long[size];
            long[] payouts = new long[size];
            long[] pickupDays = new long[size];
            long[] deliveryDays = new long[size];
            long[] remainingPayouts = new long[size + 1];

            for (int index = 0; index < size; index++) {
                Order order = orders.get(index);
                weights[index] = order.getWeightLbs();
                volumes[index] = order.getVolumeCuft();
                payouts[index] = order.getPayoutCents();
                pickupDays[index] = order.getPickupDate().toEpochDay();
                deliveryDays[index] = order.getDeliveryDate().toEpochDay();
            }

            for (int index = size - 1; index >= 0; index--) {
                remainingPayouts[index] = safeAdd(remainingPayouts[index + 1], payouts[index], "payout_cents");
            }

            return new PreparedOrders(orders, weights, volumes, payouts, pickupDays, deliveryDays, remainingPayouts);
        }

        int size() {
            return orders.size();
        }
    }

    private record Objective(
            double revenueWeight,
            double weightUtilizationWeight,
            double volumeUtilizationWeight,
            long totalAvailablePayout,
            long maxWeight,
            long maxVolume) {

        static Objective from(Truck truck, List<Order> orders, OptimizationPreferences preferences) {
            long totalAvailablePayout = 0;
            for (Order order : orders) {
                totalAvailablePayout = safeAdd(totalAvailablePayout, order.getPayoutCents(), "payout_cents");
            }

            return new Objective(
                    preferences.revenueWeightOrDefault(),
                    preferences.weightUtilizationWeightOrDefault(),
                    preferences.volumeUtilizationWeightOrDefault(),
                    totalAvailablePayout,
                    truck.getMaxWeightLbs(),
                    truck.getMaxVolumeCuft());
        }

        double score(long payout, long weight, long volume) {
            if (weightUtilizationWeight == 0.0 && volumeUtilizationWeight == 0.0) {
                return revenueWeight * payout;
            }

            double revenueScore = totalAvailablePayout == 0 ? 0.0 : (double) payout / totalAvailablePayout;
            double weightScore = (double) weight / maxWeight;
            double volumeScore = (double) volume / maxVolume;
            return (revenueWeight * revenueScore)
                    + (weightUtilizationWeight * weightScore)
                    + (volumeUtilizationWeight * volumeScore);
        }
    }

    private static class SearchState {

        private Result best;
        private double bestScore;

        SearchState(Result best, double bestScore) {
            this.best = best;
            this.bestScore = bestScore;
        }

        Result best() {
            return best;
        }

        double bestScore() {
            return bestScore;
        }

        void update(Result best, double bestScore) {
            this.best = best;
            this.bestScore = bestScore;
        }
    }

    private record Result(int mask, long payout, long weight, long volume, double score, List<Order> sourceOrders) {

        static Result empty() {
            return new Result(0, 0, 0, 0, 0.0, List.of());
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
