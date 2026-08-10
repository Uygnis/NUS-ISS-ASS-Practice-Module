package com.CarRental_NUSISS.CarRental_NUSISS;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
	List<AuditLog> findAllByOrderByTimestampDesc(Pageable pageable);
}
