package org.rentez.paymentservice.web.dto;

import java.math.BigDecimal;

/**
 * Payment's slice of the admin report, answered locally.
 *
 * <p>The monolith's ReportService summed revenue by loading every Payment row
 * into memory and filtering in Java. This is a SUM in the database, over the
 * service that owns the data.
 */
public record PaymentStats(
		BigDecimal totalRevenue,
		long successfulPayments,
		long failedPayments,
		long refundedPayments,
		long unfinishedSagas) {
}
