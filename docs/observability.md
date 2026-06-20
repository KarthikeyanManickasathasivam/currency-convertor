# Observability

Five distinct layers cover request tracing, method-level visibility, performance, business audit, and structured log output.

---

## Layer 1 — Request Tracing (Correlation ID)

**File:** `src/main/java/com/exchange/filter/CorrelationIdFilter.java`

Every inbound HTTP request is stamped with a UUID correlation ID. The filter runs at `HIGHEST_PRECEDENCE` so the ID is present on every subsequent log line in the same request, regardless of which class emits it.

| Detail | Value |
|---|---|
| Header read | `X-Correlation-ID` (uses client-supplied value if present, generates UUID otherwise) |
| MDC key | `correlationId` |
| Header echoed back | Yes — clients can tie their own logs to server logs |
| Cleanup | `MDC.remove()` in `finally` block — no leakage across threads |

The ID is injected into every log line via the Logback pattern (`%X{correlationId:-}`), so a single `grep` on a correlation ID in CloudWatch retrieves the full request lifecycle across all classes.

---

## Layer 2 — AOP Method Logging

**File:** `src/main/java/com/exchange/aspect/LoggingAspect.java`

Cross-cutting advice automatically logs method entry, exit, and exceptions across all controllers and services — no per-class boilerplate needed.

| Advice | Level | Output |
|---|---|---|
| `@Before` | DEBUG | `>> com.exchange.service.AuthService.login()` |
| `@AfterReturning` | DEBUG | `<< com.exchange.service.AuthService.login() returned` |
| `@AfterThrowing` | WARN | exception class + message |

**Pointcut scope:** `com.exchange.controller.*` and `com.exchange.service.*`

Security note: return values and arguments are never logged to avoid leaking credentials or PII.

---

## Layer 3 — Performance Monitoring

**File:** `src/main/java/com/exchange/aspect/PerformanceMonitorAspect.java`

`@Around` advice wraps every service method and measures wall-clock execution time.

| Condition | Level | Output |
|---|---|---|
| Execution ≤ 500 ms | DEBUG | `Execution [42 ms] — com.exchange.service.ExchangeRateService.getRate()` |
| Execution > 500 ms | WARN | `SLOW execution [823 ms] — com.exchange.service.ExchangeRateService.getRate()` |

**Pointcut scope:** `com.exchange.service.*` only (controllers excluded — HTTP timing belongs at the filter level).

The 500 ms threshold is intentionally below the Resilience4j retry wait (500 ms) so a slow external API call surfaces in logs before the retry fires.

---

## Layer 4 — Business Audit Trail

**Files:**
- `src/main/java/com/exchange/model/Log.java` — JPA entity (`logs` table)
- `src/main/java/com/exchange/service/LogService.java` — async writer
- `src/main/java/com/exchange/repository/LogRepository.java` — query interface

Business events are persisted to PostgreSQL as structured records, separate from application logs. This gives admins a queryable audit trail that survives log rotation.

**Log entity fields:**

| Field | Type | Purpose |
|---|---|---|
| `event` | VARCHAR(100) | Event name e.g. `TRANSACTION_CREATED` |
| `eventType` | VARCHAR(50) | Category e.g. `TRANSACTION`, `AUTH` |
| `timestamp` | TIMESTAMP | Auto-set via `@PrePersist` |
| `userId` | UUID | Associated user (nullable for pre-auth events) |
| `ipAddress` | VARCHAR(45) | Client IP from `X-Forwarded-For` |
| `details` | JSON | Flexible key-value context |

**Events currently logged:**

| Event | Type | Where |
|---|---|---|
| `USER_REGISTERED` | AUTH | `AuthController.register()` |
| `LOGIN_ATTEMPT` | AUTH | `AuthController.login()` |
| `MFA_VERIFIED` | AUTH | `AuthController.verifyMfa()` |
| `LOGOUT` | AUTH | `AuthController.logout()` |
| `TRANSACTION_CREATED` | TRANSACTION | `TransactionService` |
| `TRANSACTION_APPROVED` | TRANSACTION | `TransactionService` |
| `TRANSACTION_REJECTED` | TRANSACTION | `TransactionService` |

`LogService.log()` is `@Async` — writes never block the request thread. Admins can query via `LogRepository` by user, event type, or time range.

---

## Layer 5 — Structured Log Output

**File:** `src/main/resources/logback-spring.xml`

Log format is profile-aware. No code changes needed between local dev and production.

### Local profile — human-readable

```
2026-06-20 12:34:56.123 [http-nio-8080-exec-1] [abc-123] INFO  c.e.service.AuthService - JWT issued for: user@example.com
```

Pattern includes coloured level (`%highlight`) and cyan logger name (`%cyan`) for fast visual scanning.

Logger levels on `local`:

| Logger | Level |
|---|---|
| `com.exchange` | DEBUG |
| `org.hibernate.SQL` | DEBUG |
| `org.hibernate.type.descriptor.sql` | TRACE |
| `org.springframework.security` | WARN |

### AWS profile — valid JSON for CloudWatch

Uses Logback's built-in `ch.qos.logback.classic.encoder.JsonEncoder` (available since Logback 1.4.11, bundled in Spring Boot 3.3.x — no extra dependency).

Each line is a valid JSON object:

```json
{
  "timestamp": "2026-06-20T12:34:56.123+0530",
  "level": "INFO",
  "thread": "http-nio-5000-exec-3",
  "logger": "c.e.service.AuthService",
  "message": "JWT issued for: user@example.com",
  "mdc": { "correlationId": "abc-123-def-456" }
}
```

The `correlationId` from MDC (Layer 1) appears under `mdc` in every JSON event, making CloudWatch Logs Insights queries straightforward:

```
fields @timestamp, mdc.correlationId, level, message
| filter mdc.correlationId = "abc-123-def-456"
| sort @timestamp asc
```

Logger levels on `aws`:

| Logger | Level |
|---|---|
| `com.exchange` | INFO |
| `org.hibernate.SQL` | WARN |
| `org.springframework.security` | WARN |

---

## How the Layers Interact

```
Inbound request
      │
      ▼
[Layer 1] CorrelationIdFilter — stamps MDC with correlationId
      │
      ▼
[Layer 2] LoggingAspect — logs method entry on every controller + service call
      │
      ▼
  Business logic executes
      │
      ├──▶ [Layer 4] LogService.log() — async DB write for audit events
      │
      ▼
[Layer 3] PerformanceMonitorAspect — logs elapsed time, WARNs if > 500 ms
      │
      ▼
Response sent
      │
      ▼
[Layer 5] Logback encoder — formats all of the above as coloured text (local)
                             or valid JSON (aws) with correlationId in every line
```

---

## What Is Not Covered (Known Gaps)

| Gap | Impact |
|---|---|
| No HTTP request/response logging | Failures in filters or before controllers leave no inbound request trace |
| No Micrometer custom metrics | `/actuator/metrics` shows JVM defaults only; no counters for transactions, MFA failures, or circuit breaker opens |
| No distributed tracing (X-Ray) | Correlation ID is home-grown; not compatible with AWS X-Ray trace IDs |
| `@Async` uses default executor | If `ThreadPoolTaskExecutor` is not configured, `LogService` falls back to `SimpleAsyncTaskExecutor` (unbounded threads) |
