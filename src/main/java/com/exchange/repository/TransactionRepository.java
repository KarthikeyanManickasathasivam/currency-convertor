package com.exchange.repository;

import com.exchange.model.Transaction;
import com.exchange.model.enums.TransactionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Page<Transaction> findByUserUserIdOrderByTransactionDateDesc(UUID userId, Pageable pageable);

    Page<Transaction> findAllByOrderByTransactionDateDesc(Pageable pageable);

    List<Transaction> findByStatusOrderByTransactionDateDesc(TransactionStatus status);

    long countByStatus(TransactionStatus status);

    @Query("SELECT COUNT(t) FROM Transaction t")
    long countAll();

    @Query("""
            SELECT t.fromCurrency, COUNT(t) as cnt
            FROM Transaction t
            GROUP BY t.fromCurrency
            ORDER BY cnt DESC
            """)
    List<Object[]> findTopFromCurrencies(Pageable pageable);
}
