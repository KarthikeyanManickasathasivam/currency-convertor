package com.exchange.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Log {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id", nullable = false, updatable = false)
    private Long logId;

    @Column(name = "event", nullable = false, length = 100)
    private String event;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "timestamp", nullable = false, updatable = false)
    private LocalDateTime timestamp;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    // columnDefinition omitted so Hibernate uses its default JSON mapping (works with both H2 in tests and PostgreSQL jsonb in prod)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details")
    private Map<String, Object> details;

    @PrePersist
    protected void onCreate() {
        timestamp = LocalDateTime.now();
    }
}
