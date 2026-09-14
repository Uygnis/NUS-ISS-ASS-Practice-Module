package com.CarRental_NUSISS.CarRental_NUSISS;

import org.springframework.stereotype.Service;

/** Writes an audit trail entry for a security-relevant or business-relevant action. */
@Service
public class AuditService {

	private final AuditLogRepository auditLogRepository;

	public AuditService(AuditLogRepository auditLogRepository) {
		this.auditLogRepository = auditLogRepository;
	}

	public void log(String actorEmail, String action, String entityType, Long entityId, String details) {
		auditLogRepository.save(new AuditLog(actorEmail, action, entityType, entityId, details));
	}
}
