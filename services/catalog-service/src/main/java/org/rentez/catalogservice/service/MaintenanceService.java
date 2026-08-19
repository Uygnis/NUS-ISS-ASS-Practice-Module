package org.rentez.catalogservice.service;

import org.rentez.catalogservice.domain.Car;
import org.rentez.catalogservice.domain.CarStatus;
import org.rentez.catalogservice.domain.MaintenanceRecord;
import org.rentez.catalogservice.domain.MaintenanceStatus;
import org.rentez.catalogservice.error.ApiException;
import org.rentez.catalogservice.repository.CarRepository;
import org.rentez.catalogservice.repository.MaintenanceRecordRepository;
import org.rentez.catalogservice.web.dto.MaintenanceRequest;
import org.rentez.catalogservice.web.dto.MaintenanceResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Scheduling and progressing maintenance jobs.
 *
 * <p>Ported essentially as written, because it never crossed a boundary: it
 * mutates {@code car.status} and writes {@code maintenance_record}, both owned by
 * this service. That is precisely why maintenance stayed inside catalog rather
 * than becoming a sixth service - splitting it would turn one local transaction
 * into a distributed one for no gain.
 *
 * <p>What did change is that both methods are now genuinely atomic. The monolith
 * had no {@code @Transactional} anywhere, so {@code schedule} could mark a car
 * MAINTENANCE and then fail to write the record that would ever bring it back -
 * leaving a car permanently unrentable with nothing to explain why.
 */
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

	/** Admin schedules a job; the car leaves the rentable pool immediately. */
	@Transactional
	public MaintenanceResponse schedule(MaintenanceRequest request, String actorEmail) {
		Car car = carRepository.findById(request.carId())
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No car with id " + request.carId()));

		car.setStatus(CarStatus.MAINTENANCE);
		carRepository.save(car);

		MaintenanceRecord saved = maintenanceRepository.save(
				new MaintenanceRecord(car, request.description(), request.scheduledDate()));

		auditService.log(actorEmail, "SCHEDULE_MAINTENANCE", "MaintenanceRecord", saved.getId(),
				"Car #" + car.getId());
		return MaintenanceResponse.from(saved);
	}

	/** Staff or admin updates progress; completing a job returns the car to AVAILABLE. */
	@Transactional
	public MaintenanceResponse updateStatus(Long recordId, MaintenanceStatus status, String actorEmail) {
		MaintenanceRecord record = maintenanceRepository.findById(recordId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No maintenance record with id " + recordId));

		record.setStatus(status);
		if (status == MaintenanceStatus.COMPLETED) {
			record.setCompletedDate(LocalDate.now());
			Car car = record.getCar();
			car.setStatus(CarStatus.AVAILABLE);
			carRepository.save(car);
		}
		MaintenanceRecord saved = maintenanceRepository.save(record);

		auditService.log(actorEmail, "UPDATE_MAINTENANCE_STATUS", "MaintenanceRecord", recordId,
				"New status: " + status);
		return MaintenanceResponse.from(saved);
	}

	@Transactional(readOnly = true)
	public List<MaintenanceResponse> historyFor(Long carId) {
		return maintenanceRepository.findByCarIdOrderByScheduledDateDesc(carId).stream()
				.map(MaintenanceResponse::from)
				.toList();
	}
}
