package org.rentez.catalogservice.web.dto;

/**
 * Catalog's slice of the admin dashboard, answered from its own tables.
 *
 * <p>The monolith's {@code ReportService} injected three repositories and did all
 * the aggregation in one process. Rather than recreate that by having the
 * reporting composer fan out and re-aggregate, each service answers the part it
 * owns with a local query and the composer just stitches the slices together.
 */
public record CatalogStats(long totalCars, long availableCars, long inMaintenanceCars) {
}
