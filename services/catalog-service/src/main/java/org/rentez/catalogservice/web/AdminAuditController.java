package org.rentez.catalogservice.web;

import org.rentez.catalogservice.service.AuditService;
import org.rentez.catalogservice.web.dto.AuditLogResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * This service's own audit trail. Each service exposes its own; there is
 * deliberately no global feed - see {@link AuditLogResponse}.
 */
@RestController
@RequestMapping("/api/catalog/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAuditController {

	private final AuditService auditService;

	public AdminAuditController(AuditService auditService) {
		this.auditService = auditService;
	}

	@GetMapping("/audit-log")
	public List<AuditLogResponse> auditLog(@RequestParam(defaultValue = "100") int limit) {
		return auditService.recent(limit);
	}
}
