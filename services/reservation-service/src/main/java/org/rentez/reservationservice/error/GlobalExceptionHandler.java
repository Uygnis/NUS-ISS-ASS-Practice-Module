package org.rentez.reservationservice.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns exceptions into a consistent {@code {timestamp, status, error, message}}
 * body, so the shape of a failure does not depend on which service produced it.
 *
 * <p>Copied per service rather than shared through a library. A shared module
 * would have to be visible inside each service's Docker build context, which is
 * scoped to its own directory and runs {@code mvn dependency:go-offline} against
 * Maven Central - so an {@code org.rentez} sibling would break every image
 * build. Forty lines duplicated five times is the cheaper trade.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(ApiException.class)
	public ResponseEntity<Object> handleApi(ApiException ex) {
		// 4xx is the caller's mistake and expected traffic: one WARN line, no
		// stack. 5xx means a dependency or this service failed - keep the stack.
		if (ex.getStatus().is5xxServerError()) {
			log.error("{} {}", ex.getStatus().value(), ex.getMessage(), ex);
		}
		else {
			log.warn("{} {}", ex.getStatus().value(), ex.getMessage());
		}
		return body(ex.getStatus(), ex.getMessage());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Object> handleValidation(MethodArgumentNotValidException ex) {
		// One readable sentence per field ("Full name is required.") instead of
		// "fullName: must not be blank". fieldErrors lets the UI mark the input.
		Map<String, String> fieldErrors = new LinkedHashMap<>();
		ex.getBindingResult().getFieldErrors()
				.forEach(e -> fieldErrors.putIfAbsent(e.getField(), readable(e.getField(), e.getDefaultMessage())));
		String message = fieldErrors.isEmpty()
				? "Some of the details are not valid. Please check the form and try again."
				: String.join(" ", fieldErrors.values());
		log.warn("400 validation failed: {}", fieldErrors.keySet());
		ResponseEntity<Object> response = body(HttpStatus.BAD_REQUEST, message);
		@SuppressWarnings("unchecked")
		Map<String, Object> map = (Map<String, Object>) response.getBody();
		map.put("fieldErrors", fieldErrors);
		return response;
	}

	/** "fullName" + "must not be blank" -> "Full name is required." */
	static String readable(String field, String defaultMessage) {
		String msg = defaultMessage == null ? "is not valid" : defaultMessage;
		// A message written for people (starts upper-case) is used as-is.
		if (!msg.isEmpty() && Character.isUpperCase(msg.charAt(0))) {
			return msg.endsWith(".") ? msg : msg + ".";
		}
		String label = field.replaceAll("Id$", "").replaceAll("([a-z])([A-Z])", "$1 $2").toLowerCase();
		label = Character.toUpperCase(label.charAt(0)) + label.substring(1);
		if (msg.startsWith("must not be blank") || msg.startsWith("must not be null") || msg.startsWith("must not be empty")) {
			return label + " is required.";
		}
		if (msg.contains("well-formed email")) {
			return "Please enter a valid email address.";
		}
		if (msg.startsWith("size must be between 0 and ")) {
			return label + " must be at most " + msg.substring("size must be between 0 and ".length()) + " characters.";
		}
		if (msg.startsWith("must be greater than 0")) {
			return label + " must be more than zero.";
		}
		return label + " " + msg + ".";
	}

	@ExceptionHandler(BadCredentialsException.class)
	public ResponseEntity<Object> handleBadCredentials(BadCredentialsException ex) {
		log.warn("401 bad credentials");
		return body(HttpStatus.UNAUTHORIZED, "Invalid email or password");
	}

	@ExceptionHandler(AuthenticationException.class)
	public ResponseEntity<Object> handleUnauthenticated(AuthenticationException ex) {
		log.warn("401 {}", ex.getMessage());
		return body(HttpStatus.UNAUTHORIZED, "Your session has expired. Please sign in again.");
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<Object> handleAccessDenied(AccessDeniedException ex) {
		log.warn("403 {}", ex.getMessage());
		return body(HttpStatus.FORBIDDEN, "You do not have permission to do that");
	}

	@ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
	public ResponseEntity<Object> handleMalformed(Exception ex) {
		log.warn("400 malformed request: {}", ex.getMessage());
		return body(HttpStatus.BAD_REQUEST, "The request was not in the expected format. Please check the values and try again.");
	}

	/**
	 * Last resort. Without it an unexpected exception fell through to Spring's
	 * default body, the UI showed "Request failed (500)" and nothing useful
	 * reached the logs. Spring's own exceptions (404 no handler, 405, ...)
	 * carry their status and keep it.
	 */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<Object> handleUnexpected(Exception ex) {
		if (ex instanceof ErrorResponse er) {
			HttpStatusCode code = er.getStatusCode();
			log.warn("{} {}", code.value(), ex.getMessage());
			HttpStatus status = HttpStatus.resolve(code.value());
			if (status == null) {
				status = HttpStatus.INTERNAL_SERVER_ERROR;
			}
			// Spring's own text ("No static resource ...") is for the log, not the user.
			String message = switch (status) {
				case NOT_FOUND -> "We couldn't find what you were looking for.";
				case METHOD_NOT_ALLOWED -> "That action isn't supported here.";
				case UNSUPPORTED_MEDIA_TYPE -> "The request was not in the expected format. Please check the values and try again.";
				default -> status.getReasonPhrase();
			};
			return body(status, message);
		}
		log.error("500 unhandled {}", ex.getClass().getName(), ex);
		return body(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong on our side. Please try again in a moment.");
	}

	private ResponseEntity<Object> body(HttpStatus status, String message) {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("timestamp", Instant.now().toString());
		map.put("status", status.value());
		map.put("error", status.getReasonPhrase());
		map.put("message", message);
		return ResponseEntity.status(status).body(map);
	}
}
