package org.rentez.catalogservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * A scheduled or completed maintenance job for one car.
 *
 * <p>This is the one {@code @ManyToOne} from the monolith that survives intact.
 * Of the five object references in the original model, three crossed a future
 * service boundary and had to become raw identifiers; this one and
 * {@code Payment -> Booking} point at a table in the same schema, so they stay
 * real associations with a real foreign key. docs/ch01 is explicit that foreign
 * keys are forbidden across schemas and mandatory within one.
 *
 * <p>Fetching is LAZY here, unlike the monolith where every {@code @ManyToOne}
 * defaulted to EAGER and loading a record always dragged its car along.
 */
@Entity
@Table(name = "maintenance_record")
public class MaintenanceRecord {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "car_id", nullable = false)
	private Car car;

	@Column(nullable = false, length = 500)
	private String description;

	@Column(name = "scheduled_date", nullable = false)
	private LocalDate scheduledDate;

	@Column(name = "completed_date")
	private LocalDate completedDate;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private MaintenanceStatus status = MaintenanceStatus.SCHEDULED;

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
	public MaintenanceStatus getStatus() { return status; }
	public void setStatus(MaintenanceStatus status) { this.status = status; }
}
