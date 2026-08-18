package org.rentez.notificationservice.web;

import org.rentez.notificationservice.security.CurrentUser;
import org.rentez.notificationservice.service.NotificationService;
import org.rentez.notificationservice.web.dto.NotificationResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * The recipient-facing API. Entirely new: the monolith wrote notifications and
 * never read them back, so {@code findByRecipientIdOrderBySentAtDesc} sat unused
 * and there was no controller at all. There was therefore no existing contract to
 * preserve here, only one to design.
 *
 * <p>Every endpoint is scoped to the caller's own id taken from the token, so
 * there is no way to ask for someone else's notifications.
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

	private final NotificationService notificationService;

	public NotificationController(NotificationService notificationService) {
		this.notificationService = notificationService;
	}

	@GetMapping("/me")
	public List<NotificationResponse> mine(@AuthenticationPrincipal Jwt jwt) {
		return notificationService.forRecipient(CurrentUser.from(jwt).id());
	}

	@GetMapping("/me/unread-count")
	public Map<String, Long> unreadCount(@AuthenticationPrincipal Jwt jwt) {
		return Map.of("unread", notificationService.unreadCount(CurrentUser.from(jwt).id()));
	}

	@PutMapping("/{id}/read")
	public NotificationResponse markRead(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		return notificationService.markRead(CurrentUser.from(jwt).id(), id);
	}
}
