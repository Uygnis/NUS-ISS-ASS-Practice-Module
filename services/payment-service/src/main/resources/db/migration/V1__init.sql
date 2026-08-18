-- rentez_payment - PRD module M4, Payment and Refund.
--
-- The schema, the payment_user account and PAYMENT_DB_PASSWORD were provisioned
-- in db/init/01-schemas.sql long before this service existed. Ported from the
-- monolith's `payment` table, with Payment.booking (@ManyToOne) replaced by a
-- plain booking_id - no foreign key leaves this schema.

CREATE TABLE payment (
    id                   BIGINT         NOT NULL AUTO_INCREMENT,

    -- rentez_booking.booking.id. An identifier, never a reference.
    booking_id           BIGINT         NOT NULL,
    -- Snapshot, so a receipt still identifies its customer without asking
    -- account-service or reservation-service anything.
    customer_id          BIGINT         NOT NULL,
    customer_email       VARCHAR(255)   NOT NULL,

    -- Client-supplied via the Idempotency-Key header, or generated when absent.
    -- A retry of the same request returns the original payment instead of
    -- charging twice. NOT derived from booking_id: a legitimate retry after a
    -- declined attempt has to be allowed to create a new payment.
    idempotency_key      VARCHAR(64)    NOT NULL,

    amount               DECIMAL(12, 2) NOT NULL,
    currency             CHAR(3)        NOT NULL DEFAULT 'SGD',
    method               VARCHAR(16)    NOT NULL,

    -- INITIATED exists because the row is written BEFORE the gateway is called.
    -- The monolith saved only after the gateway returned, so a crash in between
    -- meant the money moved with nothing recorded anywhere. Ported as-is into a
    -- service that can be killed mid-request, that is a real hole.
    status               VARCHAR(16)    NOT NULL DEFAULT 'INITIATED',

    -- Where the saga got to, tracked separately from the money. A payment can be
    -- SUCCESS while the booking is not yet confirmed - that is precisely the
    -- window the sweeper exists to close.
    confirm_state        VARCHAR(24)    NOT NULL DEFAULT 'PENDING',

    transaction_ref      VARCHAR(64)    NULL,
    failure_reason       VARCHAR(255)   NULL,
    attempt_count        INT            NOT NULL DEFAULT 0,
    last_error           VARCHAR(1000)  NULL,

    created_at           DATETIME(6)    NOT NULL,
    updated_at           DATETIME(6)    NOT NULL,

    -- The second guarantee, and the one that survives a client that forgets to
    -- send an idempotency key. MySQL has no partial unique index, so this stored
    -- generated column is NULL for every row that is not SUCCESS - and NULLs do
    -- not collide in a unique index. Any number of FAILED attempts are therefore
    -- fine, while a second SUCCESS for the same booking is rejected by the
    -- database rather than by application logic that could race with itself.
    succeeded_booking_id BIGINT AS (CASE WHEN status = 'SUCCESS' THEN booking_id END) STORED,

    PRIMARY KEY (id),
    CONSTRAINT uk_payment_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT uk_payment_transaction_ref UNIQUE (transaction_ref),
    CONSTRAINT uk_payment_success_booking UNIQUE (succeeded_booking_id),
    CONSTRAINT ck_payment_status CHECK (status IN ('INITIATED', 'SUCCESS', 'FAILED', 'REFUNDED')),
    CONSTRAINT ck_payment_confirm_state CHECK (confirm_state IN
        ('PENDING', 'CONFIRMED', 'COMPENSATED', 'AWAITING_COMPENSATION', 'NOT_APPLICABLE'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_payment_booking ON payment (booking_id);
CREATE INDEX ix_payment_customer_created ON payment (customer_id, created_at DESC);
-- The reconciliation sweeper's query: money taken, saga unfinished.
CREATE INDEX ix_payment_status_confirm_state ON payment (status, confirm_state);

-- Same transactional outbox as reservation. Payment receipts and refund notices
-- are committed with the payment and delivered afterwards, so notification is
-- never on the critical path of taking money.
CREATE TABLE outbox_event (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    event_id      CHAR(36)      NOT NULL,
    event_type    VARCHAR(64)   NOT NULL,
    payload       JSON          NOT NULL,
    status        VARCHAR(16)   NOT NULL DEFAULT 'PENDING',
    attempt_count INT           NOT NULL DEFAULT 0,
    last_error    VARCHAR(1000) NULL,
    created_at    DATETIME(6)   NOT NULL,
    dispatched_at DATETIME(6)   NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_outbox_event_id UNIQUE (event_id),
    CONSTRAINT ck_outbox_status CHECK (status IN ('PENDING', 'DISPATCHED', 'FAILED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_outbox_status_created ON outbox_event (status, created_at);

CREATE TABLE audit_log (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    actor_email VARCHAR(255)  NULL,
    action      VARCHAR(64)   NOT NULL,
    entity_type VARCHAR(64)   NOT NULL,
    entity_id   BIGINT        NULL,
    details     VARCHAR(1000) NULL,
    occurred_at DATETIME(6)   NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX ix_audit_log_occurred_at ON audit_log (occurred_at DESC);
