# SmartLoad Optimization API

## How to run

```bash
git clone <your-repo>
cd <folder>
docker compose up --build
```

The service listens on port **8080** inside the container and is mapped to the host as **http://localhost:8080**.

Prerequisites: [Docker Desktop](https://docs.docker.com/desktop/install/windows-install/) (Windows) or Docker Engine with Compose v2 (`docker compose`).

### Windows: `'docker' is not recognized`

That means Docker is **not installed** or **not on your PATH**. Do this:

1. Install **Docker Desktop for Windows** from [Install Docker Desktop on Windows](https://docs.docker.com/desktop/install/windows-install/).
2. Start **Docker Desktop** from the Start menu and wait until it says **Docker is running**.
3. **Close and reopen** your terminal (or Cursor integrated terminal), then check:

   ```bat
   docker --version
   docker compose version
   ```

4. If those work, from your project folder run:

   ```bat
   docker compose up --build
   ```

If Docker is installed but the command still fails, add Docker’s CLI to PATH (typical install location: `C:\Program Files\Docker\Docker\resources\bin`) or reinstall Docker Desktop and enable **“Add shortcut to desktop”** / **Use WSL 2** when the installer offers it.

## Health check

```bash
curl http://localhost:8080/actuator/health
```

## Example request

From the repository root (after the service is up):

```bash
curl -X POST http://localhost:8080/api/v1/load-optimizer/optimize \
  -H "Content-Type: application/json" \
  -d @sample-request.json
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

---

## Repository layout (assessment)

| Item | Location |
|------|----------|
| Source code | `src/` |
| Dockerfile (multi-stage) | `Dockerfile` |
| Compose (service only, no database) | `docker-compose.yml` |
| Sample payload | `sample-request.json` |

## Local development (without Docker)

Requires Java 17 and Maven:

```bash
mvn test
mvn spring-boot:run
```

## API notes

- **POST** `/api/v1/load-optimizer/optimize` — JSON body with `truck`, `orders`, and optional `preferences`.
- Maximum **22** orders per request; larger payloads return **413 Payload Too Large**.
- Money fields use integer **cents** only (`total_payout_cents`, `payout_cents`).
- Optional `preferences` supports algorithm selection (`bitmask_dp`, `backtracking`), weighted objectives, and `include_pareto_optimal_solutions` (Pareto output is capped at **20** orders; optimization still supports up to **22**).
