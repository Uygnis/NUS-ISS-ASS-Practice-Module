package org.rentez.accountservice.service;

import org.rentez.accountservice.domain.AuditLog;
import org.rentez.accountservice.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Writes an audit trail entry for a security- or business-relevant action. */
@Service
public class AuditService {

	private final AuditLogRepository auditLogRepository;

	public AuditService(AuditLogRepository auditLogRepository) {
		this.auditLogRepository = auditLogRepository;
	}

	/**
	 * Runs in its own transaction, on purpose.
	 *
	 * <p>The monolith had no transactions at all, so an audit row committed
	 * independently of the operation that triggered it. Now that the callers are
	 * {@code @Transactional}, a plain call would join their transaction and be
	 * rolled back with them - which would silently erase the trail for exactly
	 * the failures worth auditing. {@code REQUIRES_NEW} preserves the old
	 * behaviour deliberately rather than by omission.
	 *
	 * <p>The same reasoning applies with more force in payment-service, where a
	 * declined payment is persisted and audited and only then throws.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void log(String actorEmail, String action, String entityType, Long entityId, String details) {
		auditLogRepository.save(new AuditLog(actorEmail, action, entityType, entityId, details));
	}
}
