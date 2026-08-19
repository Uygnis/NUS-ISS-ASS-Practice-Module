package org.rentez.accountservice.domain;

/**
 * A user holds exactly one role - assignment is exclusive, not additive.
 * ADMIN is a strict superset of STAFF.
 *
 * <p>Promoted from a nested {@code User.Role} to a top-level type because it is
 * also part of the JWT contract: the issued token carries the name of this enum
 * in its {@code role} claim, and every other service maps that claim to a
 * {@code ROLE_*} authority without ever calling back here. Renaming a constant
 * is therefore a breaking change across all five services, not a local edit.
 */
public enum Role {
	CUSTOMER,
	STAFF,
	ADMIN
}
