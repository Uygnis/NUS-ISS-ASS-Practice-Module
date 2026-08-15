package org.rentez.notificationservice.web;

import jakarta.validation.Valid;
import org.rentez.notificationservice.service.NotificationService;
import org.rentez.notificationservice.web.dto.IngestResult;
import org.rentez.notificationservice.web.dto.NotificationEventRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Where the other services' outbox relays deliver.
 *
 * <p>Returns <strong>200 for a redelivery</strong> rather than 409. The relay
 * retries whenever a response is lost, so duplicates are expected traffic, not an
 * error condition - answering 409 would leave the producer marking correctly
 * delivered events as failed and retrying them forever.
 */
@RestController
@RequestMapping("/api/notifications/internal")
@PreAuthorize("hasRole('SERVICE')")
public class InternalNotificationController {

	private final NotificationService notificationService;

	public InternalNotificationController(NotificationService notificationService) {
		this.notificationService = notificationService;
	}

	@PostMapping("/events")
	public IngestResult ingest(@Valid @RequestBody NotificationEventRequest request) {
		return notificationService.ingest(request);
	}
}
