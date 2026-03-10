# Zero Trust API Gateway

Click Hear to Test -> http://57.180.37.25/api/auth/token

/ production-grade security infrastructure
Zero Trust
API Gateway
Spring Boot · Redis · Kafka · PostgreSQL · ONNX · MaxMind
Java 17
Spring Boot 3.2
Reactive / WebFlux
Zero Trust
ML Threat Detection
Docker Ready
01 — Architecture
Request Pipeline
CLIENT
──────►
:8080 GATEWAY
▼
① Audit
→
② Rate Limit
→
③ JWT Auth
→
④ Geo Block
→
⑤ Proxy Route
▼
/api/payments
/api/users
/api/orders
Redis
Rate Limits · Tokens · CB
Kafka
Audit Events
Postgres
Audit Logs · Geo · Schema
Prometheus
Metrics · Grafana
02 — Filter Chain
Security Layers
①
AuditFilter — @Order(5)
Assigns a UUID to every request. Records method, path, client IP, user ID, response status, and latency. Publishes an AuditEvent to Kafka topic gateway-audit after the response is committed.
Topic: gateway-audit  |  Fail: open
②
RateLimitFilter — @Order(10)
Sliding-window counter in Redis. Key is user:{userId} when authenticated or ip:{clientIp} for anonymous requests. Adds X-RateLimit-Remaining and X-RateLimit-Limit headers. Returns 429 on breach.
Default: 60 RPM · Burst: 10 · Window: 60s
③
JwtAuthFilter — @Order(20)
Validates HS256 JWT from Authorization: Bearer <token>. Checks issuer, expiry, and Redis blacklist (by JTI). Mutates the request to add X-User-Id, X-User-Role, X-Token-Jti headers for downstream. Skips public paths.
Public paths: /auth/**, /actuator/health, /actuator/prometheus
④
GeoBlockFilter — @Order(30)
Reads country from CF-IPCountry or X-Country-Code header. Checks against Redis-cached blocked countries and hardcoded OFAC sanctions list. Returns 403 for blocked regions. Fails open if geo headers are absent.
Blocked by default: KP, IR, SY (OFAC sanctions)
⑤
GatewayRoutingFilter — Proxy
Matches request path to configured route. Checks circuit breaker state (Redis). Forwards via WebClient, strips hop-by-hop headers, pipes response body. Records success/failure to circuit breaker. Returns 503 if CB is OPEN, 502 on network error.
CB threshold: 5 failures · Half-open timeout: 30s
03 — Endpoints
All Routes
🔐 Auth  —  /auth/**
POST
/auth/login
Issue JWT token · body: {username, password}
Public
POST
/auth/register
Register new user · body: {username, password, email}
Public
POST
/auth/logout
Blacklist current JWT by JTI in Redis
JWT
POST
/auth/refresh
Rotate token — old JTI blacklisted, new token issued
JWT
GET
/auth/validate
Validate token · returns claims, expiry, role
JWT
🌐 Proxied Routes  —  /api/**
ANY
/api/payments/**
Proxied → payment-service:5678 · circuit-breaker guarded
JWT
ANY
/api/users/**
Proxied → user-service:5678 · circuit-breaker guarded
JWT
ANY
/api/orders/**
Proxied → order-service:5678 · circuit-breaker guarded
JWT
🛠️ Admin  —  /admin/**
GET
/admin/health
Gateway status, version, timestamp
Admin
GET
/admin/routes
List all configured proxy routes with targets
Admin
GET
/admin/circuit-breaker/{serviceId}
Get current circuit breaker state (OPEN / CLOSED)
Admin
POST
/admin/circuit-breaker/{serviceId}/reset
Force circuit breaker to CLOSED state
Admin
POST
/admin/token/revoke
Blacklist token by JTI · body: {jti, userId, ttlMs?}
Admin
GET
/admin/rate-limit/{key}
Remaining quota for a given rate-limit key
Admin
GET
/admin/config
View live config — JWT, rate limits, CB settings (secret hidden)
Admin
📊 System  —  Public
GET
/health
Simple liveness probe
Public
GET
/info
Service name, version, route count
Public
GET
/actuator/health
Spring Boot health indicator
Public
GET
/actuator/prometheus
Prometheus metrics scrape endpoint
Public
GET
/actuator/metrics
Spring Micrometer metrics
Public
04 — Quick Start
Get Running in 60 Seconds
1
Clone & configure
terminal
# Set required secrets export JWT_SECRET="your-256-bit-secret-key-here-must-be-long-enough" export POSTGRES_PASSWORD="strong-db-password"
2
Start the full stack
terminal
docker-compose up --build # Starts: gateway · redis · postgres · kafka · zookeeper · grafana · prometheus # + dummy payment / user / order services for testing
3
Login and call an API
curl
# 1. Get a token curl -X POST http://localhost:8080/auth/login \ -H "Content-Type: application/json" \ -d '{"username":"admin","password":"any"}' # 2. Call a proxied route curl http://localhost:8080/api/payments \ -H "Authorization: Bearer <token>"
⚠ Production Warning: The default JWT secret in application.yml is insecure. Always override GATEWAY_JWT_SECRET and POSTGRES_PASSWORD via environment variables before deploying.
05 — Stack
Technology
⚡
Spring WebFlux
Reactive gateway runtime
Spring Boot 3.2.4
⚡
Redis
Rate limiting · token blacklist · circuit breaker state
7-alpine · Reactive client
📨
Kafka
Async audit event streaming
Confluent 7.6 · Spring Kafka
🗄️
PostgreSQL
Audit log · geo history · model versions
16-alpine
🔑
JJWT
HS256 JWT signing & validation
0.12.5
🤖
ONNX Runtime
ML threat scoring inference
1.17.0 · Microsoft
🗺️
MaxMind GeoIP2
IP geolocation · impossible travel
4.2.0
📊
Prometheus + Grafana
Metrics scrape · dashboards
Prometheus 2.50 · Grafana 10.3
06 — Database
Schema Overview
audit_log
id PK
BIGSERIAL
request_id
UUID
client_ip
VARCHAR(45)
user_id
VARCHAR(255)
decision
VARCHAR(16)
threat_score
DOUBLE
details
JSONB
token_blacklist
jti PK
VARCHAR(255)
user_id
VARCHAR(255)
blacklisted_at
TIMESTAMPTZ
expires_at
TIMESTAMPTZ
user_behavior_profile
user_id PK
VARCHAR(255)
typical_user_agents
TEXT[]
typical_hours
INT[]
avg_request_rate
DOUBLE
last_known_ip
VARCHAR(45)
circuit_breaker_state
service_name PK
VARCHAR(255)
state
VARCHAR(16)
failure_count
INT
opened_at
TIMESTAMPTZ
half_open_at
TIMESTAMPTZ
geo_login_history
id PK
BIGSERIAL
user_id
VARCHAR(255)
ip_address
VARCHAR(45)
country_code
VARCHAR(3)
latitude / longitude
DOUBLE
model_versions
id PK
BIGSERIAL
model_name
VARCHAR(255)
version
VARCHAR(64)
accuracy
DOUBLE
is_active
BOOLEAN
07 — Ports
Service Map
Port	Service	Notes
8080	Zero Trust Gateway	Main entry point — all traffic flows through here
6379	Redis	Rate limits, token blacklist, circuit breaker state
5432	PostgreSQL	Audit logs, geo history, model versions
9092	Kafka	Audit event streaming topic: gateway-audit
3000	Grafana	Dashboards — default admin/admin
9090	Prometheus	Scrapes /actuator/prometheus every 15s
5678	payment-service (mock)	hashicorp/http-echo stub
5679	user-service (mock)	hashicorp/http-echo stub
5680	order-service (mock)	hashicorp/http-echo stub
08 — Security Features
Zero Trust Controls
🔐
JWT Authentication
HS256 signed tokens. Configurable expiry (default 1h). Claims carry userId and role. Every token has a JTI for precise revocation.
⏱️
Rate Limiting
Redis sliding window. 60 RPM per user or IP. Burst of 10 requests. Remaining quota returned in response headers.
🚫
Token Revocation
JTI blacklist in Redis with TTL matching token expiry. Supports instant logout and admin-forced revocation.
🌍
Geo Blocking
Country-level access control via CF-IPCountry or X-Country-Code headers. Redis-cached block list. OFAC sanctions enforced by default.
⚡
Circuit Breaker
Per-service state machine in Redis. CLOSED → OPEN after 5 failures. HALF_OPEN after 30s timeout. Returns 503 when open.
📋
Full Audit Trail
Every request logged to Kafka with UUID, IP, user, method, path, status, and latency. Async — never blocks the request path.
🤖
ML Threat Scoring
ONNX Runtime inference for real-time threat detection. Low threshold at 60, medium at 80. Pluggable model with version tracking in DB.
🗺️
Impossible Travel
Flags logins that are geographically impossible given the time since last login. Threshold: 900 km/h. Powered by MaxMind GeoIP2.
09 — Configuration
Environment Variables
.env
# ── Required in production ────────────────────────────────── GATEWAY_JWT_SECRET=your-256-bit-minimum-secret-for-hs256-signing POSTGRES_PASSWORD=strong-random-password # ── Service URLs (defaults shown) ─────────────────────────── PAYMENT_SERVICE_URL=http://payment-service:5678 USER_SERVICE_URL=http://user-service:5678 ORDER_SERVICE_URL=http://order-service:5678 # ── Optional ML / Geo ──────────────────────────────────────── ONNX_MODEL_PATH=/models/threat-detector.onnx GEOIP_DB_PATH=/data/GeoLite2-City.mmdb # ── Shadow / Chaos (testing only) ──────────────────────────── SHADOW_SERVICE_URL=http://shadow-svc:9999
ℹ Chaos Engineering: Set gateway.chaos.enabled=true with latency-ms and error-rate to inject faults for resilience testing. Never enable in production.
ZTGateway
Zero Trust API Gateway  ·  v1.0.0-SNAPSHOT
