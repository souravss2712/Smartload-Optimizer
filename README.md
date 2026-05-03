# Teleport Assessment - SmartLoad Optimization API

SmartLoad is a Spring Boot 3 microservice that selects the most profitable compatible set of shipment orders a truck can carry while respecting weight, volume, route, hazmat, and date-validity constraints.

## Tech Stack

- Java 17
- Spring Boot 3
- Maven
- Lombok
- Jakarta Validation
- Spring Boot Actuator

## Project Structure

```text
com.smartload
|-- controller
|   `-- LoadOptimizerController.java
|-- service
|   |-- LoadOptimizerService.java
|   `-- impl
|       `-- LoadOptimizerServiceImpl.java
|-- optimizer
|   `-- LoadOptimizer.java
|-- model
|   |-- Truck.java
|   |-- Order.java
|   |-- OptimizeRequest.java
|   `-- OptimizeResponse.java
|-- exception
|   |-- GlobalExceptionHandler.java
|   `-- TooManyOrdersException.java
`-- SmartLoadApplication.java
```

## Run

```bash
docker compose up --build
```

The API runs on `http://localhost:8080`.

Health check:

```bash
curl http://localhost:8080/actuator/health
```

## Optimize Loads

```bash
curl -X POST http://localhost:8080/api/v1/load-optimizer/optimize \
  -H "Content-Type: application/json" \
  -d '{
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
  }'
```

Example response:

```json
{
  "truck_id": "truck-123",
  "selected_order_ids": ["ord-001", "ord-002"],
  "total_payout_cents": 430000,
  "total_weight_lbs": 30000,
  "total_volume_cuft": 2100,
  "utilization_weight_percent": 68.18,
  "utilization_volume_percent": 70.0
}
```

## Algorithm

The optimizer pre-groups orders by route and hazmat status, then runs bitmask dynamic programming for each compatible group. It enumerates subsets with bit operations, reuses totals from previous masks, prunes overweight or over-volume subsets early, verifies that selected orders share a feasible pickup/delivery window, and keeps the highest-payout valid result.

Route values are normalized for compatibility checks by trimming whitespace, collapsing repeated whitespace, and comparing case-insensitively.

Maximum supported orders per request: `22`. Requests above that limit return `413 Payload Too Large`.

## Test

```bash
mvn test
```
