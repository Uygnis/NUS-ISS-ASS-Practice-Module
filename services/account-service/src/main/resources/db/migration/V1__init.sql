-- rentez_auth - PRD module M1, Identity and Access.
--
-- Ported from the monolith's `app_user` and `audit_log` tables. Deliberately a
-- faithful port: BIGINT identifiers rather than the ULID public_id scheme in
-- docs/ch02, which is a later migration. What IS carried over from ch02 is the
-- rule that no foreign key may cross a schema boundary - nothing here points at
-- rentez_fleet, rentez_booking, rentez_payment or rentez_notification.
--
-- Hibernate runs with ddl-auto=validate against this file, so column types and
-- lengths must match the @Column declarations in domain/ exactly.

CREATE TABLE app_user (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    full_name     VARCHAR(150) NOT NULL,
    email         VARCHAR(255) NOT NULL,
    -- BCrypt output is 60 characters; 72 leaves room for a future algorithm
    -- prefix without a migration. The raw password is never stored.
    password_hash VARCHAR(72)  NOT NULL,
    phone         VARCHAR(20)  NULL,
    -- VARCHAR + CHECK rather than MySQL's ENUM type: adding a value to a MySQL
    -- ENUM is a table rebuild, and @Enumerated(STRING) already writes the name.
    role          VARCHAR(32)  NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_app_user_email UNIQUE (email),
    CONSTRAINT ck_app_user_role CHECK (role IN ('CUSTOMER', 'STAFF', 'ADMIN'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Login resolves by email and then checks `enabled`, so the unique index above
-- already covers the hot path. This one serves the admin user list.
CREATE INDEX ix_app_user_role_enabled ON app_user (role, enabled);

-- Append-only trail of who did what. The monolith's AuditLog was already free of
-- object references - it records the actor by email and the target as a
-- (entity_type, entity_id) pair - which is exactly why it splits per service
-- with no redesign. Each service writes audit rows to its OWN schema; there is
-- deliberately no cross-service audit table.
CREATE TABLE audit_log (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    -- Nullable: some actions are taken by the system rather than a person.
    actor_email VARCHAR(255) NULL,
    action      VARCHAR(64)  NOT NULL,
    entity_type VARCHAR(64)  NOT NULL,
    entity_id   BIGINT       NULL,
    details     VARCHAR(1000) NULL,
    -- Named occurred_at rather than `timestamp`: TIMESTAMP is also a MySQL type
    -- name, and this matches the audit naming in docs/ch02.
    occurred_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- The admin audit view is "most recent first", always.
CREATE INDEX ix_audit_log_occurred_at ON audit_log (occurred_at DESC);
