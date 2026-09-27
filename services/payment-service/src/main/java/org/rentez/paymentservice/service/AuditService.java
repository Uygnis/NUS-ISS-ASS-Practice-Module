package org.rentez.paymentservice.service;

import org.rentez.paymentservice.domain.AuditLog;
import org.rentez.paymentservice.repository.AuditLogRepository;
import org.rentez.paymentservice.web.dto.AuditLogResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Writes an audit trail entry for a security- or business-relevant action, and reads this service's own trail back. */
@Service
public class AuditService {

	private static final int MAX_AUDIT_PAGE = 500;

	private final AuditLogRepository auditLogRepository;

	public AuditService(AuditLogRepository auditLogRepository) {
		this.auditLogRepository = auditLogRepository;
	}

	/**
	 * Joins the caller's transaction (or opens one if there is none).
	 *
	 * <p>This was {@code REQUIRES_NEW}, so an audit row would survive its caller
	 * rolling back. The cost was a SECOND pooled connection while the caller's
	 * transaction still held its first - and with a pool of 3, three concurrent
	 * bookings could each hold one connection and wait for another. Nothing then
	 * moved until Hikari's timeout: the load test collapsed at 50 actions/s with
	 * CPU under 1% (see docs/quality-attributes.md).
	 *
	 * <p>Joining loses nothing here. Every caller audits as the last step of a
	 * transaction that commits - including the declined-payment path, whose
	 * transaction commits before the caller throws - so the audit row commits
	 * with the change it describes, and a change that rolls back is not audited
	 * as though it had happened.
	 */
	@Transactional
	public void log(String actorEmail, String action, String entityType, Long entityId, String details) {
		auditLogRepository.save(new AuditLog(actorEmail, action, entityType, entityId, details));
	}

	/** Most recent first, capped so an admin cannot ask for the whole table. */
	@Transactional(readOnly = true)
	public List<AuditLogResponse> recent(int limit) {
		return auditLogRepository
				.findAllByOrderByOccurredAtDesc(PageRequest.of(0, Math.min(Math.max(limit, 1), MAX_AUDIT_PAGE)))
				.stream()
				.map(AuditLogResponse::from)
				.toList();
	}
}
