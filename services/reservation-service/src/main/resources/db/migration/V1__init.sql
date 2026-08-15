-- rentez_booking - PRD module M3, Booking.
--
-- Ported from the monolith's `booking` table. Two structural changes matter:
--
-- 1. Booking held @ManyToOne references to User and Car, i.e. object references
--    into two other bounded contexts. Both become plain identifiers plus a
--    snapshot of the few fields anything downstream actually needs. docs/ch01:
--    "A booking row stores vehicle_id as a plain identifier with no REFERENCES
--    clause." There is deliberately NO foreign key out of this schema.
--
-- 2. booking_day is new. See its comment below - it is the fix for a real
--    double-booking race that exists in the monolith today.

CREATE TABLE booking (
    id                  BIGINT         NOT NULL AUTO_INCREMENT,

    -- rentez_auth.app_user.id. No FK: cross-schema references are forbidden.
    customer_id         BIGINT         NOT NULL,
    -- Snapshot. Notifications and the audit trail need an address, and reaching
    -- into account-service for it on every read is exactly the coupling the
    -- split is meant to remove.
    customer_email      VARCHAR(255)   NOT NULL,

    -- rentez_fleet.car.id. No FK, same reason.
    car_id              BIGINT         NOT NULL,
    -- Snapshot of the car AS BOOKED. This is what lets a booking still render,
    -- and still explain its own total, after the car is edited or deleted. It is
    -- also what killed two cross-service reads at once: the monolith's
    -- NotificationService walked booking.getCar().getMake() to compose a message,
    -- and ReportService grouped by booking.getCar().getType().
    car_make            VARCHAR(80)    NOT NULL,
    car_model           VARCHAR(80)    NOT NULL,
    -- VARCHAR, not an enum mirrored from catalog: catalog must stay free to add
    -- a vehicle type without breaking this service's deserialisation.
    car_type            VARCHAR(32)    NOT NULL,
    -- The rate quoted at booking time. Frozen, so a later price change cannot
    -- silently alter what an existing booking costs.
    daily_rate_snapshot DECIMAL(10, 2) NOT NULL,

    start_date          DATE           NOT NULL,
    end_date            DATE           NOT NULL,
    pickup_location     VARCHAR(120)   NULL,
    total_amount        DECIMAL(12, 2) NOT NULL,
    status              VARCHAR(32)    NOT NULL DEFAULT 'PENDING_PAYMENT',
    created_at          DATETIME(6)    NOT NULL,

    PRIMARY KEY (id),
    CONSTRAINT ck_booking_status CHECK (status IN
        ('PENDING_PAYMENT', 'CONFIRMED', 'MODIFIED', 'CANCELLED', 'COMPLETED')),
    CONSTRAINT ck_booking_dates CHECK (end_date >= start_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- "My bookings", newest first.
CREATE INDEX ix_booking_customer_created ON booking (customer_id, created_at DESC);
-- Overlap lookups and the per-car history.
CREATE INDEX ix_booking_car_dates ON booking (car_id, start_date, end_date);

-- One row per car per rented day. THE concurrency guarantee.
--
-- The monolith checked availability and then inserted, with no transaction and
-- no constraint:
--
--     List<Long> bookedCarIds = bookingRepository.findBookedCarIds(...);
--     if (bookedCarIds.contains(car.getId())) throw ...;
--     bookingRepository.save(booking);
--
-- That is a textbook time-of-check-to-time-of-use race: two concurrent requests
-- both read "free" and both insert. docs/ch02 rejects it explicitly as
-- "Approach A" and selects this table instead, because correctness is delegated
-- to a unique index - the one component every pod genuinely shares.
--
-- A booking from 10 to 12 August inserts three rows, inclusive of both ends.
-- Rows exist only while the booking is blocking (PENDING_PAYMENT, CONFIRMED,
-- MODIFIED); cancelling deletes them in the same transaction.
--
-- This FK is intra-schema and therefore required, unlike the cross-schema ones
-- above. ON DELETE CASCADE keeps the two tables from ever disagreeing.
CREATE TABLE booking_day (
    car_id     BIGINT NOT NULL,
    day        DATE   NOT NULL,
    booking_id BIGINT NOT NULL,
    PRIMARY KEY (car_id, day),
    CONSTRAINT fk_booking_day_booking FOREIGN KEY (booking_id) REFERENCES booking (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Releasing a booking's days on cancel or modify.
CREATE INDEX ix_booking_day_booking ON booking_day (booking_id);

-- Transactional outbox. Written in the SAME transaction as the business row, so
-- a booking and its pending notification commit or fail together; a relay then
-- delivers them at-least-once and notification-service de-duplicates on
-- event_id. Without this, "save the booking, then POST to notification" can lose
-- the notification, and a notification outage can fail a booking.
--
-- The transport today is HTTP. Swapping in SQS later replaces the relay's sink
-- and nothing else - the table, the event_id and the de-duplication are the
-- parts that would otherwise have to be retrofitted.
CREATE TABLE outbox_event (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    -- The de-duplication key the consumer enforces a UNIQUE index on.
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

-- The relay polls exactly this.
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
