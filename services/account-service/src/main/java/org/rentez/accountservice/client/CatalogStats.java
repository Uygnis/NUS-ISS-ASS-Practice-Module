package org.rentez.accountservice.client;

/** Catalog's slice, from {@code GET /api/catalog/internal/stats}. */
public record CatalogStats(long totalCars, long availableCars, long inMaintenanceCars) {
}
