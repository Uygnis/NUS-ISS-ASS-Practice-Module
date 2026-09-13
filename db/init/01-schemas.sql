-- RentEZ — one PostgreSQL instance, one database, five schemas, five roles.
-- rentez_startup_project_v1_1_20260810_1421SGT
--
-- Postgres runs this ONCE, on first boot of an empty data volume. Editing it
-- afterwards has no effect. To re-apply:  make clean && make up
--
-- WHY ONE DATABASE AND FIVE SCHEMAS, NOT FIVE DATABASES
-- In MySQL, SCHEMA and DATABASE are synonyms, so "five schemas" was five
-- CREATE DATABASE statements. Postgres separates the two, and the schema is the
-- right unit here: a role cannot cross a schema boundary without an explicit
-- GRANT, but it CAN hold one connection pool to one instance. Five databases
-- would need five pools per service and would make the connection arithmetic in
-- the architecture doc worse, not better. Matches PRD §2.1.
--
-- The isolation is real, not decorative. Each schema is OWNED by its own role
-- and no USAGE is granted to anyone else, so auth_user running
-- SELECT * FROM rentez_booking.booking receives "permission denied for schema"
-- rather than rows. `make db-isolation` prints exactly that, and it is the
-- evidence that the service boundaries hold.
--
-- IMPORTANT: the passwords below must match .env.example. `make check`
-- verifies this and fails loudly if they drift apart, because a mismatch
-- surfaces as an opaque "password authentication failed" hours later.

-- ================================================================== roles
-- Postgres has no CREATE ROLE ... IF NOT EXISTS, so each one is wrapped in a
-- DO block that swallows the duplicate. Written out five times rather than
-- looped: `make check-passwords` greps these lines, and a loop over a VALUES
-- list would hide the passwords from it.

DO $$ BEGIN
    CREATE ROLE auth_user LOGIN PASSWORD 'auth_pw';
EXCEPTION WHEN duplicate_object THEN RAISE NOTICE 'role auth_user already exists';
END $$;

DO $$ BEGIN
    CREATE ROLE fleet_user LOGIN PASSWORD 'fleet_pw';
EXCEPTION WHEN duplicate_object THEN RAISE NOTICE 'role fleet_user already exists';
END $$;

DO $$ BEGIN
    CREATE ROLE booking_user LOGIN PASSWORD 'booking_pw';
EXCEPTION WHEN duplicate_object THEN RAISE NOTICE 'role booking_user already exists';
END $$;

DO $$ BEGIN
    CREATE ROLE payment_user LOGIN PASSWORD 'payment_pw';
EXCEPTION WHEN duplicate_object THEN RAISE NOTICE 'role payment_user already exists';
END $$;

DO $$ BEGIN
    CREATE ROLE notification_user LOGIN PASSWORD 'notification_pw';
EXCEPTION WHEN duplicate_object THEN RAISE NOTICE 'role notification_user already exists';
END $$;

-- ===================================================== membership (RDS only)
-- REQUIRED ON RDS, A NO-OP LOCALLY, AND THE DIFFERENCE IS WORTH UNDERSTANDING.
--
-- `CREATE SCHEMA ... AUTHORIZATION <role>` requires the current user to be able
-- to SET ROLE to that role. A genuine superuser bypasses the check, which is why
-- this file works untouched against the Docker container: there, the init script
-- runs as `postgres`, with usesuper = t and no role memberships at all.
--
-- The RDS master user is NOT a superuser. It holds rds_superuser, which is an
-- ordinary role with CREATEROLE. PostgreSQL 16 deliberately narrowed what
-- CREATEROLE implies: a role you create is now granted back to you as
-- ADMIN TRUE, INHERIT FALSE, *SET FALSE*. Creating the role therefore does not
-- let you own a schema on its behalf, and the failure reads:
--
--     ERROR:  must be able to SET ROLE "auth_user"
--
-- Granting explicitly with SET TRUE makes the requirement satisfied and, more
-- importantly, makes it independent of who happens to be running the script.
DO $$
DECLARE service_role text;
BEGIN
    FOREACH service_role IN ARRAY ARRAY[
        'auth_user', 'fleet_user', 'booking_user', 'payment_user', 'notification_user'
    ] LOOP
        EXECUTE format('GRANT %I TO CURRENT_USER WITH SET TRUE', service_role);
    END LOOP;
END $$;

-- ================================================================ schemas
-- AUTHORIZATION makes the role the schema OWNER, which is what lets Flyway run
-- CREATE TABLE / CREATE INDEX as that role with no further GRANTs. It is also
-- what denies every other role: a non-owner has no USAGE on the schema unless
-- someone grants it, and nothing below grants it.

CREATE SCHEMA IF NOT EXISTS rentez_auth         AUTHORIZATION auth_user;
CREATE SCHEMA IF NOT EXISTS rentez_fleet        AUTHORIZATION fleet_user;
CREATE SCHEMA IF NOT EXISTS rentez_booking      AUTHORIZATION booking_user;
CREATE SCHEMA IF NOT EXISTS rentez_payment      AUTHORIZATION payment_user;
CREATE SCHEMA IF NOT EXISTS rentez_notification AUTHORIZATION notification_user;

-- ============================================================ search_path
-- Set per role, so an unqualified CREATE TABLE from Flyway lands in the right
-- schema and an unqualified SELECT from Hibernate finds it again.
--
-- This is what lets spring.flyway.schemas stay deliberately UNSET (see the note
-- in each application.properties): Flyway uses the connection's default schema,
-- which is rentez_auth for auth_user here and `public` inside the throwaway
-- Testcontainers database under test. Pinning it would break the tests.

ALTER ROLE auth_user         SET search_path = rentez_auth;
ALTER ROLE fleet_user        SET search_path = rentez_fleet;
ALTER ROLE booking_user      SET search_path = rentez_booking;
ALTER ROLE payment_user      SET search_path = rentez_payment;
ALTER ROLE notification_user SET search_path = rentez_notification;

-- ============================================================= privileges
-- Postgres grants CONNECT on a database to PUBLIC by default, and every role
-- is a member of PUBLIC. Revoking it and granting back explicitly means the
-- list of who can reach this database is the list below and nothing else.
--
-- There is deliberately no GRANT of USAGE on any schema to any role other than
-- its owner. That omission IS the boundary.

REVOKE ALL ON DATABASE rentez FROM PUBLIC;
GRANT CONNECT ON DATABASE rentez
  TO auth_user, fleet_user, booking_user, payment_user, notification_user;

-- `public` is a shared scratch schema every role can see. Nothing in this
-- project should ever put a table there; revoking makes an accidental
-- unqualified CREATE TABLE fail loudly instead of succeeding invisibly.
REVOKE ALL ON SCHEMA public FROM PUBLIC;

-- Sanity output, visible in `docker compose logs postgres` on first boot.
SELECT 'RentEZ: 5 schemas and 5 roles created' AS status;
