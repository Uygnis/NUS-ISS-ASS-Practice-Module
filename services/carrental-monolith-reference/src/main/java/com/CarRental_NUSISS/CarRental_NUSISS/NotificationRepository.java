package com.CarRental_NUSISS.CarRental_NUSISS;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
	List<Notification> findByRecipientIdOrderBySentAtDesc(Long recipientId);
}
