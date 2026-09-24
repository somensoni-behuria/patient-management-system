# Patient Management System — Microservices Build Plan

## Context

Build a **production-ready Patient Management System** as a Java Spring Boot microservices
project, together with full **HLD** and **LLD** design documents.

The committed repo and docs must be self-contained — **no reference to any external video,
tutorial, or third-party repo** appears in the README, HLD, LLD, or any Markdown in the repo.

Decisions confirmed with the user:
- **Scope:** Full faithful replica that *actually runs* — all 6 services + gRPC + Kafka +
  API gateway + AWS infrastructure-as-code + integration tests.
- **Docs:** HLD and LLD as Markdown (with Mermaid diagrams) committed in the repo.
- **Stack fidelity:** Modernized — latest Java 21 + Spring Boot 3.5.x and current gRPC/Kafka
  libraries, keeping the video's architecture but using current conventions.

The working directory is a personal workspace (finance/notes files); the project will live in
its own subfolder `patient-management-system/` and be initialized as a fresh git repo. Nothing
existing is touched.

## Target Architecture

```
                       ┌─────────────┐
      client  ───▶     │ API Gateway │  (Spring Cloud Gateway, JWT validation)
                       └──────┬──────┘
        ┌──────────────┬──────┴───────┬───────────────┐
        │ /auth/**     │ /api/        │ /api/         │
        ▼              │ patients/**  │ analytics/**  │
 ┌────────────┐        ▼ (JWT)        ▼ (JWT, read)   │
 │Auth Service│ ┌────────────┐  ┌────────────┐        │
 │ +Postgres  │ │Patient Svc │  │Analytics   │◀───────┤ Kafka
 └────────────┘ │ +Postgres  │  │ +Postgres  │        │ consume
                └─────┬──────┘  └────────────┘        │
       gRPC (9005)    │      Kafka produce            │
     ┌────────────────┤──────────────────┐            │
     ▼                │                   ▼            │
 ┌────────────┐       │             ┌──────────┐       │
 │Billing Svc │       │             │  Kafka   │───────┤
 │ (gRPC only)│       │             └────┬─────┘       │
 │  INTERNAL  │       │                  ▼             │
 └────────────┘       │           ┌──────────────┐    │
                      │           │Notification  │    │
                      │           │(Kafka con,   │────┘
                      │           │  INTERNAL)   │
                      │           └──────────────┘
                      └── billing & notification: no gateway route (internal only)
```

- **Client → gateway only.** The gateway exposes three client-facing route groups:
  `/auth/**` → auth-service, `/api/patients/**` → patient-service (JWT-protected), and
  **`/api/analytics/**` → analytics-service (JWT-protected, read-only)**, plus aggregated
  `/api-docs/**` for Swagger.
- **Analytics is both a consumer and a query service.** It consumes patient events from Kafka
  into its own Postgres store and exposes **read-only** REST endpoints (aggregates/metrics) that
  the client reaches through the gateway. *This extends the base reference (where analytics was
  consumer-only); added at user request and called out explicitly in the LLD.*
- **Billing is not client-facing.** No gateway route to it; only **patient-service** calls it
  over **gRPC (9005)** to create a billing account when a patient is created.
- **Notification stays internal** — fire-and-forget Kafka consumer, no gateway route.
- **Auth:** auth-service issues JWTs; the gateway's JWT filter validates them on the protected
  routes before forwarding.

### Gateway routes (authoritative)

| Path predicate | Target | Filters |
|---|---|---|
| `/auth/**` | `auth-service:4005` | StripPrefix=1 |
| `/api/patients/**` | `patient-service:4000` | StripPrefix=1, **JwtValidation** |
| `/api/analytics/**` | `analytics-service:4002` | StripPrefix=1, **JwtValidation** (read-only) |
| `/api-docs/patients` | `patient-service:4000/v3/api-docs` | RewritePath |
| `/api-docs/auth` | `auth-service:4005/v3/api-docs` | RewritePath |
| `/api-docs/analytics` | `analytics-service:4002/v3/api-docs` | RewritePath |

billing / notification have **no** gateway route (internal-only).

### Modules (Maven multi-module, groupId `com.pm`)

| Module | Port(s) | Purpose |
|---|---|---|
| `auth-service` | HTTP 4005 | Login, JWT issue/validate; PostgreSQL user store (seeded admin). |
| `patient-service` | HTTP 4000 | Patient CRUD; gRPC client → billing; Kafka producer; PostgreSQL. |
| `billing-service` | gRPC 9005 (HTTP 4001 actuator only) | Internal gRPC server; creates billing accounts. Not gateway-routed. |
| `notification-service` | HTTP 4003 | Kafka consumer; sends/loggs notifications. |
| `analytics-service` | HTTP 4002 | Kafka consumer **+ read-only query API** (gateway-routed); ingests patient events into its own Postgres and serves aggregates/metrics. |
| `api-gateway` | HTTP 4004 | Spring Cloud Gateway routing + JWT auth filter. |
| `infrastructure` | — | AWS CDK (Java) stack → LocalStack (VPC, RDS, MSK, ECS/Fargate, ALB). |
| `integration-tests` | — | REST-assured black-box tests against the running stack. |

Modernization choices: Java 21, Spring Boot **3.5.x**, `grpc-spring-boot-starter` (current),
`spring-kafka`, `springdoc-openapi` 2.x (Swagger UI per service), `jjwt` 0.12.x, Protobuf 4.x,
**Resilience4j** (timeouts/retries/circuit breakers), **Micrometer + OpenTelemetry** (metrics/
traces for p99), Spring Boot Actuator (readiness/liveness + graceful shutdown), Testcontainers
for tests, Docker multi-stage builds (JRE 21 base).

## Repository Layout

```
patient-management-system/
├── README.md                     # quickstart, ports, run instructions
├── docker-compose.yml            # postgres x3 (auth/patient/analytics), kafka, all services,
│                                 #   prometheus + grafana, healthchecks
├── observability/                # prometheus.yml + grafana dashboards/provisioning
├── .gitignore  .env.example
├── docs/
│   ├── HLD.md                    # high-level design (Mermaid diagrams)
│   ├── LLD.md                    # low-level design (per-service detail)
│   └── diagrams/                 # exported/embedded Mermaid sources
├── auth-service/          (pom.xml, src, Dockerfile)
├── patient-service/       (+ src/main/proto for gRPC + Kafka event schema)
├── billing-service/       (+ src/main/proto)
├── notification-service/
├── analytics-service/
├── api-gateway/
├── infrastructure/        (CDK app + localstack-deploy.sh)
├── integration-tests/
└── api-requests/                 # .http files for manual testing
```

## Per-Service LLD Summary (what gets built)

- **auth-service:** `User` entity + JPA repo (Postgres), `AuthController` (`POST /login`,
  `GET /validate`), `AuthService`, `JwtUtil` (HS256, jjwt 0.12), Spring Security config,
  seed admin via `data.sql`/`CommandLineRunner`, Swagger.
- **patient-service:** `Patient` entity/DTO/mapper, `PatientController` (CRUD, validation
  groups), `PatientService`, `PatientRepository`, `BillingServiceGrpcClient` (calls billing on
  create), `KafkaProducer` sending Protobuf `PatientEvent`, Flyway or `data.sql` seed, Swagger,
  global exception handler, **Resilience4j** (timeout/retry/circuit-breaker) on the billing gRPC
  call + Actuator health & graceful shutdown.
- **billing-service:** `BillingGrpcService` (`billing.proto`: `CreateBillingAccount`), returns
  account id/status; logs.
- **notification-service:** `KafkaConsumer` on `patient` topic, deserializes Protobuf, sends/
  logs a welcome notification.
- **analytics-service:** `KafkaConsumer` on `patient` topic persisting events to its own
  Postgres (`analytics_event` table); `AnalyticsController` exposing **read-only** aggregates
  (e.g. `GET /api/analytics/summary`, `GET /api/analytics/patients/count`) behind the gateway's
  JWT filter; `AnalyticsService` + repository. *(Extension beyond the base reference.)*
- **api-gateway:** route config (YAML) forwarding `/auth/**`, `/api/patients/**`, etc.;
  `JwtValidationGatewayFilterFactory` calling auth-service `/validate`; aggregated Swagger.
- **shared proto:** `billing.proto` (gRPC) and `patient_event.proto` (Kafka), compiled per
  service via `protobuf-maven-plugin`.

## Documents (docs/)

- **HLD.md:** problem/goals, context & container diagrams (Mermaid C4-ish), service catalog,
  sync vs async communication, data ownership per service, auth flow, deployment topology
  (LocalStack/AWS), tech stack, cross-cutting concerns (config, logging, resilience, Swagger),
  scalability & trade-offs.
- **LLD.md:** per-service package structure, class responsibilities, REST endpoint tables,
  request/response DTOs, DB schemas (ERD), gRPC service/message definitions, Kafka event schema
  & topic, JWT structure & filter sequence, sequence diagrams (create-patient end-to-end,
  login), config/env-var matrix, Docker & CDK resource mapping.

All docs use **Mermaid** diagrams and contain **no reference** to any external video/tutorial/repo.

## Deployment, Zero-Downtime & Performance (HLD section + Mermaid diagrams)

The prior draft specified infra but not the reliability/SLO design. This section is a
first-class part of `HLD.md`.

**Deployment strategy (recommended):** **rolling deployment** on ECS/Fargate per service, one
service at a time, behind the ALB — the default for the CDK stack. **Blue-green** (via a second
target group + ALB listener swap) is documented as the upgrade path for the gateway and
patient-service where instant rollback matters. Both are drawn as Mermaid diagrams; the plan
recommends rolling as the default and blue-green as opt-in.

**Zero-downtime mechanics (designed + documented, and honored in the compose/CDK configs):**
- **Readiness/liveness** via Spring Boot Actuator (`/actuator/health/readiness|liveness`);
  ALB/ECS only routes to `READY` tasks, so old tasks drain before new ones take traffic.
- **Graceful shutdown:** `server.shutdown=graceful` + `spring.lifecycle.timeout-per-shutdown-phase`,
  plus ECS connection draining, so in-flight requests finish.
- **Backward-compatible DB migrations** (Flyway, expand/contract) so old and new versions run
  against the same schema during a rollout.
- **Kafka consumers** use consumer groups → partitions rebalance across rolling instances with
  no lost events; producers are idempotent/retriable.
- **gRPC** client uses deadlines + retry so a billing task restart doesn't fail patient creates.

**p99 latency design (documented as an SLO budget, enforced by resilience config):**
- Target: read paths **p99 < 200 ms**, patient-create (gateway→JWT validate→patient→gRPC billing
  →Kafka publish) **p99 < 400 ms**. A per-hop **latency budget table** in the HLD sums to these.
- **Resilience4j** timeouts, retries, bulkheads and circuit breakers on the gateway JWT call and
  the patient→billing gRPC call keep tail latency bounded under partial failure.
- **Horizontal autoscaling** (ECS target-tracking on CPU / request-count) to hold p99 under load.
- **Observability to measure p99:** Micrometer → Prometheus metrics + OpenTelemetry traces.
  **Prometheus + Grafana containers are wired into docker-compose** (scrape configs + a
  provisioned latency/throughput dashboard) so p99 is measurable locally out of the box.

**Confirmed choices:** default deployment strategy is **rolling** (blue-green documented as the
opt-in alternative); observability ships as **Prometheus + Grafana** in docker-compose.

**Mermaid diagrams added for this (in `HLD.md`):**
1. **Deployment topology** — VPC, subnets, ALB, ECS/Fargate services, RDS, MSK (LocalStack ↔ AWS).
2. **Rolling deployment** sequence — drain old task → health-check new → shift traffic.
3. **Blue-green** sequence — deploy green, health-check, ALB listener swap, keep blue for rollback.
4. **CI/CD pipeline** flow — build → test → image → deploy → smoke.
5. **p99 latency budget** — per-hop timing across the create-patient path.
6. **Resilience** — circuit-breaker/timeout/retry states on the critical calls.

## Build Order (phases)

1. **Scaffold:** repo skeleton, parent context, `.gitignore`, `docker-compose.yml` shell,
   `README.md`, `docs/` stubs, git init.
2. **billing-service** (gRPC server + proto) — no deps, verify boots.
3. **patient-service** — entity/REST/DB, then gRPC client to billing, then Kafka producer.
4. **notification-service** + **analytics-service** — Kafka consumers.
5. **auth-service** — users, JWT, security, seed.
6. **api-gateway** — routing + JWT filter.
7. **docker-compose** — wire Postgres x3 (auth/patient/analytics) + Kafka + all services +
   **Prometheus + Grafana** with healthchecks, graceful shutdown, Actuator readiness/liveness &
   env vars; bring the whole stack up and confirm the create-patient flow works end-to-end and
   metrics appear in Grafana.
8. **infrastructure** — AWS CDK (Java) stack + `localstack-deploy.sh`.
9. **integration-tests** — REST-assured login → create patient → assert.
10. **docs** — write HLD.md and LLD.md to match the final build.

## Verification (must actually work)

- `mvn -q -T1C verify` builds every module and runs unit/integration tests.
- `docker compose up --build` starts Postgres x2, Kafka, and all 6 services; healthchecks green.
- End-to-end smoke (via `api-requests/*.http` or curl):
  1. `POST /auth/login` (seeded user) → receives JWT.
  2. `POST /api/patients` with Bearer token → 201; patient persisted.
  3. Logs show billing gRPC account created + Kafka event consumed by notification & analytics.
  4. `GET /api/patients` → returns the new patient.
- Swagger UI reachable per service (and aggregated at the gateway).
- `infrastructure/localstack-deploy.sh` synthesizes/deploys the CDK stack against LocalStack
  (documented as optional; requires Docker + LocalStack + `cdklocal`).
- `integration-tests` pass against the running compose stack.

## Notes / Risks

- AWS CDK + LocalStack is the most environment-sensitive piece; docker-compose is the primary
  "actually works" path and is validated first. LocalStack deploy is provided and documented but
  gated on the user having LocalStack installed.
- Exact ports above follow the video's convention and can be adjusted; they are centralized in
  `docker-compose.yml` and each `application.yml`.
- Maven wrapper (`mvnw`) will be included so no global Maven install is required.
