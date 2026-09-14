package com.CarRental_NUSISS.CarRental_NUSISS;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** Reports/analytics and the audit trail - both admin-only. */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

	private final ReportService reportService;
	private final AuditLogRepository auditLogRepository;

	public AdminController(ReportService reportService, AuditLogRepository auditLogRepository) {
		this.reportService = reportService;
		this.auditLogRepository = auditLogRepository;
	}

	@GetMapping("/reports/summary")
	public ReportSummary summary() {
		return reportService.summary();
	}

	@GetMapping("/audit-log")
	public List<AuditLog> auditLog(@RequestParam(defaultValue = "100") int limit) {
		return auditLogRepository.findAllByOrderByTimestampDesc(PageRequest.of(0, Math.min(limit, 500)));
	}
}
