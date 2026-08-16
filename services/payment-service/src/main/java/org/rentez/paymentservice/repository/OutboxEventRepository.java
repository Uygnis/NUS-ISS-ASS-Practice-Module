package org.rentez.paymentservice.repository;

import org.rentez.paymentservice.domain.OutboxEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

	List<OutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxEvent.Status status, Pageable pageable);

	long countByStatus(OutboxEvent.Status status);
}
