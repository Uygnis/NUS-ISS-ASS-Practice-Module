package com.CarRental_NUSISS.CarRental_NUSISS;

import jakarta.persistence.*;
import java.time.LocalDate;

/** A scheduled or completed maintenance job for one car. */
@Entity
@Table(name = "maintenance_record")
public class MaintenanceRecord {

	public enum Status { SCHEDULED, IN_PROGRESS, COMPLETED }

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(optional = false)
	@JoinColumn(name = "car_id")
	private Car car;

	@Column(nullable = false)
	private String description;

	@Column(nullable = false)
	private LocalDate scheduledDate;

	private LocalDate completedDate;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Status status = Status.SCHEDULED;

	protected MaintenanceRecord() {
	}

	public MaintenanceRecord(Car car, String description, LocalDate scheduledDate) {
		this.car = car;
		this.description = description;
		this.scheduledDate = scheduledDate;
	}

	public Long getId() { return id; }
	public Car getCar() { return car; }
	public String getDescription() { return description; }
	public LocalDate getScheduledDate() { return scheduledDate; }
	public LocalDate getCompletedDate() { return completedDate; }
	public void setCompletedDate(LocalDate completedDate) { this.completedDate = completedDate; }
	public Status getStatus() { return status; }
	public void setStatus(Status status) { this.status = status; }
}
