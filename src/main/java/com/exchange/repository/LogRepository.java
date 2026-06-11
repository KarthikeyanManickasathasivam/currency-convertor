package com.exchange.repository;

import com.exchange.model.Log;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.UUID;

@Repository
public interface LogRepository extends JpaRepository<Log, Long> {

    Page<Log> findAllByOrderByTimestampDesc(Pageable pageable);

    Page<Log> findByUserIdOrderByTimestampDesc(UUID userId, Pageable pageable);

    Page<Log> findByEventTypeOrderByTimestampDesc(String eventType, Pageable pageable);

    Page<Log> findByTimestampBetweenOrderByTimestampDesc(
            LocalDateTime from, LocalDateTime to, Pageable pageable);
}
