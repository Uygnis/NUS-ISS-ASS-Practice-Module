package org.rentez.paymentservice.repository;

import org.rentez.paymentservice.domain.ConfirmState;
import org.rentez.paymentservice.domain.Payment;
import org.rentez.paymentservice.domain.PaymentStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

	Optional<Payment> findByIdempotencyKey(String idempotencyKey);

	/** The existing successful payment for a booking, if any. */
	Optional<Payment> findByBookingIdAndStatus(Long bookingId, PaymentStatus status);

	List<Payment> findByBookingIdOrderByCreatedAtDesc(Long bookingId);

	List<Payment> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

	/**
	 * The sweeper's query: money moved, saga unfinished.
	 *
	 * <p>These rows represent a real-world inconsistency - a customer charged
	 * whose booking is not confirmed, or refunded whose booking is not released.
	 * Nothing else looks for them, which is what makes this the difference between
	 * a saga and two hopeful writes.
	 *
	 * <p>The status filter has to span SUCCESS <em>and</em> REFUNDED, and getting
	 * that wrong is easy: compensation sets the status to REFUNDED before
	 * releasing the booking, so a failure at that last step leaves
	 * {@code REFUNDED / AWAITING_COMPENSATION}. Matching only on SUCCESS would
	 * skip exactly the rows most in need of attention - money already returned,
	 * booking still held - and they would never be retried.
	 *
	 * <p>Deliberately excluded: {@code INITIATED} rows, which were written
	 * ahead of the gateway call and whose outcome is unknown. Resolving those
	 * means asking the provider what happened to the charge, which the mock
	 * gateway cannot answer; they are left for manual reconciliation rather than
	 * being guessed at in either direction.
	 */
	@Query("""
			select p from Payment p
			where p.status in (
				org.rentez.paymentservice.domain.PaymentStatus.SUCCESS,
				org.rentez.paymentservice.domain.PaymentStatus.REFUNDED)
			and p.confirmState in :states
			order by p.createdAt asc
			""")
	List<Payment> findUnfinished(@Param("states") Collection<ConfirmState> states, Pageable pageable);

	/**
	 * Payments stranded before the gateway answered. Surfaced for visibility only -
	 * see {@link #findUnfinished} for why they are not swept automatically.
	 */
	long countByStatusAndConfirmState(PaymentStatus status, ConfirmState confirmState);

	@Query("""
			select coalesce(sum(p.amount), 0) from Payment p
			where p.status = org.rentez.paymentservice.domain.PaymentStatus.SUCCESS
			""")
	BigDecimal totalRevenue();

	long countByStatus(PaymentStatus status);
}
