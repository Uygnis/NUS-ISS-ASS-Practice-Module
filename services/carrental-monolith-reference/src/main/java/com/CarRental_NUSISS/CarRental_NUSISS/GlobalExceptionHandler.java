package com.CarRental_NUSISS.CarRental_NUSISS;

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

/** Turns exceptions into a consistent {@code {status, error, message}} JSON body. */
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
