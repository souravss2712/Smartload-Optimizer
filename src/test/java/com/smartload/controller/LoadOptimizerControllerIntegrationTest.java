package com.smartload.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LoadOptimizerControllerIntegrationTest {

    private static final String OPTIMIZE_PATH = "/api/v1/load-optimizer/optimize";

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void acceptsSnakeCaseJsonAndReturnsSnakeCaseResponse() {
        ResponseEntity<String> response = post(sampleRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("\"truck_id\":\"truck-123\"")
                .contains("\"selected_order_ids\"")
                .contains("\"total_payout_cents\":430000")
                .contains("\"utilization_weight_percent\":68.18")
                .doesNotContain("truckId")
                .doesNotContain("selectedOrderIds");
    }

    @Test
    void returnsBadRequestForInvalidInput() {
        String request = """
                {
                  "truck": {
                    "id": "truck-123",
                    "max_weight_lbs": 44000,
                    "max_volume_cuft": 3000
                  },
                  "orders": [
                    {
                      "id": "ord-001",
                      "weight_lbs": 18000,
                      "volume_cuft": 1200,
                      "origin": "Los Angeles, CA",
                      "destination": "Dallas, TX",
                      "pickup_date": "2025-12-05",
                      "delivery_date": "2025-12-09",
                      "is_hazmat": false
                    }
                  ]
                }
                """;

        ResponseEntity<String> response = post(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("order.payout_cents is required");
    }

    @Test
    void returnsPayloadTooLargeBeforeNestedValidationForTooManyOrders() {
        String emptyOrders = IntStream.range(0, 23)
                .mapToObj(ignored -> "{}")
                .collect(Collectors.joining(","));
        String request = """
                {
                  "truck": {
                    "id": "truck-123",
                    "max_weight_lbs": 44000,
                    "max_volume_cuft": 3000
                  },
                  "orders": [%s]
                }
                """.formatted(emptyOrders);

        ResponseEntity<String> response = post(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody()).contains("orders cannot contain more than 22 items");
    }

    @Test
    void exposesActuatorHealthEndpoint() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    private ResponseEntity<String> post(String requestBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(
                OPTIMIZE_PATH,
                HttpMethod.POST,
                new HttpEntity<>(requestBody, headers),
                String.class);
    }

    private String sampleRequest() {
        return """
                {
                  "truck": {
                    "id": "truck-123",
                    "max_weight_lbs": 44000,
                    "max_volume_cuft": 3000
                  },
                  "orders": [
                    {
                      "id": "ord-001",
                      "payout_cents": 250000,
                      "weight_lbs": 18000,
                      "volume_cuft": 1200,
                      "origin": "Los Angeles, CA",
                      "destination": "Dallas, TX",
                      "pickup_date": "2025-12-05",
                      "delivery_date": "2025-12-09",
                      "is_hazmat": false
                    },
                    {
                      "id": "ord-002",
                      "payout_cents": 180000,
                      "weight_lbs": 12000,
                      "volume_cuft": 900,
                      "origin": "Los Angeles, CA",
                      "destination": "Dallas, TX",
                      "pickup_date": "2025-12-04",
                      "delivery_date": "2025-12-10",
                      "is_hazmat": false
                    }
                  ]
                }
                """;
    }
}
