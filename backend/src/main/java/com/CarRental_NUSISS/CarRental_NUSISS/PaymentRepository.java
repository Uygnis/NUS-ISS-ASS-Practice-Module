package com.CarRental_NUSISS.CarRental_NUSISS;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
	List<Payment> findByBookingId(Long bookingId);
	Optional<Payment> findFirstByBookingIdAndStatusOrderByCreatedAtDesc(Long bookingId, Payment.Status status);
}
