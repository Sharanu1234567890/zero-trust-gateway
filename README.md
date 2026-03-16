# Zero Trust API Gateway

Click Hear to Test -> http://57.180.37.25/api/auth/token
   
<div align="center">

```
███████╗███████╗██████╗  ██████╗      ████████╗██████╗ ██╗   ██╗███████╗████████╗
╚══███╔╝██╔════╝██╔══██╗██╔═══██╗        ██╔══╝██╔══██╗██║   ██║██╔════╝╚══██╔══╝
  ███╔╝ █████╗  ██████╔╝██║   ██║        ██║   ██████╔╝██║   ██║███████╗   ██║   
 ███╔╝  ██╔══╝  ██╔══██╗██║   ██║        ██║   ██╔══██╗██║   ██║╚════██║   ██║   
███████╗███████╗██║  ██║╚██████╔╝        ██║   ██║  ██║╚██████╔╝███████║   ██║   
╚══════╝╚══════╝╚═╝  ╚═╝ ╚═════╝         ╚═╝   ╚═╝  ╚═╝ ╚═════╝ ╚══════╝   ╚═╝  
```

# ⚡ Zero Trust API Gateway

**Production-grade reactive API gateway with JWT auth, ML threat detection, geo-blocking, and circuit breaking**

[![Java 17](https://img.shields.io/badge/Java-17-orange?style=flat-square&logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.4-brightgreen?style=flat-square&logo=springboot)](https://spring.io/projects/spring-boot)
[![WebFlux](https://img.shields.io/badge/Reactive-WebFlux-blue?style=flat-square&logo=spring)](https://docs.spring.io/spring-framework/reference/web/webflux.html)
[![Redis](https://img.shields.io/badge/Redis-7-red?style=flat-square&logo=redis)](https://redis.io/)
[![Kafka](https://img.shields.io/badge/Kafka-7.6-black?style=flat-square&logo=apachekafka)](https://kafka.apache.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue?style=flat-square&logo=postgresql)](https://www.postgresql.org/)
[![Docker](https://img.shields.io/badge/Docker-Ready-2496ED?style=flat-square&logo=docker)](https://www.docker.com/)
[![Live](https://img.shields.io/badge/Live-57.180.37.25-success?style=flat-square)](http://57.180.37.25)

> **Never trust. Always verify.** Every request is authenticated, rate-limited, geo-checked, and threat-scored before it ever reaches your services.

</div>

---

## 📡 Live Server

```
http://57.180.37.25
```

Test it right now:

```bash
# Get a token
curl -X POST http://57.180.37.25/api/auth/token \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}'

# Call a protected route
curl http://57.180.37.25/api/payments \
  -H "Authorization: Bearer <your_token>"
```

---

## 🗺️ Architecture

```
                          ┌─────────────────────────────────────────────┐
  CLIENT ──────────────►  │           ZERO TRUST GATEWAY :8080           │
                          │                                               │
                          │  ① AuditFilter        (@Order 5)             │
                          │       ↓                                       │
                          │  ② RateLimitFilter    (@Order 10)  ←→ Redis  │
                          │       ↓                                       │
                          │  ③ JwtAuthFilter      (@Order 20)  ←→ Redis  │
                          │       ↓                                       │
                          │  ④ GeoBlockFilter     (@Order 30)  ←→ Redis  │
                          │       ↓                                       │
                          │  ⑤ GatewayRoutingFilter (Proxy)              │
                          └──────────────┬──────────────────────────────-┘
                                         │
                  ┌──────────────────────┼──────────────────────┐
                  ▼                      ▼                       ▼
          payment-service:5678   user-service:5678      order-service:5678


  Supporting Infrastructure:
  ┌─────────┐  ┌──────────┐  ┌──────────────┐  ┌──────────────────┐
  │  Redis  │  │ Postgres │  │    Kafka     │  │ Prometheus+Grafana│
  │  :6379  │  │  :5432   │  │    :9092     │  │   :9090 / :3000  │
  └─────────┘  └──────────┘  └──────────────┘  └──────────────────┘
```

---

## 🔗 All Endpoints

### 🔐 Auth — `/auth/**`  *(No token required)*

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/auth/login` | Issue JWT · body: `{"username","password"}` |
| `POST` | `/auth/register` | Register user · body: `{"username","password","email"}` |
| `POST` | `/auth/logout` | Blacklist current token JTI in Redis |
| `POST` | `/auth/refresh` | Rotate token — old JTI blacklisted, new one issued |
| `GET`  | `/auth/validate` | Validate token · returns claims, expiry, role |

**Login example:**
```bash
curl -X POST http://57.180.37.25/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "admin", "password": "admin"}'
```
```json
{
  "access_token": "eyJhbGciOiJIUzI1NiJ9...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "user_id": "admin",
  "role": "ADMIN"
}
```

---

### 🌐 Proxied Routes — `/api/**`  *(JWT required)*

| Method | Endpoint | Routes To | Circuit Breaker |
|--------|----------|-----------|-----------------|
| `ANY` | `/api/payments/**` | `payment-service:5678` | ✅ Guarded |
| `ANY` | `/api/users/**` | `user-service:5678` | ✅ Guarded |
| `ANY` | `/api/orders/**` | `order-service:5678` | ✅ Guarded |

```bash
curl http://57.180.37.25/api/payments \
  -H "Authorization: Bearer <token>"
```

---

### 🛠️ Admin — `/admin/**`  *(JWT required)*

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET`  | `/admin/health` | Gateway status, version, timestamp |
| `GET`  | `/admin/routes` | List all configured proxy routes |
| `GET`  | `/admin/circuit-breaker/{serviceId}` | Get CB state: `OPEN` or `CLOSED` |
| `POST` | `/admin/circuit-breaker/{serviceId}/reset` | Force circuit breaker to CLOSED |
| `POST` | `/admin/token/revoke` | Blacklist a token by JTI · body: `{"jti","userId","ttlMs?"}` |
| `GET`  | `/admin/rate-limit/{key}` | Remaining quota for a rate-limit key |
| `GET`  | `/admin/config` | Live config snapshot (JWT secret excluded) |

---

### 📊 System — *(Public)*

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/health` | Liveness probe |
| `GET` | `/info` | Service name, version, route count |
| `GET` | `/actuator/health` | Spring Boot health indicator |
| `GET` | `/actuator/prometheus` | Prometheus metrics scrape |
| `GET` | `/actuator/metrics` | Micrometer metrics |

---

## 🛡️ Security Filter Chain

```
Request In
    │
    ▼
┌─────────────────────────────────────────────────┐
│  ① AUDIT  (Order 5)                              │
│  Assigns UUID · records IP, method, path, user   │
│  Publishes AuditEvent to Kafka after response    │
└────────────────────────┬────────────────────────┘
                         ▼
┌─────────────────────────────────────────────────┐
│  ② RATE LIMIT  (Order 10)                        │
│  Key: user:{id} or ip:{addr}  →  Redis counter   │
│  60 RPM / 60s window  →  429 on breach           │
│  Adds X-RateLimit-Remaining header               │
└────────────────────────┬────────────────────────┘
                         ▼
┌─────────────────────────────────────────────────┐
│  ③ JWT AUTH  (Order 20)                          │
│  Validates HS256 token + issuer + expiry         │
│  Checks JTI against Redis blacklist              │
│  Injects X-User-Id, X-User-Role, X-Token-Jti    │
│  Skips: /auth/**, /actuator/health               │
└────────────────────────┬────────────────────────┘
                         ▼
┌─────────────────────────────────────────────────┐
│  ④ GEO BLOCK  (Order 30)                         │
│  Reads CF-IPCountry or X-Country-Code header     │
│  Redis-cached block list + OFAC hardcoded        │
│  Blocked by default: KP, IR, SY  →  403          │
│  Fails open if no geo header present             │
└────────────────────────┬────────────────────────┘
                         ▼
┌─────────────────────────────────────────────────┐
│  ⑤ PROXY ROUTE                                   │
│  Matches path prefix to route config             │
│  Checks circuit breaker state (Redis)  →  503    │
│  Forwards via WebClient, pipes response          │
│  Records success/failure to circuit breaker      │
└─────────────────────────────────────────────────┘
```

---

## ⚡ Quick Start

**1. Clone and set secrets**
```bash
export JWT_SECRET="your-256-bit-minimum-secret-for-hs256-algorithm"
export POSTGRES_PASSWORD="strong-random-password"
```

**2. Start full stack**
```bash
docker-compose up --build
```

This spins up: Gateway · Redis · PostgreSQL · Kafka · Zookeeper · Grafana · Prometheus · 3 mock services

**3. Verify it's running**
```bash
curl http://localhost:8080/health
# {"status":"UP","timestamp":"2026-03-11T..."}
```

---

## 🏗️ Project Structure

```
zero-trust-gateway/
├── src/main/java/com/ztgateway/
│   ├── ZeroTrustGatewayApplication.java   ← Entry point
│   ├── config/
│   │   ├── GatewayProperties.java         ← All config bindings
│   │   ├── KafkaConfig.java               ← Kafka producer setup
│   │   ├── RedisConfig.java               ← Reactive Redis template
│   │   ├── SecurityConfig.java            ← CORS configuration
│   │   └── WebClientConfig.java           ← WebClient for proxying
│   ├── controller/
│   │   ├── AuthController.java            ← /auth/** endpoints
│   │   ├── AdminController.java           ← /admin/** endpoints
│   │   ├── HealthController.java          ← /health, /info
│   │   └── GatewayRoutingFilter.java      ← /api/** proxy routing
│   ├── filter/
│   │   ├── AuditFilter.java               ← Order 5 — request logging
│   │   ├── RateLimitFilter.java           ← Order 10 — Redis rate limit
│   │   ├── JwtAuthFilter.java             ← Order 20 — JWT validation
│   │   └── GeoBlockFilter.java            ← Order 30 — country blocking
│   ├── service/
│   │   ├── JwtService.java                ← Token issue / validate / parse
│   │   ├── RateLimiterService.java        ← Sliding window counter
│   │   ├── TokenBlacklistService.java     ← JTI blacklist in Redis
│   │   ├── CircuitBreakerService.java     ← CB state machine in Redis
│   │   ├── AuditService.java              ← Kafka event publisher
│   │   └── ProxyService.java              ← WebClient request forwarding
│   ├── model/
│   │   └── AuditEvent.java                ← Kafka event payload
│   └── util/
│       ├── FilterOrder.java               ← Order constants
│       └── ResponseUtils.java             ← JSON error responses
├── src/main/resources/
│   └── application.yml                    ← All configuration
├── scripts/
│   ├── init.sql                           ← PostgreSQL schema
│   └── prometheus.yml                     ← Metrics scrape config
├── Dockerfile                             ← Multi-stage build
└── docker-compose.yml                     ← Full infra stack
```

---

## 🗄️ Database Schema

```sql
audit_log              -- Every request logged with threat score + decision
token_blacklist        -- Revoked JTIs with TTL
user_behavior_profile  -- Typical agents, hours, routes per user
geo_login_history      -- IP + lat/lon login history for travel detection
blocked_countries      -- Country-level access control (KP, IR, SY default)
circuit_breaker_state  -- Per-service CB state + failure count
model_versions         -- ONNX model registry with accuracy tracking
```

---

## 🚢 Service Ports

| Port | Service | Notes |
|------|---------|-------|
| `8080` | **Zero Trust Gateway** | Main entry point |
| `6379` | Redis | Rate limits · token blacklist · circuit breaker |
| `5432` | PostgreSQL | Audit logs · geo history · schema |
| `9092` | Kafka | Audit events → topic `gateway-audit` |
| `3000` | Grafana | Dashboards · default `admin/admin` |
| `9090` | Prometheus | Scrapes `/actuator/prometheus` every 15s |
| `5678` | payment-service (mock) | `hashicorp/http-echo` stub |
| `5679` | user-service (mock) | `hashicorp/http-echo` stub |
| `5680` | order-service (mock) | `hashicorp/http-echo` stub |

---

## ⚙️ Configuration Reference

```yaml
gateway:
  jwt:
    secret: ${GATEWAY_JWT_SECRET}     # ⚠ Must be 256-bit minimum
    expiration-ms: 3600000            # 1 hour
    issuer: zero-trust-gateway

  rate-limit:
    default-rpm: 60                   # Requests per minute
    burst-size: 10
    window-size-seconds: 60

  circuit-breaker:
    failure-threshold: 5              # Failures before OPEN
    half-open-timeout-ms: 30000       # 30s before retry
    success-threshold: 3

  threat:
    low-threshold: 60                 # ONNX model score thresholds
    medium-threshold: 80

  geo:
    impossible-travel-threshold-kmh: 900
```

### Environment Variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `GATEWAY_JWT_SECRET` | ✅ **Yes** | insecure fallback | HS256 signing key (min 256 bits) |
| `POSTGRES_PASSWORD` | ✅ **Yes** | `gateway_secret` | Database password |
| `PAYMENT_SERVICE_URL` | No | `http://payment-service:5678` | Payment backend |
| `USER_SERVICE_URL` | No | `http://user-service:5678` | User backend |
| `ORDER_SERVICE_URL` | No | `http://order-service:5678` | Order backend |
| `ONNX_MODEL_PATH` | No | *(disabled)* | Path to threat detection model |
| `GEOIP_DB_PATH` | No | *(disabled)* | MaxMind GeoLite2 database path |
| `SHADOW_SERVICE_URL` | No | *(disabled)* | Traffic mirroring target |

---

## 🔬 Tech Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| Runtime | Spring Boot WebFlux (Netty) | 3.2.4 |
| Language | Java | 17 |
| Cache / State | Redis (Reactive) | 7-alpine |
| Messaging | Apache Kafka | Confluent 7.6 |
| Database | PostgreSQL | 16-alpine |
| Auth | JJWT (HS256) | 0.12.5 |
| ML Inference | ONNX Runtime | 1.17.0 |
| Geolocation | MaxMind GeoIP2 | 4.2.0 |
| Metrics | Micrometer + Prometheus | — |
| Dashboards | Grafana | 10.3.1 |
| Build | Maven + Docker multi-stage | — |

---

## ⚠️ Security Notes

> **Never deploy with default secrets.**

- Override `GATEWAY_JWT_SECRET` — the fallback in `application.yml` is public and insecure
- Override `POSTGRES_PASSWORD` — default `gateway_secret` is not safe
- Change Grafana default password (`admin/admin`) before exposing port 3000
- Kafka uses `PLAINTEXT` — add TLS before exposing to the internet
- Chaos mode (`gateway.chaos.enabled`) must never be enabled in production

---

## 🧪 Testing All Endpoints

```bash
BASE=http://57.180.37.25

# 1. Health check (public)
curl $BASE/health

# 2. Login
TOKEN=$(curl -s -X POST $BASE/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}' | grep -o '"access_token":"[^"]*"' | cut -d'"' -f4)

# 3. Validate token
curl $BASE/auth/validate -H "Authorization: Bearer $TOKEN"

# 4. Call proxied services
curl $BASE/api/payments -H "Authorization: Bearer $TOKEN"
curl $BASE/api/users    -H "Authorization: Bearer $TOKEN"
curl $BASE/api/orders   -H "Authorization: Bearer $TOKEN"

# 5. Admin — circuit breaker state
curl $BASE/admin/circuit-breaker/payment-service -H "Authorization: Bearer $TOKEN"

# 6. Admin — config
curl $BASE/admin/config -H "Authorization: Bearer $TOKEN"

# 7. Logout (blacklists token)
curl -X POST $BASE/auth/logout -H "Authorization: Bearer $TOKEN"

# 8. Prometheus metrics
curl $BASE/actuator/prometheus
```

---

<div align="center">

**Zero Trust API Gateway** · v1.0.0 · Java 17 · Spring Boot 3.2 · Reactive

*"Never trust. Always verify."*

</div>
