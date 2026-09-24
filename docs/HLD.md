# High-Level Design (HLD) — Patient Management System

## 1. Introduction

The Patient Management System is a microservices application for registering and managing
patients. Creating a patient also opens a billing account and emits an event that downstream
services react to (notifications, analytics). The system is designed to be **horizontally
scalable**, **independently deployable per service**, and **operable with zero-downtime
deployments** under a defined latency budget.

### 1.1 Goals

- Clean service boundaries with a database-per-service data model.
- A single authenticated entrypoint (API gateway + JWT).
- Mixed communication styles chosen per interaction: REST (external), gRPC (low-latency internal
  command), Kafka (asynchronous fan-out).
- Production concerns first-class: health probes, graceful shutdown, metrics/tracing, resilience,
  and a documented deployment and rollback strategy.

### 1.2 Non-goals

- A production payment integration (billing is simulated).
- A real notification channel (notifications are logged).
- Multi-region/DR topology (single-region reference).

## 2. Requirements

### 2.1 Functional

- Authenticate users and issue JWTs; validate them at the edge.
- CRUD patients; enforce unique email.
- On patient creation: create a billing account (synchronous) and publish a `PATIENT_CREATED`
  event (asynchronous).
- Consume patient events to (a) send a welcome notification and (b) record analytics.
- Serve read-only analytics aggregates to authenticated clients.

### 2.2 Non-functional (SLOs)

| Concern | Target |
|---|---|
| Availability | No dropped requests during a normal deploy (zero-downtime) |
| Latency — reads | p99 < 200 ms |
| Latency — patient create (end-to-end via gateway) | p99 < 400 ms |
| Recovery | A single service/task failure must not fail the create flow (graceful degradation) |
| Observability | p99 latency and error rate measurable per service |

## 3. System context

```mermaid
flowchart TB
  user([Client / Front-end])
  subgraph PMS[Patient Management System]
    gw[API Gateway]
    svcs[Microservices]
  end
  user -->|HTTPS REST + JWT| gw
  gw --> svcs
```

The client only ever talks to the API gateway. All internal services are private.

## 4. Container diagram

```mermaid
flowchart LR
  client([Client])
  gw[API Gateway<br/>Spring Cloud Gateway :4004]

  subgraph edge[Edge auth]
    auth[Auth Service :4005]
  end

  subgraph core[Core]
    patient[Patient Service :4000]
    billing[Billing Service<br/>gRPC :9005]
  end

  subgraph events[Event-driven]
    kafka{{Kafka topic: patient}}
    notification[Notification Service :4003]
    analytics[Analytics Service :4002]
  end

  authdb[(auth_db)]
  patientdb[(patient_db)]
  analyticsdb[(analytics_db)]

  client -->|/auth/**| gw
  client -->|/api/patients/** JWT| gw
  client -->|/api/analytics/** JWT| gw
  gw --> auth
  gw --> patient
  gw --> analytics
  auth --> authdb
  patient --> patientdb
  patient -->|gRPC CreateBillingAccount| billing
  patient -->|produce PatientEvent| kafka
  kafka --> notification
  kafka --> analytics
  analytics --> analyticsdb
```

## 5. Service catalog

| Service | Responsibility | Inbound | Outbound | Data |
|---|---|---|---|---|
| api-gateway | Routing, JWT validation, Swagger aggregation | REST (client) | REST (auth/patient/analytics) | none |
| auth-service | Issue/validate JWT, user store | REST | — | `auth_db` |
| patient-service | Patient CRUD, orchestrate side effects | REST | gRPC (billing), Kafka (produce) | `patient_db` |
| billing-service | Create billing accounts | gRPC | — | none (simulated) |
| notification-service | Send welcome notifications | Kafka | — | none |
| analytics-service | Record events, serve aggregates | Kafka + REST | — | `analytics_db` |

## 6. Communication patterns

- **REST (synchronous, external):** client ⇆ gateway ⇆ services. Human-facing, cacheable,
  well-supported by tooling and Swagger.
- **gRPC (synchronous, internal):** patient → billing. Chosen for a low-latency, strongly-typed
  request/response command on the critical create path. Protobuf contract in
  [`billing_service.proto`].
- **Kafka (asynchronous, internal):** patient → {notification, analytics}. Chosen to decouple
  side effects from the request path so a slow/absent consumer never blocks patient creation, and
  to allow independent scaling and replay. Protobuf-encoded `PatientEvent`.

### 6.1 Create-patient sequence

```mermaid
sequenceDiagram
  autonumber
  participant C as Client
  participant G as API Gateway
  participant A as Auth Service
  participant P as Patient Service
  participant B as Billing Service
  participant K as Kafka
  participant N as Notification
  participant AN as Analytics

  C->>G: POST /api/patients (Bearer JWT)
  G->>A: GET /validate (token)
  A-->>G: 200 OK
  G->>P: POST /patients
  P->>P: persist patient (patient_db)
  P->>B: gRPC CreateBillingAccount
  B-->>P: accountId, ACTIVE
  P-)K: produce PatientEvent (async)
  P-->>G: 201 Created
  G-->>C: 201 Created
  K-)N: PatientEvent
  K-)AN: PatientEvent
  N->>N: send welcome notification
  AN->>AN: record analytics (analytics_db)
```

## 7. Data ownership

Each service owns its database; no service reads another's schema directly. Cross-service data
flows only through APIs and events. This keeps services independently deployable and schema
changes local.

## 8. Security & authentication

- Passwords stored as **BCrypt** hashes.
- Login returns a signed **HS256 JWT** (subject = email, `role` claim).
- The gateway's `JwtValidation` filter calls auth-service `/validate` for protected routes and
  rejects with 401 before forwarding.

```mermaid
sequenceDiagram
  autonumber
  participant C as Client
  participant G as API Gateway
  participant A as Auth Service
  C->>G: POST /auth/login {email,password}
  G->>A: /login
  A->>A: verify BCrypt, sign JWT
  A-->>C: { token }
  Note over C,G: subsequent protected calls
  C->>G: GET /api/patients (Bearer token)
  G->>A: GET /validate (Authorization)
  alt valid
    A-->>G: 200
    G->>G: forward to patient-service
  else invalid/missing
    A-->>G: 401
    G-->>C: 401 (never forwarded)
  end
```

## 9. Cross-cutting concerns

- **Configuration:** environment variables per service (see the config matrix in the LLD),
  defaulted for local runs in `docker-compose.yml`.
- **API docs:** each service exposes OpenAPI/Swagger; the gateway aggregates them.
- **Logging:** structured SLF4J logging with correlation via Micrometer observation tags.
- **Observability:** Micrometer → Prometheus metrics and OpenTelemetry-ready traces; Prometheus +
  Grafana are wired into `docker-compose` with a p99/throughput dashboard.
- **Resilience:** Resilience4j retry + circuit breaker on the patient→billing gRPC call, with a
  graceful fallback.

## 10. Deployment topology

Docker Compose is the primary local runtime. For infrastructure-as-code, the `infrastructure`
module (AWS CDK, deployed to LocalStack) provisions the cloud topology:

```mermaid
flowchart TB
  subgraph AWS[AWS / LocalStack region]
    alb[Application Load Balancer]
    subgraph vpc[VPC]
      subgraph ecs[ECS Fargate cluster]
        gwt[api-gateway task]
        autht[auth-service task]
        pt[patient-service task]
        bt[billing-service task]
        nt[notification-service task]
        ant[analytics-service task]
      end
      msk[(MSK / Kafka)]
      rds_auth[(RDS auth_db)]
      rds_patient[(RDS patient_db)]
      rds_analytics[(RDS analytics_db)]
    end
  end
  internet([Internet]) --> alb --> gwt
  gwt --> autht & pt & ant
  pt --> bt
  pt --> msk
  msk --> nt & ant
  autht --> rds_auth
  pt --> rds_patient
  ant --> rds_analytics
```

Services register in a Cloud Map namespace (`patient-management.local`) for DNS-based discovery;
the ALB fronts only the gateway.

## 11. Zero-downtime deployment

**Default strategy: rolling deployment** (per service, one task at a time), with **blue-green**
documented as the opt-in path for the gateway/patient-service where instant rollback is desired.

Zero-downtime is achieved by:

- **Readiness/liveness probes** (Spring Boot Actuator). The load balancer/orchestrator only sends
  traffic to `READY` tasks and drains old ones first.
- **Graceful shutdown** (`server.shutdown=graceful` + a shutdown timeout) so in-flight requests
  complete before a task stops; the platform drains connections.
- **Backward-compatible (expand/contract) DB migrations** so old and new versions run against the
  same schema during the rollout.
- **Kafka consumer groups** — partitions rebalance across restarting instances with no lost
  events; producers are idempotent and retried.
- **gRPC deadlines + retry** so a billing task restart doesn't fail patient creation.

### 11.1 Rolling deployment

```mermaid
sequenceDiagram
  autonumber
  participant D as Deployer
  participant LB as Load Balancer
  participant Old as Task v1
  participant New as Task v2
  D->>New: start Task v2
  New-->>LB: readiness = UP
  LB->>New: begin routing traffic
  D->>LB: deregister Task v1 (drain)
  LB->>Old: stop new traffic, finish in-flight
  Old-->>D: graceful shutdown complete
  Note over LB,New: capacity maintained throughout — no downtime
```

### 11.2 Blue-green (opt-in)

```mermaid
sequenceDiagram
  autonumber
  participant D as Deployer
  participant LB as ALB Listener
  participant Blue as Blue (current)
  participant Green as Green (new)
  D->>Green: deploy Green target group
  Green-->>D: health checks pass
  D->>LB: switch listener Blue -> Green
  Note over LB,Green: 100% traffic now on Green
  alt problem detected
    D->>LB: switch listener back to Blue (instant rollback)
  else stable
    D->>Blue: decommission Blue
  end
```

## 12. CI/CD pipeline

```mermaid
flowchart LR
  commit([Push / PR]) --> build[Build: mvn package]
  build --> test[Unit tests]
  test --> image[Build & scan Docker images]
  image --> push[Push to registry]
  push --> deploy[Rolling deploy to ECS/Fargate]
  deploy --> smoke[Smoke + integration tests]
  smoke -->|fail| rollback[Rollback to previous task set]
  smoke -->|pass| done([Release complete])
```

## 13. Performance & p99 latency budget

The create-patient path is the tightest budget. Target end-to-end **p99 < 400 ms**:

| Hop | Operation | p99 budget |
|---|---|---|
| Gateway | routing + JWT validate (cached path) | 40 ms |
| Gateway → Patient | network + deserialization | 20 ms |
| Patient | persist patient (DB write) | 120 ms |
| Patient → Billing | gRPC CreateBillingAccount | 120 ms |
| Patient → Kafka | async produce (off the response path) | ~0 ms (fire-and-forget) |
| Serialization/response | build + return 201 | 60 ms |
| **Total (synchronous path)** | | **≈ 360 ms** |

Read paths (list/get patient, analytics) target **p99 < 200 ms** — a single service hop plus one
DB read.

How the budget is held:

- **Resilience4j timeouts** cap the billing gRPC call so a slow dependency can't blow the budget;
  the circuit breaker sheds load and the fallback returns a `PENDING_BILLING` status.
- **Async Kafka** keeps notifications/analytics entirely off the response path.
- **Horizontal autoscaling** (ECS target-tracking on CPU / request count) adds capacity under load.
- **Prometheus histogram buckets** (`http_server_requests_seconds_bucket`) + Grafana measure the
  actual p99 continuously.

## 14. Resilience — circuit breaker states

```mermaid
stateDiagram-v2
  [*] --> Closed
  Closed --> Open: failure rate >= 50% over window
  Open --> HalfOpen: after wait-duration (10s)
  HalfOpen --> Closed: trial calls succeed
  HalfOpen --> Open: trial calls fail
  note right of Open
    Calls short-circuit to fallback
    (PENDING_BILLING); patient create
    still succeeds.
  end note
```

## 15. Scalability & trade-offs

- **Database-per-service** enables independent scaling and deployment at the cost of no cross-
  service joins (resolved via events/APIs).
- **gRPC on the critical path** gives low latency and a typed contract but couples patient→billing
  availability; mitigated with timeouts, retry, circuit breaker, and a fallback.
- **Kafka for fan-out** decouples side effects and enables replay/back-pressure, at the cost of
  eventual consistency for notifications and analytics.
- **Gateway as a single entrypoint** centralizes auth and routing but must itself be scaled and
  deployed with zero downtime (hence blue-green as an option there).

## 16. Technology stack

| Concern | Choice |
|---|---|
| Language / runtime | Java 21 |
| Framework | Spring Boot 3.5 |
| Gateway | Spring Cloud Gateway (2025.0.x) |
| Sync internal RPC | gRPC 1.69 + Protobuf 4.29 |
| Async messaging | Apache Kafka (Spring Kafka) |
| Persistence | PostgreSQL 16 + Spring Data JPA |
| AuthN | Spring Security + JJWT (HS256) |
| Resilience | Resilience4j |
| API docs | springdoc-openapi (Swagger UI) |
| Metrics | Micrometer + Prometheus + Grafana |
| Containerization | Docker (multi-stage), Docker Compose |
| IaC | AWS CDK (Java) on LocalStack |
| Testing | JUnit 5, Mockito, REST-assured |
```
