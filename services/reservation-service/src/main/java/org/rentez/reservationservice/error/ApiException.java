package org.rentez.reservationservice.error;

import org.springframework.http.HttpStatus;

/** Thrown by services for expected failure cases (not found, conflict, bad state, ...). */
public class ApiException extends RuntimeException {

	private final HttpStatus status;

	public ApiException(HttpStatus status, String message) {
		super(message);
		this.status = status;
	}

	public HttpStatus getStatus() {
		return status;
	}
}
