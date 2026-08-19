package org.rentez.catalogservice.repository;

import org.rentez.catalogservice.domain.AuditLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

	List<AuditLog> findAllByOrderByOccurredAtDesc(Pageable pageable);
}
