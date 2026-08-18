package org.rentez.paymentservice.domain;

/**
 * What happened to the money.
 *
 * <p>{@code INITIATED} is new. The monolith constructed and saved a Payment only
 * after the gateway had answered, so the window between "card charged" and "row
 * written" had no record at all - in-process that window is microseconds and
 * survives anything short of a JVM kill, but a service can be evicted,
 * OOM-killed or redeployed inside it. The row is now written first and updated
 * with the outcome.
 */
public enum PaymentStatus {
	INITIATED,
	SUCCESS,
	FAILED,
	REFUNDED
}
