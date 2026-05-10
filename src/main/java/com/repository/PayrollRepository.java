package com.repository;

import com.model.PayrollRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PayrollRepository extends JpaRepository<PayrollRecord, Long> {
    Optional<PayrollRecord> findByCorrelationId(String correlationId);
}