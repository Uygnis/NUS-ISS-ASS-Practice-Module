package org.rentez.accountservice.client;

import java.math.BigDecimal;

/** Payment's slice, from {@code GET /api/payments/internal/stats}. */
public record PaymentStats(
		BigDecimal totalRevenue,
		long successfulPayments,
		long failedPayments,
		long refundedPayments,
		long unfinishedSagas) {
}
