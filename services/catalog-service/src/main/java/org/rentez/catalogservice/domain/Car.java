package org.rentez.catalogservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * One car in the fleet.
 *
 * <p>Like {@code User} in account-service, this entity has no outbound
 * relationships, which is what made catalog the second-easiest domain to
 * extract. Bookings used to hold a {@code @ManyToOne Car}; they now store a
 * {@code car_id} plus a snapshot of make, model, type and daily rate, so no row
 * outside this schema points at one of these.
 */
@Entity
@Table(name = "car")
public class Car {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 80)
	private String make;

	@Column(nullable = false, length = 80)
	private String model;

	/**
	 * Mapped to model_year. {@code year} is reserved in H2 2.x - where this DDL
	 * first failed - and is a keyword and type name in MySQL besides.
	 */
	@Column(name = "model_year", nullable = false)
	private int year;

	@Column(name = "daily_rate", nullable = false, precision = 10, scale = 2)
	private BigDecimal dailyRate;

	@Column(nullable = false, length = 120)
	private String location;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private CarType type;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
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
