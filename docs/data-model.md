# Data Model & Database Design

## Entity: users

| Field | Java Type | JPA Annotations | Notes |
|-------|-----------|-----------------|-------|
| userId | Long | `@Id @GeneratedValue(strategy = IDENTITY)` | Auto-increment PK |
| username | String | `@Column(unique = true, nullable = false, length = 50)` | Login identifier; indexed |
| email | String | `@Column(unique = true, nullable = false, length = 255) @Email` | Required for MFA OTP and notifications via SES; indexed |
| passwordHash | String | `@Column(nullable = false, length = 255)` | BCrypt hash (cost 12); NEVER returned in API responses |
| role | Role (enum) | `@Enumerated(EnumType.STRING) @Column(nullable = false)` | ADMIN or USER; embedded in JWT claims |
| createdAt | LocalDateTime | `@CreatedDate @Column(updatable = false)` | JPA auditing |
| updatedAt | LocalDateTime | `@LastModifiedDate` | JPA auditing |
| isActive | Boolean | `@Column(nullable = false)` default true | Soft delete flag |

## Entity: exchange_rates

| Field | Java Type | JPA Annotations | Notes |
|-------|-----------|-----------------|-------|
| id | Long | `@Id @GeneratedValue(strategy = IDENTITY)` | Auto-increment PK |
| fromCurrency | String | `@Column(nullable = false, length = 10)` | ISO 4217 code (e.g., USD) |
| toCurrency | String | `@Column(nullable = false, length = 10)` | Composite unique with fromCurrency |
| rate | BigDecimal | `@Column(nullable = false, precision = 18, scale = 8)` | 8 decimal places for forex precision |
| lastUpdated | LocalDateTime | `@Column(nullable = false)` | Timestamp of last update |
| source | Source (enum) | `@Enumerated(EnumType.STRING)` | API or MANUAL |
| isActive | Boolean | `@Column(nullable = false)` default true | Soft delete flag |

## Entity: transactions

| Field | Java Type | JPA Annotations | Notes |
|-------|-----------|-----------------|-------|
| transactionId | Long | `@Id @GeneratedValue(strategy = IDENTITY)` | Auto-increment PK |
| user | User | `@ManyToOne(fetch = LAZY) @JoinColumn(name = "user_id")` | FK to users; LAZY to avoid N+1 |
| fromCurrency | String | `@Column(nullable = false, length = 10)` | Source currency |
| toCurrency | String | `@Column(nullable = false, length = 10)` | Target currency |
| amount | BigDecimal | `@Column(nullable = false, precision = 18, scale = 2)` | Original amount |
| convertedAmount | BigDecimal | `@Column(nullable = false, precision = 18, scale = 2)` | Converted result |
| rate | BigDecimal | `@Column(nullable = false, precision = 18, scale = 8)` | Rate snapshot at conversion time (immutable) |
| transactionDate | LocalDateTime | `@CreatedDate @Column(updatable = false)` | DEFAULT NOW() |
| status | TransactionStatus (enum) | `@Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)` | PENDING_APPROVAL, APPROVED, REJECTED. Default APPROVED for < $100. Indexed. |
| approvedBy | User | `@ManyToOne(fetch = LAZY) @JoinColumn(name = "approved_by", nullable = true)` | Admin who approved/rejected; NULL for auto-approved |
| approvalDate | LocalDateTime | `@Column(nullable = true)` | Timestamp of approval/rejection action |

## Entity: logs

| Field | Java Type | JPA Annotations | Notes |
|-------|-----------|-----------------|-------|
| logId | Long | `@Id @GeneratedValue(strategy = IDENTITY)` | Auto-increment PK |
| event | String | `@Column(nullable = false, length = 255)` | Event description |
| eventType | String | `@Column(nullable = false, length = 50)` | LOGIN, LOGOUT, CONVERSION, ADMIN_ACTION, API_CALL, ERROR |
| timestamp | LocalDateTime | `@CreatedDate @Column(updatable = false)` | Event timestamp |
| userId | Long | `@Column(nullable = true)` | Nullable; system events have no user |
| ipAddress | String | `@Column(length = 45)` | IPv4/IPv6 |
| details | String | `@Column(columnDefinition = "TEXT")` | JSON-formatted additional details |

## Enums

```java
public enum Role { ADMIN, USER }
public enum Source { API, MANUAL }
public enum TransactionStatus { PENDING_APPROVAL, APPROVED, REJECTED }
```

## Flyway Migrations

### V1__create_users_table.sql
```sql
CREATE TABLE users (
    user_id SERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('ADMIN', 'USER')),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    is_active BOOLEAN NOT NULL DEFAULT true
);
CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_role ON users(role);
```

### V2__create_exchange_rates_table.sql
```sql
CREATE TABLE exchange_rates (
    id SERIAL PRIMARY KEY,
    from_currency VARCHAR(10) NOT NULL,
    to_currency VARCHAR(10) NOT NULL,
    rate DECIMAL(18,8) NOT NULL,
    last_updated TIMESTAMP NOT NULL DEFAULT NOW(),
    source VARCHAR(10) NOT NULL CHECK (source IN ('API', 'MANUAL')),
    is_active BOOLEAN NOT NULL DEFAULT true,
    UNIQUE(from_currency, to_currency)
);
```

### V3__create_transactions_table.sql
```sql
CREATE TABLE transactions (
    transaction_id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(user_id),
    from_currency VARCHAR(10) NOT NULL,
    to_currency VARCHAR(10) NOT NULL,
    amount DECIMAL(18,2) NOT NULL,
    converted_amount DECIMAL(18,2) NOT NULL,
    rate DECIMAL(18,8) NOT NULL,
    transaction_date TIMESTAMP NOT NULL DEFAULT NOW(),
    status VARCHAR(20) NOT NULL DEFAULT 'APPROVED' CHECK (status IN ('PENDING_APPROVAL', 'APPROVED', 'REJECTED')),
    approved_by INTEGER REFERENCES users(user_id),
    approval_date TIMESTAMP
);
CREATE INDEX idx_transactions_user_id ON transactions(user_id);
CREATE INDEX idx_transactions_date ON transactions(transaction_date);
CREATE INDEX idx_transactions_status ON transactions(status);
```

### V4__create_logs_table.sql
```sql
CREATE TABLE logs (
    log_id SERIAL PRIMARY KEY,
    event VARCHAR(255) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    timestamp TIMESTAMP NOT NULL DEFAULT NOW(),
    user_id INTEGER REFERENCES users(user_id),
    ip_address VARCHAR(45),
    details TEXT
) PARTITION BY RANGE (timestamp);
CREATE INDEX idx_logs_event_type ON logs(event_type);
CREATE INDEX idx_logs_timestamp ON logs(timestamp);
```

## Redis Key Patterns

| Key Pattern | Value Type | TTL | Example | Invalidation |
|-------------|-----------|-----|---------|-------------|
| `rate:{FROM}:{TO}` | String (JSON) | 5 min | `rate:USD:EUR` → `{"rate":0.92,"ts":"..."}` | Admin rate update, scheduled refresh |
| `rates:all` | String (JSON array) | 5 min | Full rate list | Any rate change |
| `token:blacklist:{jti}` | String ("revoked") | Matches token exp | Token revocation on logout |
| `ratelimit:{ip}` | String (counter) | 1 min | Bucket4j auto-manages |
| `mfa:otp:{userId}` | String (JSON) | 5 min | `mfa:otp:42` → `{"otp":"482916","attempts":0}` | Successful verification or expiry; max 3 attempts |

## Database Connection Config (application.yml)

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST}:5432/exchange_db
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      idle-timeout: 30000
      connection-timeout: 10000
  jpa:
    hibernate:
      ddl-auto: validate  # Flyway manages schema
    properties:
      hibernate:
        default_schema: public
  flyway:
    enabled: true
    locations: classpath:db/migration
```
