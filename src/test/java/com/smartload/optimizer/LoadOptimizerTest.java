package com.smartload.optimizer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeout;

import com.smartload.model.OptimizeResponse;
import com.smartload.model.Order;
import com.smartload.model.Truck;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LoadOptimizerTest {

    private final LoadOptimizer optimizer = new LoadOptimizer();

    @Test
    void selectsMostProfitableValidCombination() {
        Truck truck = truck(44_000, 3_000);
        List<Order> orders = List.of(
                order("ord-001", 250_000, 18_000, 1_200, "Los Angeles, CA", "Dallas, TX", false),
                order("ord-002", 180_000, 12_000, 900, "Los Angeles, CA", "Dallas, TX", false),
                order("ord-003", 400_000, 35_000, 2_700, "Los Angeles, CA", "Dallas, TX", false));

        OptimizeResponse response = optimizer.optimize(truck, orders);

        assertThat(response.getSelectedOrderIds()).containsExactly("ord-001", "ord-002");
        assertThat(response.getTotalPayoutCents()).isEqualTo(430_000);
        assertThat(response.getTotalWeightLbs()).isEqualTo(30_000);
        assertThat(response.getTotalVolumeCuft()).isEqualTo(2_100);
        assertThat(response.getUtilizationWeightPercent()).isEqualTo(68.18);
        assertThat(response.getUtilizationVolumePercent()).isEqualTo(70.0);
    }

    @Test
    void skipsSubsetsThatOverflowCapacity() {
        Truck truck = truck(20_000, 2_000);
        List<Order> orders = List.of(
                order("ord-001", 200_000, 18_000, 1_200, "Los Angeles, CA", "Dallas, TX", false),
                order("ord-002", 300_000, 15_000, 1_100, "Los Angeles, CA", "Dallas, TX", false));

        OptimizeResponse response = optimizer.optimize(truck, orders);

        assertThat(response.getSelectedOrderIds()).containsExactly("ord-002");
        assertThat(response.getTotalPayoutCents()).isEqualTo(300_000);
        assertThat(response.getTotalWeightLbs()).isEqualTo(15_000);
        assertThat(response.getTotalVolumeCuft()).isEqualTo(1_100);
    }

    @Test
    void enforcesHazmatCompatibility() {
        Truck truck = truck(44_000, 3_000);
        List<Order> orders = List.of(
                order("non-hazmat", 250_000, 10_000, 700, "Los Angeles, CA", "Dallas, TX", false),
                order("hazmat-1", 180_000, 8_000, 500, "Los Angeles, CA", "Dallas, TX", true),
                order("hazmat-2", 190_000, 8_000, 500, "Los Angeles, CA", "Dallas, TX", true));

        OptimizeResponse response = optimizer.optimize(truck, orders);

        assertThat(response.getSelectedOrderIds()).containsExactly("hazmat-1", "hazmat-2");
        assertThat(response.getTotalPayoutCents()).isEqualTo(370_000);
    }

    @Test
    void enforcesRouteCompatibility() {
        Truck truck = truck(44_000, 3_000);
        List<Order> orders = List.of(
                order("la-dallas", 250_000, 10_000, 700, "Los Angeles, CA", "Dallas, TX", false),
                order("la-dallas-2", 260_000, 10_000, 700, "Los Angeles, CA", "Dallas, TX", false),
                order("sf-denver", 700_000, 12_000, 800, "San Francisco, CA", "Denver, CO", false));

        OptimizeResponse response = optimizer.optimize(truck, orders);

        assertThat(response.getSelectedOrderIds()).containsExactly("sf-denver");
        assertThat(response.getTotalPayoutCents()).isEqualTo(700_000);
    }

    @Test
    void normalizesRouteValuesForCompatibility() {
        Truck truck = truck(44_000, 3_000);
        List<Order> orders = List.of(
                order("ord-001", 250_000, 10_000, 700, " Los   Angeles, CA ", "Dallas, TX", false),
                order("ord-002", 260_000, 10_000, 700, "los angeles, ca", " dallas, tx ", false));

        OptimizeResponse response = optimizer.optimize(truck, orders);

        assertThat(response.getSelectedOrderIds()).containsExactly("ord-001", "ord-002");
        assertThat(response.getTotalPayoutCents()).isEqualTo(510_000);
    }

    @Test
    void enforcesCommonTimeWindowCompatibility() {
        Truck truck = truck(44_000, 3_000);
        List<Order> orders = List.of(
                orderWithDates(
                        "early-window",
                        250_000,
                        10_000,
                        700,
                        "Los Angeles, CA",
                        "Dallas, TX",
                        false,
                        LocalDate.of(2025, 12, 1),
                        LocalDate.of(2025, 12, 3)),
                orderWithDates(
                        "late-window",
                        260_000,
                        10_000,
                        700,
                        "Los Angeles, CA",
                        "Dallas, TX",
                        false,
                        LocalDate.of(2025, 12, 5),
                        LocalDate.of(2025, 12, 7)));

        OptimizeResponse response = optimizer.optimize(truck, orders);

        assertThat(response.getSelectedOrderIds()).containsExactly("late-window");
        assertThat(response.getTotalPayoutCents()).isEqualTo(260_000);
    }

    @Test
    void returnsEmptySelectionWhenNoOrderFits() {
        Truck truck = truck(10_000, 500);
        List<Order> orders = List.of(
                order("too-heavy", 250_000, 11_000, 400, "Los Angeles, CA", "Dallas, TX", false),
                order("too-large", 260_000, 5_000, 600, "Los Angeles, CA", "Dallas, TX", false));

        OptimizeResponse response = optimizer.optimize(truck, orders);

        assertThat(response.getSelectedOrderIds()).isEmpty();
        assertThat(response.getTotalPayoutCents()).isZero();
        assertThat(response.getTotalWeightLbs()).isZero();
        assertThat(response.getTotalVolumeCuft()).isZero();
    }

    @Test
    void optimizesTwentyTwoSameLaneOrdersWithinPerformanceBudget() {
        Truck truck = truck(44_000, 3_000);
        List<Order> orders = new ArrayList<>();
        for (int index = 1; index <= 22; index++) {
            orders.add(order(
                    "ord-%03d".formatted(index),
                    10_000 + index,
                    1_000,
                    100,
                    "Los Angeles, CA",
                    "Dallas, TX",
                    false));
        }

        assertTimeout(Duration.ofSeconds(2), () -> {
            OptimizeResponse response = optimizer.optimize(truck, orders);

            assertThat(response.getSelectedOrderIds()).hasSize(22);
            assertThat(response.getTotalWeightLbs()).isEqualTo(22_000);
            assertThat(response.getTotalVolumeCuft()).isEqualTo(2_200);
        });
    }

    private Truck truck(long maxWeightLbs, long maxVolumeCuft) {
        return Truck.builder()
                .id("truck-123")
                .maxWeightLbs(maxWeightLbs)
                .maxVolumeCuft(maxVolumeCuft)
                .build();
    }

    private Order order(
            String id,
            long payoutCents,
            long weightLbs,
            long volumeCuft,
            String origin,
            String destination,
            boolean hazmat) {
        return orderWithDates(
                id,
                payoutCents,
                weightLbs,
                volumeCuft,
                origin,
                destination,
                hazmat,
                LocalDate.of(2025, 12, 5),
                LocalDate.of(2025, 12, 9));
    }

    private Order orderWithDates(
            String id,
            long payoutCents,
            long weightLbs,
            long volumeCuft,
            String origin,
            String destination,
            boolean hazmat,
            LocalDate pickupDate,
            LocalDate deliveryDate) {
        return Order.builder()
                .id(id)
                .payoutCents(payoutCents)
                .weightLbs(weightLbs)
                .volumeCuft(volumeCuft)
                .origin(origin)
                .destination(destination)
                .pickupDate(pickupDate)
                .deliveryDate(deliveryDate)
                .hazmat(hazmat)
                .build();
    }
}
