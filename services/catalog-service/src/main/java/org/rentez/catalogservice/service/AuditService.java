package org.rentez.catalogservice.service;

import org.rentez.catalogservice.domain.AuditLog;
import org.rentez.catalogservice.repository.AuditLogRepository;
import org.rentez.catalogservice.web.dto.AuditLogResponse;
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
	 * <p>This was {@code REQUIRES_NEW}, which takes a SECOND pooled connection
	 * while the caller's transaction still holds its first. With a pool of 3,
	 * three concurrent callers deadlock until Hikari's timeout - the pattern
	 * that collapsed reservation-service at 50 actions/s (docs/quality-attributes.md).
	 * It is fixed here too before this service meets the same load.
	 *
	 * <p>Joining loses nothing: every caller audits as the last step of an
	 * operation that commits, and none audits a failure before throwing. The
	 * audit row now commits with the change it describes.
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
