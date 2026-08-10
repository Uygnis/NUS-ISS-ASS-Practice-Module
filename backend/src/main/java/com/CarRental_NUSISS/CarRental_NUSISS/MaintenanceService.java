package com.CarRental_NUSISS.CarRental_NUSISS;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.List;

@Service
public class MaintenanceService {

	private final MaintenanceRecordRepository maintenanceRepository;
	private final CarRepository carRepository;
	private final AuditService auditService;

	public MaintenanceService(MaintenanceRecordRepository maintenanceRepository, CarRepository carRepository,
			AuditService auditService) {
		this.maintenanceRepository = maintenanceRepository;
		this.carRepository = carRepository;
		this.auditService = auditService;
	}

	/** Admin schedules a job; the car is taken off the rentable list immediately. */
	public MaintenanceRecord schedule(MaintenanceRequest request, String actorEmail) {
		Car car = carRepository.findById(request.carId())
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No car with id " + request.carId()));

		car.setStatus(Car.CarStatus.MAINTENANCE);
		carRepository.save(car);

		MaintenanceRecord record = new MaintenanceRecord(car, request.description(), request.scheduledDate());
		MaintenanceRecord saved = maintenanceRepository.save(record);
		auditService.log(actorEmail, "SCHEDULE_MAINTENANCE", "MaintenanceRecord", saved.getId(),
				"Car #" + car.getId());
		return saved;
	}

	/** Staff (or admin) updates progress; completing a job puts the car back into AVAILABLE. */
	public MaintenanceRecord updateStatus(Long recordId, MaintenanceRecord.Status status, String actorEmail) {
		MaintenanceRecord record = maintenanceRepository.findById(recordId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No maintenance record with id " + recordId));

		record.setStatus(status);
		if (status == MaintenanceRecord.Status.COMPLETED) {
			record.setCompletedDate(LocalDate.now());
			Car car = record.getCar();
			car.setStatus(Car.CarStatus.AVAILABLE);
			carRepository.save(car);
		}
		MaintenanceRecord saved = maintenanceRepository.save(record);
		auditService.log(actorEmail, "UPDATE_MAINTENANCE_STATUS", "MaintenanceRecord", recordId, "New status: " + status);
		return saved;
	}

	public List<MaintenanceRecord> historyFor(Long carId) {
		return maintenanceRepository.findByCarIdOrderByScheduledDateDesc(carId);
	}
}
