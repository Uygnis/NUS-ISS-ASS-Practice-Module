package com.CarRental_NUSISS.CarRental_NUSISS;

import jakarta.persistence.*;
import java.math.BigDecimal;

/**
 * One car in the fleet. Replaces the earlier hard-coded record: this is now a
 * managed JPA entity so admins/staff can create, edit and retire vehicles.
 */
@Entity
@Table(name = "car")
public class Car {

	public enum CarType { SEDAN, SUV, HATCHBACK, TRUCK, ELECTRIC, LUXURY }

	/** Whether the car can currently be booked. MAINTENANCE and RETIRED both block booking. */
	public enum CarStatus { AVAILABLE, RENTED, MAINTENANCE, RETIRED }

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String make;

	@Column(nullable = false)
	private String model;

	/** Mapped to model_year: "year" is a reserved keyword in H2 2.x, so the plain name fails DDL. */
	@Column(name = "model_year", nullable = false)
	private int year;

	@Column(nullable = false)
	private BigDecimal dailyRate;

	@Column(nullable = false)
	private String location;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private CarType type;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private CarStatus status = CarStatus.AVAILABLE;

	protected Car() {
	}

	public Car(String make, String model, int year, BigDecimal dailyRate, String location, CarType type) {
		this.make = make;
		this.model = model;
		this.year = year;
		this.dailyRate = dailyRate;
		this.location = location;
		this.type = type;
	}

	public Long getId() { return id; }
	public String getMake() { return make; }
	public void setMake(String make) { this.make = make; }
	public String getModel() { return model; }
	public void setModel(String model) { this.model = model; }
	public int getYear() { return year; }
	public void setYear(int year) { this.year = year; }
	public BigDecimal getDailyRate() { return dailyRate; }
	public void setDailyRate(BigDecimal dailyRate) { this.dailyRate = dailyRate; }
	public String getLocation() { return location; }
	public void setLocation(String location) { this.location = location; }
	public CarType getType() { return type; }
	public void setType(CarType type) { this.type = type; }
	public CarStatus getStatus() { return status; }
	public void setStatus(CarStatus status) { this.status = status; }
}
