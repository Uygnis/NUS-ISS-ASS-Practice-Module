package com.CarRental_NUSISS.CarRental_NUSISS;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** A reservation of one car by one customer over a date range. */
@Entity
@Table(name = "booking")
public class Booking {

	public enum BookingStatus { PENDING_PAYMENT, CONFIRMED, MODIFIED, CANCELLED, COMPLETED }

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(optional = false)
	@JoinColumn(name = "customer_id")
	private User customer;

	@ManyToOne(optional = false)
	@JoinColumn(name = "car_id")
	private Car car;

	@Column(nullable = false)
	private LocalDate startDate;

	@Column(nullable = false)
	private LocalDate endDate;

	private String pickupLocation;

	@Column(nullable = false)
	private BigDecimal totalAmount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private BookingStatus status = BookingStatus.PENDING_PAYMENT;

	@Column(nullable = false)
	private Instant createdAt = Instant.now();

	protected Booking() {
	}

	public Booking(User customer, Car car, LocalDate startDate, LocalDate endDate,
			String pickupLocation, BigDecimal totalAmount) {
		this.customer = customer;
		this.car = car;
		this.startDate = startDate;
		this.endDate = endDate;
		this.pickupLocation = pickupLocation;
		this.totalAmount = totalAmount;
	}

	public Long getId() { return id; }
	public User getCustomer() { return customer; }
	public Car getCar() { return car; }
	public void setCar(Car car) { this.car = car; }
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
