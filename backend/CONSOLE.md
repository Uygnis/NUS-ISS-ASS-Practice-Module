# Backend console harness

A second `main` for driving the backend from a text menu — no Docker, no React frontend,
no `/h2-console`. It calls the same `AuthService` / `CarService` / `BookingService` /
`PaymentService` / `MaintenanceService` / `UserService` / `ReportService` beans the REST
controllers call, so what passes here is the real code path minus HTTP.

Entry point: `src/main/java/.../console/CarRentalConsoleApp.java`

## Running it

**IntelliJ** — open `CarRentalConsoleApp`, run it (green arrow). The Run window is the
terminal; type menu choices straight into it. Add `--with-web` or `--smoke` under
*Run > Edit Configurations > Program arguments*.

**Maven** (from the project root):

```bash
./mvnw -DskipTests package
java -cp target/CarRental-NUSISS-0.0.1-SNAPSHOT.jar \
     -Dloader.main=com.CarRental_NUSISS.CarRental_NUSISS.console.CarRentalConsoleApp \
     org.springframework.boot.loader.launch.PropertiesLauncher
```

## Modes

| Args | What you get |
|---|---|
| *(none)* | Headless. Real services over embedded in-memory H2, `console` profile, no Tomcat, no port bound. |
| `--with-web` | Boots the actual `CarRentalNusissApplication` — REST API on `:8080`, JWT security, CORS — **and** the menu, both over the same beans and the same data. Use this to let other code / the frontend / curl talk to the API while you drive it from the console. |
| `--smoke` | Runs the scripted end-to-end suite, prints a pass/fail report, writes it to `target/reports` as HTML **and** PDF, exits non-zero on any failure. Combinable with `--with-web`. |
| `--report-dir=DIR` | Write those report files somewhere else. |
| `--help` | Usage. |

The unchanged `CarRentalNusissApplication.main` still works exactly as before — this is
additive. `ConsoleConfig` is gated behind the `console` profile precisely so the normal
web boot never sees it.

## Menu

Options are offered per role, mirroring the controllers' `@PreAuthorize` rules.

| Keys | |
|---|---|
| `1`–`3` | register / log in, or (when logged in) view profile, update profile, show JWT |
| `4`–`6` | browse cars, search by location/type/dates, view one car — public, no login |
| `10`–`17` | CUSTOMER: create / list / view / modify / cancel bookings, pay, payment history, notifications |
| `20`–`24` | STAFF + ADMIN: set car status, maintenance history, update a maintenance job, view or cancel any booking |
| `30`–`39` | ADMIN: add / update / delete a car, schedule maintenance, list users, enable-disable a user, change a role, refund, reports, audit log |
| `88` / `99` / `0` | run the smoke suite / log out / exit |

Blank input at a required prompt cancels the action; prompts showing `[value]` default to
it on Enter. Service failures print as the status the API would return, e.g.
`! [HTTP 409 Conflict] This car is already booked for part of that date range`.

Request records are run through the same `jakarta.validation` `Validator` that `@Valid`
uses on the controllers, so bad input fails here the way it fails over HTTP.

## Smoke suite

125 assertions across nine sections: seeded data, registration/login (including duplicate
email, wrong password, unknown email, disabled account), bean validation, fleet CRUD and
search, booking lifecycle (pricing, double-booking, reversed dates, cross-customer
ownership, modify), payments (declined card, receipt/confirmation notifications, revenue
before-and-after, refund, double-pay, double-refund), maintenance (car pulled from
availability and returned), cancellation (customer, staff override, idempotency), user
administration, and reports plus the audit trail.

Failures are reported per check and a section that throws unexpectedly aborts only itself,
so one broken flow can't mask the rest.

It writes real rows (a customer, cars, bookings, payments) under a per-run unique tag, so
it can be run repeatedly. On the throwaway in-memory database that's the point; against a
persistent database the test data stays.

## Shareable report

Every `--smoke` run (menu option `88` included) also writes the whole run to disk — for a
submission, a code review, or anyone who would rather read a document than a console:

```
target/reports/smoke-test-report.html          <- latest, open in any browser
target/reports/smoke-test-report.pdf           <- latest, 4 pages
target/reports/smoke-test-report-<stamp>.html  <- timestamped copy, keeps earlier runs
target/reports/smoke-test-report-<stamp>.pdf
```

Both list **every check in the order it ran**, numbered, grouped by section with a per-section
tally, and marked `ok` (green) or `FAIL` (red). Where a check observed a value — a status code
and message, an enum, an amount — that value is printed beside it as the evidence, e.g.

```
ok    41  double-booking the same car and dates is a conflict   [409 "This car is already
          booked for part of that date range"]
```

Any failures are repeated in a summary block at the top, so a red run can be triaged without
reading the whole thing. The header records what was actually tested: mode, active profiles,
JDBC URL, schema strategy, Spring Boot and Java versions, start time, duration and the run tag.

Neither file has external dependencies: the HTML is self-contained (styles inline, and it has
print rules if you'd rather print from the browser), and the PDF is written directly by
`PdfDocument` using the standard Type1 fonts — no iText, no PDFBox, nothing added to `pom.xml`.
`target/` is gitignored, so reports are never committed; regenerate with one command.

## Known limits

- **Role rules are menu-level here.** `@PreAuthorize` lives on the controllers and the
  services don't re-check it, so calling a service directly can't test authorization. The
  menu only offers each option to the roles the controller allows. Ownership and state
  rules (booking is yours, booking is `PENDING_PAYMENT`, car is `AVAILABLE`, …) *are* in
  the services and are enforced for real.
- Passwords are echoed at the prompt. Local test harness; demo credentials.
- No HTTP layer in headless mode, so CORS, JSON (de)serialization, `@DateTimeFormat`
  parsing and `GlobalExceptionHandler`'s response bodies are not exercised. Use
  `--with-web` plus curl for those.
