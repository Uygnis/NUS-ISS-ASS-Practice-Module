package org.rentez.paymentservice.repository;

import org.rentez.paymentservice.domain.AuditLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

	List<AuditLog> findAllByOrderByOccurredAtDesc(Pageable pageable);
}
