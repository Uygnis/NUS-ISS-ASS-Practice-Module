package org.rentez.catalogservice.repository;

import org.rentez.catalogservice.domain.MaintenanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MaintenanceRecordRepository extends JpaRepository<MaintenanceRecord, Long> {

	List<MaintenanceRecord> findByCarIdOrderByScheduledDateDesc(Long carId);
}
