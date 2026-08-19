package org.rentez.reservationservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A reservation of one car by one customer over a date range.
 *
 * <p>This entity is where the monolith's coupling was worst and where the split
 * shows most clearly. It used to hold:
 *
 * <pre>
 *   &#64;ManyToOne(optional = false) &#64;JoinColumn(name = "customer_id") private User customer;
 *   &#64;ManyToOne(optional = false) &#64;JoinColumn(name = "car_id")      private Car  car;
 * </pre>
 *
 * <p>Two object references reaching into two other bounded contexts, both EAGER,
 * so loading any booking pulled a User and a Car with it. Those are now plain
 * identifiers, and the handful of fields other code actually read are copied
 * onto the row at creation time.
 *
 * <p>The snapshot is not redundancy for its own sake - it deletes two
 * cross-service reads outright:
 *
 * <ul>
 *   <li>{@code NotificationService} composed messages by walking
 *       {@code booking.getCar().getMake()} and {@code booking.getCustomer()};
 *       it now receives flat values and never dereferences anything.</li>
 *   <li>{@code ReportService} grouped bookings by
 *       {@code booking.getCar().getType()}; reservation can now answer that with
 *       a local {@code GROUP BY}.</li>
 * </ul>
 *
 * <p>It also makes a booking self-contained: it still renders, and can still
 * explain its own total, after the car is repriced, retired or deleted.
 */
@Entity
@Table(name = "booking")
public class Booking {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** rentez_auth.app_user.id - an identifier, never a reference. */
	@Column(name = "customer_id", nullable = false)
	private Long customerId;

	@Column(name = "customer_email", nullable = false, length = 255)
	private String customerEmail;

	/** rentez_fleet.car.id - an identifier, never a reference. */
	@Column(name = "car_id", nullable = false)
	private Long carId;

	@Column(name = "car_make", nullable = false, length = 80)
	private String carMake;

	@Column(name = "car_model", nullable = false, length = 80)
	private String carModel;

	/** String, not an enum mirrored from catalog - see V1__init.sql. */
	@Column(name = "car_type", nullable = false, length = 32)
	private String carType;

	/** The rate quoted at booking time, frozen. */
	@Column(name = "daily_rate_snapshot", nullable = false, precision = 10, scale = 2)
	private BigDecimal dailyRateSnapshot;

	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;

	@Column(name = "end_date", nullable = false)
	private LocalDate endDate;

	@Column(name = "pickup_location", length = 120)
	private String pickupLocation;

	@Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
	private BigDecimal totalAmount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private BookingStatus status = BookingStatus.PENDING_PAYMENT;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();

	protected Booking() {
	}

	public Booking(Long customerId, String customerEmail, Long carId, String carMake, String carModel,
			String carType, BigDecimal dailyRateSnapshot, LocalDate startDate, LocalDate endDate,
			String pickupLocation, BigDecimal totalAmount) {
		this.customerId = customerId;
		this.customerEmail = customerEmail;
		this.carId = carId;
		this.carMake = carMake;
		this.carModel = carModel;
		this.carType = carType;
		this.dailyRateSnapshot = dailyRateSnapshot;
		this.startDate = startDate;
		this.endDate = endDate;
		this.pickupLocation = pickupLocation;
		this.totalAmount = totalAmount;
	}

	/**
	 * Replaces {@code booking.getCustomer().getId().equals(customer.getId())}.
	 *
	 * <p>The monolith compared two loaded entities, which only worked because the
	 * caller had already fetched the User. This compares the caller's id straight
	 * from the JWT - cheaper, and with no account-service involved.
	 */
	public boolean isOwnedBy(Long userId) {
		return customerId.equals(userId);
	}

	public Long getId() { return id; }
	public Long getCustomerId() { return customerId; }
	public String getCustomerEmail() { return customerEmail; }
	public Long getCarId() { return carId; }
	public String getCarMake() { return carMake; }
	public String getCarModel() { return carModel; }
	public String getCarType() { return carType; }
	public BigDecimal getDailyRateSnapshot() { return dailyRateSnapshot; }
	public LocalDate getStartDate() { return startDate; }
	public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
	public LocalDate getEndDate() { return endDate; }
	public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
	public String getPickupLocation() { return pickupLocation; }
	public void setPickupLocation(String pickupLocation) { this.pickupLocation = pickupLocation; }
	public BigDecimal getTotalAmount() { return totalAmount; }
	public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
	public BookingStatus getStatus() { return status; }
	public void setStatus(BookingStatus status) { this.status = status; }
	public Instant getCreatedAt() { return createdAt; }
}
