# CarRental-NUSISS — Backend API

Reference for whoever builds the React frontend. Base URL `http://localhost:8080`.

Nothing in the frontend calls this API yet — `frontend/src` is still the stock Vite scaffold.
This document is the contract to build against.

## Running it

```bash
# API only (works today)
docker compose up backend

# Whole stack — needs the rest of the frontend folder first:
#   git checkout practice/mdnurhakimazmannus-patch-1 -- frontend
docker compose up
```

Frontend dev server lands on `http://localhost:3000`, API on `http://localhost:8080`.
Both origins (`:3000` and Vite's default `:5173`) are already allow-listed for CORS via
`app.cors.allowed-origins`; override with the `CORS_ALLOWED_ORIGINS` env var.

Storage is in-memory H2 — **all data resets on restart**. Browse it at `/h2-console`
(JDBC URL `jdbc:h2:mem:carrental`, user `sa`, blank password).

To exercise the backend without Docker, the frontend or `/h2-console` at all, there is a
text-menu harness that calls the services directly — see [CONSOLE.md](CONSOLE.md). It also
has a `--smoke` mode that drives every endpoint's service in sequence with assertions and
writes the result to `target/reports` as HTML and PDF.

## Auth

Login returns a JWT. Send it on every protected call:

```
Authorization: Bearer <token>
```

Tokens last 24h. There is no refresh endpoint — on `401`, send the user back to login.

### Seeded accounts

| Email | Password | Role |
|---|---|---|
| `admin@nusiss.edu` | `Admin123!` | ADMIN |
| `staff@nusiss.edu` | `Staff123!` | STAFF |
| `customer@nusiss.edu` | `Customer123!` | CUSTOMER |

### Endpoints

| Method | Path | Access | Body |
|---|---|---|---|
| POST | `/api/auth/register` | public | `{fullName, email, password, phone?}` |
| POST | `/api/auth/login` | public | `{email, password}` |

`password` must be ≥8 characters. Both return `201`/`200` with:

```json
{ "token": "eyJ...", "tokenType": "Bearer", "userId": 3, "fullName": "Cara Customer", "role": "CUSTOMER" }
```

## Cars

| Method | Path | Access |
|---|---|---|
| GET | `/api/cars` | public |
| GET | `/api/cars/{id}` | public |
| POST | `/api/cars` | ADMIN |
| PUT | `/api/cars/{id}` | ADMIN |
| DELETE | `/api/cars/{id}` | ADMIN → `204` |
| PATCH | `/api/cars/{id}/status?status=` | ADMIN, STAFF |

`GET /api/cars` takes optional query params `location`, `type`, `startDate`, `endDate`
(dates as `YYYY-MM-DD`). With no params it returns everything currently available;
with any param it runs a filtered search that excludes cars already booked in that window.

POST/PUT body: `{make, model, year, dailyRate, location, type}`.

Car JSON:

```json
{ "id": 1, "make": "Toyota", "model": "Corolla", "year": 2022,
  "dailyRate": 65.00, "location": "Jurong", "type": "SEDAN", "status": "AVAILABLE" }
```

- `type` — `SEDAN` `SUV` `HATCHBACK` `TRUCK` `ELECTRIC` `LUXURY`
- `status` — `AVAILABLE` `RENTED` `MAINTENANCE` `RETIRED`

## Bookings

All CUSTOMER-only. Note there is **no** `GET /api/bookings` — use `/me`.

| Method | Path | Body |
|---|---|---|
| POST | `/api/bookings` | `{carId, startDate, endDate, pickupLocation?}` → `201` |
| GET | `/api/bookings/me` | — |
| GET | `/api/bookings/{id}` | — |
| PUT | `/api/bookings/{id}` | `{startDate, endDate, pickupLocation?}` |
| DELETE | `/api/bookings/{id}` | — cancels, returns the booking |

Dates must be today or later. A new booking starts at `PENDING_PAYMENT` — it is not
confirmed until paid.

`totalAmount` is `dailyRate × days`, where days counts **both endpoints inclusively**
(minimum 1). Sept 1 → Sept 4 on a $65/day car bills 4 days = `260.00`, not 3 nights.
Mirror that arithmetic if the UI shows a price preview before submitting.

Booking JSON embeds the full `customer` and `car` objects:

```json
{ "id": 1, "customer": { ... }, "car": { ... },
  "startDate": "2026-09-01", "endDate": "2026-09-04",
  "pickupLocation": "Jurong", "totalAmount": 260.00,
  "status": "PENDING_PAYMENT", "createdAt": "2026-08-10T14:30:00Z" }
```

`status` — `PENDING_PAYMENT` `CONFIRMED` `MODIFIED` `CANCELLED` `COMPLETED`

## Payments

| Method | Path | Access | Body |
|---|---|---|---|
| POST | `/api/payments` | CUSTOMER | `{bookingId, method, cardNumber?}` → `201` |
| POST | `/api/payments/{id}/refund` | ADMIN | — |

`method` — `CARD` `PAYPAL` `WALLET`. A successful payment flips its booking to `CONFIRMED`.
Payment `status` — `SUCCESS` `FAILED` `REFUNDED`.

The gateway is simulated: **any `cardNumber` starting with `0000` is declined**, everything
else succeeds. A decline returns `402` and still records a `FAILED` payment — useful for
building the error path in the checkout UI.

## Maintenance

| Method | Path | Access | Body |
|---|---|---|---|
| POST | `/api/maintenance` | ADMIN | `{carId, description, scheduledDate}` → `201` |
| PUT | `/api/maintenance/{id}/status?status=` | ADMIN, STAFF | — |
| GET | `/api/maintenance/car/{carId}` | ADMIN, STAFF | — |

`status` — `SCHEDULED` `IN_PROGRESS` `COMPLETED`.

Side effects the UI should expect: scheduling a job immediately sets the car to
`MAINTENANCE` (pulling it out of search results), and marking a job `COMPLETED` stamps
`completedDate` and puts the car back to `AVAILABLE`.

## Users

| Method | Path | Access |
|---|---|---|
| GET | `/api/users/me` | any logged-in user |
| PUT | `/api/users/me` | any logged-in user — `{fullName, phone?}` |
| GET | `/api/admin/users` | ADMIN |
| PUT | `/api/admin/users/{id}/status?enabled=` | ADMIN |
| PUT | `/api/admin/users/{id}/role?role=` | ADMIN |

`role` — `CUSTOMER` `STAFF` `ADMIN`.

User JSON — `passwordHash` is never serialized, here or in the `customer` object embedded
in booking responses:

```json
{ "id": 3, "fullName": "Cara Customer", "email": "customer@nusiss.edu",
  "phone": "90000003", "role": "CUSTOMER", "enabled": true,
  "createdAt": "2026-08-10T14:56:36Z" }
```

## Admin reports

| Method | Path |
|---|---|
| GET | `/api/admin/reports/summary` |
| GET | `/api/admin/audit-log?limit=100` |

Both ADMIN-only. `limit` is capped at 500. Summary shape:

```json
{ "totalCars": 5, "availableCars": 4, "carsInMaintenance": 1,
  "totalBookings": 12, "confirmedBookings": 9, "cancelledBookings": 1,
  "totalRevenue": 1840.00, "bookingsByCarType": { "SEDAN": 7, "SUV": 5 } }
```

## Errors

Every failure returns the same shape:

```json
{ "timestamp": "2026-08-10T14:30:00Z", "status": 404,
  "error": "Not Found", "message": "No car with id 99" }
```

| Code | Means |
|---|---|
| `400` | Validation failed. `message` is `field: reason`, joined by `; ` |
| `401` | Bad credentials, or a missing/expired token |
| `403` | Wrong role, or the booking isn't yours — also returned for a disallowed CORS origin |
| `404` | No such entity |
| `409` | Conflict, e.g. email already registered or car already booked for those dates |
| `402` | Payment declined by the simulated gateway |
