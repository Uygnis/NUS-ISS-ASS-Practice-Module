-- RentEZ — one MySQL instance, five schemas, five users.
-- rentez_startup_project_v1_1_20260810_1421SGT
--
-- MySQL runs this ONCE, on first boot of an empty data volume. Editing it
-- afterwards has no effect. To re-apply:  make clean && make up
--
-- In MySQL, SCHEMA and DATABASE are synonyms — "five schemas" is five
-- CREATE DATABASE statements on the single instance. Matches PRD §2.1.
--
-- IMPORTANT: the passwords below must match .env.example. `make check`
-- verifies this and fails loudly if they drift apart, because a mismatch
-- surfaces as an opaque "Access denied" hours later.

-- ============================================================== schemas
CREATE DATABASE IF NOT EXISTS rentez_auth
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS rentez_fleet
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS rentez_booking
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS rentez_payment
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS rentez_notification
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- ================================================== users and privileges
-- One user per service, granted on its own schema and nothing else. There is
-- deliberately no GRANT ... ON *.* anywhere in this file: auth_user running
-- SELECT * FROM rentez_booking.bookings receives a permission error, which is
-- the evidence that the service boundaries are real and not decorative.
--
-- DDL privileges (CREATE, DROP, ALTER, INDEX) are included because Flyway runs
-- migrations as these users.

-- account-service -> PRD module M1, Identity & Access
CREATE USER IF NOT EXISTS 'auth_user'@'%' IDENTIFIED BY 'auth_pw';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, DROP, ALTER, INDEX, REFERENCES
  ON rentez_auth.* TO 'auth_user'@'%';

-- catalog-service -> PRD module M2, Fleet & Vehicle Catalogue
CREATE USER IF NOT EXISTS 'fleet_user'@'%' IDENTIFIED BY 'fleet_pw';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, DROP, ALTER, INDEX, REFERENCES
  ON rentez_fleet.* TO 'fleet_user'@'%';

-- reservation-service -> PRD module M3, Booking
CREATE USER IF NOT EXISTS 'booking_user'@'%' IDENTIFIED BY 'booking_pw';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, DROP, ALTER, INDEX, REFERENCES
  ON rentez_booking.* TO 'booking_user'@'%';

-- PRD module M4, Payment & Refund. The service does not exist in the
-- repository yet; the schema and user are created now so it can be added
-- later without touching database setup.
CREATE USER IF NOT EXISTS 'payment_user'@'%' IDENTIFIED BY 'payment_pw';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, DROP, ALTER, INDEX, REFERENCES
  ON rentez_payment.* TO 'payment_user'@'%';

-- notification-service -> PRD module M5, Notification
CREATE USER IF NOT EXISTS 'notification_user'@'%' IDENTIFIED BY 'notification_pw';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, DROP, ALTER, INDEX, REFERENCES
  ON rentez_notification.* TO 'notification_user'@'%';

FLUSH PRIVILEGES;

-- Sanity output, visible in `docker compose logs mysql` on first boot.
SELECT 'RentEZ: 5 schemas and 5 users created' AS status;
