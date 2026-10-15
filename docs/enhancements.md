# UX Enhancements & Bug Fixes — `feature/enhance-fix-bugs`

## Ground rules

1. **No architecture changes.** Same five services, gateway, databases, Helm charts and AWS stacks. No new services, endpoints, tables or dependencies beyond what Spring Boot / React already ship.
2. **Scope:** form fields, card design, error messages, imagery and other quality-of-life fixes.
3. **Colour scheme is frozen.** Reuse the existing CSS variables in `frontend/src/index.css` (`--sign-green-dark`, `--route-red`, `--steel`, `--steel-light`, …). No new colours.
4. **Backend logging goes to stdout through SLF4J.** Fluent Bit (`aws/eksctl/cluster.yaml`) already ships every container's stdout to the CloudWatch log group `/rentez/<env>/cluster`, so no AWS changes are needed.

Legend: 🐞 bug · ✨ enhancement · Priority **P1** (do first) / **P2** / **P3**

---

## 1. Bugs

| # | Where | Issue | Fix | P |
|---|---|---|---|---|
| B1 ✅ | `BrowsePage.jsx` — "Book this car" | The booking form opens below the results grid, off-screen, so it looks like nothing happened. | Scroll the booking panel into view and focus its first field. | P1 |
| B2 ✅ | `BrowsePage.jsx` — booking form | **Car ID** is an editable number field. A user can change it and book a different car by accident. | Make it read-only and show the car's make, model, type and rate instead of a raw ID. | P1 |
| B3 ✅ | `BrowsePage.jsx` / `BookingsPage.jsx` date inputs | Past dates can be picked, and End can be set before Start. The backend rejects this with "endDate cannot be before startDate". | Set `min` = today on Start and `min` = Start on End. Validate on the client before sending. | P1 |
| B4 ✅ | `BookingService.requireOrderedDates` | Bookings in the past are accepted by the backend. | Reject `startDate < today` with 400 "Start date can't be in the past". | P1 |
| B5 ✅ | `BookingsPage.jsx` — inline edit | The Save/Cancel buttons are not inside a `<form>`, so Enter does nothing. Save errors also replace the page-level error and are easy to miss. | Wrap the edit row in a form and show errors inside the edit row. | P2 |
| B6 ✅ | `PaymentsPage.jsx` | Card number shows for PAYPAL/WALLET. "optional" placeholder is misleading for CARD. | Show the card field only for CARD, require it there, and allow digits only, formatted `#### #### #### ####`. | P1 |
| B7 ✅ | `PaymentsPage.jsx` | Booking ID is free text. Users have to remember IDs. | Use a dropdown of the user's `PENDING_PAYMENT` bookings, labelled with car, dates and amount. Keep the preselect from the "Pay" button. | P1 |
| B8 ✅ | `PaymentsPage.jsx` | The Pay button has no busy state, so a double-click sends two requests. Idempotency prevents a double charge, but the user still sees a confusing error. | Disable the button while the request runs and show "Processing…". | P1 |
| B9 ✅ | `FleetPage.jsx` `CarsPanel.load` | A leftover `console.log(list)` is still in the code. | Remove it. | P3 |
| B10 ✅ | `ProfilePage.jsx` | After a save error, the earlier "Profile updated." message stays on screen. Also, the error message renders above the form while the success message renders below it. | Clear both messages on save and render them in the same place. | P3 |
| B11 ✅ | `NotificationsPage.jsx` | `sentAt` is a raw ISO timestamp. | Show it as a relative time ("5 min ago"), with the full time in a tooltip. | P2 |
| B12 ✅ | Every `GlobalExceptionHandler` | There is no catch-all handler. Unexpected exceptions return Spring's default body, which the UI shows as "Request failed (500)", and nothing useful is logged. | Add an `@ExceptionHandler(Exception.class)` that logs the stack trace at ERROR and returns "Something went wrong on our side. Please try again." | P1 |

## 2. Meaningful error messages

| # | Where | Today | Proposed |
|---|---|---|---|
| E1 ✅ | `GlobalExceptionHandler.handleValidation` (×5) | `fullName: must not be blank; email: must be a well-formed email address` | Use readable field names and per-field messages: "Full name is required", "Enter a valid email address". Also return a `fieldErrors` map so the UI can highlight the specific input. The `message` field stays, so the response contract is unchanged. |
| E2 ✅ | `client.js` network error | `Could not reach http://… — is the service running…` | "We can't reach RentEz right now. Check your connection and try again." Keep the technical detail in `console.error`. |
| E3 ✅ | `client.js` 401 / 403 | Raw backend text | On 401: "Your session has expired — please sign in again" and redirect to `/login`. On 403: "You don't have access to this." |
| E4 ✅ | `BookingService` | "endDate cannot be before startDate" | "End date must be on or after the start date." |
| E5 ✅ | `BookingService` | "Cannot modify a CANCELLED booking" | "This booking is cancelled and can no longer be changed." (a message for each status) |
| E6 ✅ | `PaymentService` | "Payment was declined by the gateway" | "Your payment was declined. Please check your card details or try another method." |
| E7 ✅ | `ReservationClient` / `CatalogClient` | "Reservation service is unavailable" | "Bookings are temporarily unavailable. Please try again in a moment." |
| E8 ✅ | `CarService` / `MaintenanceService` | "No car with id 42" | "Car #42 was not found — it may have been removed." |
| E9 ✅ | Front end, forms in general | A single message line at the top or bottom of the form | Show a field-level hint under the specific input (`.field.invalid` uses the existing `--route-red`). |

## 3. Form field enhancements

| # | Form | Enhancement |
|---|---|---|
| F1 ✅ | Register | Add `autoComplete` (`name`, `email`, `tel`, `new-password`). Make phone `type="tel"` with a pattern hint like "+65 9123 4567". Add a show/hide password toggle and a confirm-password field. |
| F2 ✅ | Login | Add `autoComplete="email" / "current-password"` and a show/hide toggle. Autofocus the email field. |
| F3 ✅ | Browse search | Turn Location into a dropdown or `<datalist>` of the known locations from the catalog results. Add a "Clear filters" button. Keep the search in the URL query so the back button works. |
| F4 ✅ | Booking form | Show a live price summary: "3 days × $80.00 = **$240.00**", matching the backend's inclusive day count. Prefill the pickup location from the car. |
| F5 ✅ | Fleet → Add car | Set `min=1990 max=currentYear+1` on Year and `step=0.01 min=0` on Daily rate. Clear the form after a successful save and show a success message. |
| F6 ✅ | Fleet → Maintenance | Use a dropdown of cars ("#12 Toyota Corolla") instead of a raw Car ID. |
| F7 ✅ | Profile | Use `type="tel"` for phone. Disable the Save button until something has changed. |
| F8 ✅ | All submit buttons | Show a consistent busy state (`disabled` plus "…ing" label). Several forms (payments, profile, fleet, bookings edit) don't have one yet. |

## 4. Card & layout design (existing palette only)

| # | Where | Enhancement |
|---|---|---|
| C1 ✅ | `.carcard` (Browse) | Add a **car image** header, built from local SVG silhouettes chosen by `type` (SEDAN, SUV, HATCHBACK, …) and stored in `frontend/src/assets/cars/`. This needs no backend or schema change. Use the year and location as small chips. Lay the rate and button out on one row. |
| C2 ✅ | `.carcard` | Add a hover lift (shadow) and a visible `:focus-within` outline so keyboard users can see which card is selected. |
| C3 ✅ | Browse results | Add a result count ("12 cars available 14–16 Oct") and sort options (price ↑/↓, year). |
| C4 ✅ | Bookings table | Show a card layout on narrow screens (< 640px). Explain disabled actions with a tooltip ("Paid bookings can't be modified"). |
| C5 ✅ | Bookings | Add a "Pay now" button on the green success notice after a booking is created, linking straight to the payment step. |
| C6 ✅ | Notifications | Add an unread badge count in the `Layout` nav, a "Mark all read" button, and an icon for each type. |
| C7 ✅ | Empty states | Give each empty state a small illustration and a call-to-action button, e.g. "No bookings yet → Browse cars". |
| C8 ✅ | Loading states | Replace the "Loading…" text with skeleton rows or cards using `--steel-light`. |
| C9 ✅ | Messages | Make success messages disappear after about 4 seconds and give them a close button. Keep error messages until the user closes them. |

## 5. Backend logging (CloudWatch via stdout)

Today, only 8 classes log anything (DataSeeder, Outbox dispatchers, PaymentService, etc.). Controllers and most services log nothing.

| # | Change |
|---|---|
| L1 ✅ | **Log pattern.** In each `application.properties`, set `logging.pattern.console` to include `%d{ISO8601} %-5level [${spring.application.name}] [%X{requestId}] %logger{36} - %msg%n`, so CloudWatch Logs Insights can filter by service and request. |
| L2 ✅ | **Request ID.** Add a small `OncePerRequestFilter` in each service that reads `X-Request-Id` (or generates one), puts it in the MDC, and echoes it back in the response. It also logs one line per request: `method path status durationMs userId`. |
| L3 ✅ | **Business events at INFO:** user registered / logged in / failed login (email masked) / disabled; booking created / modified / cancelled (bookingId, carId, dates, total); payment attempted / succeeded / declined / refunded (paymentId, bookingId, amount — **never** card numbers); car added / status changed / deleted; maintenance scheduled / completed; notification received / marked read. |
| L4 ✅ | **WARN** for every `ApiException` with a 4xx status (status and message only). **ERROR** with the stack trace in the new catch-all handler (B12) and on downstream call failures (`ReservationClient`, `CatalogClient`, `StatsClient`). |
| L5 ✅ | **Privacy:** never log passwords, JWTs or card numbers. Mask emails (`j***@gmail.com`). |
| L6 | Document example Logs Insights queries in `docs/` (for example, errors by service in the last hour, and p95 request duration). |

---

## Progress

✅ = done on this branch. Notes on what shipped:

- **Logging (L1–L5).** Each service has a new `logging/RequestLoggingFilter` and an updated `logging.pattern.console`. Business events are logged once in each service's `AuditService.log`, which every audited action already passes through. Log lines look like this:
  `2026-10-15 00:18:50,881 WARN  [account-service] [3f2c…] o.r.a.service.AuthService - event=LOGIN_FAILED reason=unknown_email actor=n***@example.com`
- **B4.** The past-date check applies when a booking is created, and when a modification moves its start date. A booking already under way can still be modified with its original start date.
- **B12.** Spring's own HTTP errors (404, 405, …) keep their status codes. Only genuinely unexpected exceptions become a 500, with the stack trace logged.
- **B12 (continued).** Malformed bodies and wrongly typed values return 400 "The request was not in the expected format…". Unknown routes return 404 "We couldn't find what you were looking for." instead of Spring's "No static resource…" text.
- **L5 (continued).** Emails are masked. Service identities (e.g. the payment service confirming a booking) are logged unmasked, because they are not personal data.
- **E4.** Shipped together with B4: "The end date must be on or after the start date."
- **E1/E9.** Validation errors are now plain sentences ("Full name is required.", "Phone must be at most 20 characters."), plus a `fieldErrors` map. Register, Profile and Fleet → Add car show each error under its input, outlined in the existing `--route-red`.
- **E2/E3.** Network failures read "We can't reach RentEz right now…". A 401 on a signed-in request clears the session and shows "Your session has expired. Please sign in again." on the sign-in page. A wrong password still shows "Invalid email or password", because the login request carries no token. A 403 reads "You don't have access to this."
- **E5–E8.** Backend messages were reworded in all five services: not found, not yours, cancelled/completed, payment declined, already paid, a dependency temporarily unavailable, and the staff car-status restriction. Tests that pinned the old monolith wording were updated. The phrases "already booked" and "already been paid" were kept, because `perf/demo/record-usecases.js` matches on them.
- **Found while doing E6:** the backend only accepts payment for `PENDING_PAYMENT` bookings. The Pay button and the payment dropdown also offered `MODIFIED` bookings, which are already paid, so they would always fail. Both now offer only `PENDING_PAYMENT` bookings, and the backend explains each status instead of "not awaiting payment (status: X)".
- **F1–F8 (form fields).**
  - **Register:** `autocomplete` on every field; phone is `type="tel"`, optional, with a `+65 9123 4567` hint; a Show/Hide password toggle (`PasswordInput` in `ui.jsx`); and a confirm-password field that is checked before the request is sent.
  - **Login:** the email field is focused on load, with correct `autocomplete` and the Show/Hide toggle.
  - **Browse:** the search is kept in the URL (`?location=…&startDate=…`), so Back and shared links restore the results. Location suggests known pickup points (a `<datalist>` built from the catalog), and a Clear button resets everything.
  - **Booking form (F4):** the price summary and pickup prefill shipped with the earlier bug fixes.
  - **Add car:** year limited to 1990 through next year, rate at least 0.01, placeholders, and a busy state.
  - **Maintenance:** a description hint and a busy state.
  - **Profile:** phone is `type="tel"`, and Save stays disabled until something changes.
  - **My bookings:** the Modify row is now a real form, so Enter saves. It has a busy state, a success notice, and shows errors inside the row (this also closes B5).
- **C1–C9 (cards and layout).**
  - **C1–C3 Browse:** each card has an inline-SVG silhouette for its type (`components/CarArt.jsx`, palette tokens only, no image files), year and location chips, and the price and button on one row. Cards lift on hover and show a focus outline. A result count reads "5 cars available 20 Dec – 22 Dec", and the results can be sorted by price or year. The "Book this car" label is unchanged, because the demo script clicks it.
  - **C4 My bookings:** below 640px wide the table becomes stacked cards (`table.stack`). Disabled actions explain why in a tooltip.
  - **C5:** the "created" notice stays on screen with a **Pay now** button that opens Payments with the booking selected.
  - **C6 Notifications:** a tone and icon per event type, readable titles, relative times ("34 min ago"), and Mark all read. The nav badge refreshes immediately, through a `rentez:notifications-changed` event.
  - **C7:** empty states have an illustration, and a "Browse cars" button where useful.
  - **C8:** shimmer skeletons replace "Loading…" on every page, with no animation when reduced motion is set.
  - **C9:** success messages close after 4 seconds; errors stay. Both have a × button.
- **Found while re-testing the lint fixes (backend bug, also on `main`):** Fleet → Cars with a type but no location returned 500 `function lower(bytea) does not exist`. `CarRepository.findByFiltersNotStatus` was missing the `cast(:location as String)` that `findByFilters` already had. It is fixed, with a regression test (`staffFleetListFiltersByTypeWithoutALocation`, run on Testcontainers Postgres). The new error logging pinpointed it from a single request.
- **Removed the "Service URLs / GATEWAY" footer** (`ConfigPanel.jsx`). It was a developer override for the gateway URL, saved in localStorage, with no use for customers. The gateway now always comes from `public/config.js` (set at deploy time), and any old saved override is cleared on load, so it can't silently send requests to the wrong place.
- **Found during C5:** pages opened at the previous page's scroll position, so the booking notice was off-screen. `Layout` now scrolls to the top whenever the page changes.
- **B11/B10.** Covered by C6 (relative times) and the earlier Profile changes (one message area, cleared on save).
- **Demo script.** `perf/demo/record-usecases.js` lives on `docs/presentation-info-branch`, not `dev`, so this branch doesn't touch it. When the two meet, it needs: `input[placeholder=optional]` → `input[name=cardNumber]`, and the booking-ID `type(...)` → `select[name=bookingId]` `selectOption`.
- **B8.** `perf/demo/record-usecases.js` use case 3 double-clicks "Pay" to demonstrate idempotency. The button now disables after the first click, so re-record that clip or send the second request through the API.

### Verification (2026-10-15, local `make up` stack)

- `./mvnw test` passes in all five services, including the new `rejectsAStartDateInThePast` test. The `BookingRulesIntegrationTest` dates moved from 2026/27 to 2036/37, because 2026 dates are now in the past.
- `scripts/smoke.sh`: 33 checks passed, 0 failed (re-run after the error-message changes: still 33/33).
- **Error messages in the browser:** register with an over-long phone shows the error under the field; an invalid token redirects to sign-in with the expiry notice; a wrong password still shows "Invalid email or password".
- Clicked through in the browser:
  - **Customer:** search with past dates is blocked; the booking form scrolls into view with the car name and a price summary; the payment form shows a booking dropdown, the card field only for cards, card number formatting and validation, and a success message.
  - **Staff:** both maintenance car dropdowns work, and choosing a car loads its history straight away.
- **Form fields in the browser:** the Register password toggle and confirm mismatch; login autofocus; Browse restoring a search from the URL, location suggestions and Clear; Profile Save enabling and disabling; and Modify saving on Enter (re-priced 4 → 5 days, $260 → $325).
- **Cards and layout in the browser:** car art and sorting; booking showing the sticky notice, with Pay now preselecting the booking; notification icons and Mark all read clearing the badge; bookings as stacked cards at 375px wide with no sideways scroll; a success message closing after 4 seconds. Smoke test still 33/33.
- **Not yet clicked through:** scheduling maintenance, and the Add car limits. Both are covered by the build only.
- **Frontend lint is clean:** `npm run lint` (the whole frontend, including `vite.config.js`, which now gets Node globals) reports 0 errors and 0 warnings, down from 21 errors on `main` and 25 mid-branch.
  - **Fast Refresh:** `ui.jsx` exports only components; constants and formatting helpers moved to `components/format.js`. The context hooks moved out of the provider files into `context/useAuth.js` and `context/useConfig.js`. They are named that way because macOS treats `authContext.js` and `AuthContext.jsx` as the same file.
  - **set-state-in-effect:** each page now fetches inside its effect, using `.then` with an `active` cleanup flag, so a stale response is ignored. `load()` just bumps a reload counter, so existing callers are unchanged. Browse runs its search from a `query` state seeded from the URL, and the Bookings "created" notice is seeded from the navigation state.
  - **Leftovers:** the dead `StatsPanel` reference and an unused assignment in `ConfigContext` were removed.

## Suggested order of work

1. **P1 bugs:** B1–B4, B6–B8, B12.
2. **Backend logging:** L1–L5. This is low risk and helps debug everything after it.
3. **Error messages:** E1–E8.
4. **Form enhancements:** F1–F8.
5. **Card / visual polish:** C1–C9.
6. **P2/P3 leftovers:** B5, B9–B11, L6.

Each step should keep `make test` / CI green. No step touches Helm, CloudFormation, DB migrations or service boundaries.
