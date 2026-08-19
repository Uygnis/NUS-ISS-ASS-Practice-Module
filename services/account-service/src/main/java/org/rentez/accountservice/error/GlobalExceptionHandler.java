package org.rentez.accountservice.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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

	@ExceptionHandler(ApiException.class)
	public ResponseEntity<Object> handleApi(ApiException ex) {
		return body(ex.getStatus(), ex.getMessage());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Object> handleValidation(MethodArgumentNotValidException ex) {
		String message = ex.getBindingResult().getFieldErrors().stream()
				.map(e -> e.getField() + ": " + e.getDefaultMessage())
				.reduce((a, b) -> a + "; " + b)
				.orElse("Validation failed");
		return body(HttpStatus.BAD_REQUEST, message);
	}

	@ExceptionHandler(BadCredentialsException.class)
	public ResponseEntity<Object> handleBadCredentials(BadCredentialsException ex) {
		return body(HttpStatus.UNAUTHORIZED, "Invalid email or password");
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<Object> handleAccessDenied(AccessDeniedException ex) {
		return body(HttpStatus.FORBIDDEN, "You do not have permission to do that");
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
