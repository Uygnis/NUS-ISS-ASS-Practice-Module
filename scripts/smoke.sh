#!/usr/bin/env bash
# RentEZ - end-to-end smoke test.
#
# Drives the whole rental flow through the GATEWAY ONLY (:8080), never a service
# port. That restriction is the point: it proves the nginx routing, the five
# services, the shared-secret JWT and the cross-service calls all line up the way
# a real client would meet them.
#
#   make up                     # or: SPRING_PROFILES_ACTIVE=seed make up
#   ./scripts/smoke.sh
#
# Requires the seed profile for the demo admin and the starter fleet.

set -uo pipefail

GATEWAY="${GATEWAY:-http://localhost:8080}"
PASS=0
FAIL=0

pass() { printf "  \033[0;32mok\033[0m   %s\n" "$1"; PASS=$((PASS + 1)); }
fail() { printf "  \033[0;31mFAIL\033[0m %s\n" "$1"; FAIL=$((FAIL + 1)); }

check() { # check <description> <actual> <expected>
	if [ "$2" = "$3" ]; then pass "$1 ($2)"; else fail "$1 — expected $3, got $2"; fi
}

# Body of the last request, and its status code, without two round-trips.
call() { # call <method> <path> [json-body] [extra-header...]
	local method="$1" path="$2" body="${3:-}"
	shift 3 2>/dev/null || shift 2
	local args=(-sS -o /tmp/smoke-body.json -w '%{http_code}' -X "$method" "${GATEWAY}${path}")
	[ -n "$body" ] && args+=(-H 'Content-Type: application/json' -d "$body")
	for h in "$@"; do args+=(-H "$h"); done
	curl "${args[@]}" 2>/dev/null
}

# Evaluates a Python expression against the last response body, bound to `d`.
# Takes the whole expression rather than a suffix appended to "d", so that
# len(d) and d['x'] both work - concatenating onto "d" silently produced
# "dlen(d)" and every count came back empty.
json() { python3 -c "import json,sys;d=json.load(open('/tmp/smoke-body.json'));print(eval(sys.argv[1]))" "$1" 2>/dev/null; }

printf "\n\033[1mRentEZ smoke test — everything through %s\033[0m\n\n" "$GATEWAY"

# ----------------------------------------------------------------- gateway
printf "Gateway\n"
check "healthz" "$(call GET /healthz)" "200"
check "unknown route 404s" "$(call GET /api/nope)" "404"
# The /internal endpoints must not be reachable from outside, even though the
# prefix locations would otherwise match them.
check "internal paths blocked" "$(call GET /api/reservations/internal/stats)" "404"
check "internal paths blocked (catalog)" "$(call GET /api/catalog/internal/stats)" "404"

# ----------------------------------------------------------------- account
printf "\nAccount\n"
EMAIL="smoke$(date +%s)@example.com"
code=$(call POST /api/accounts/auth/register "{\"fullName\":\"Smoke Test\",\"email\":\"${EMAIL}\",\"password\":\"Sup3rSecret!\",\"phone\":\"90001234\"}")
check "register customer" "$code" "201"
CUSTOMER_TOKEN=$(json "d['token']")
CUSTOMER_ID=$(json "d['userId']")

code=$(call POST /api/accounts/auth/login "{\"email\":\"${EMAIL}\",\"password\":\"Sup3rSecret!\"}")
check "login" "$code" "200"

code=$(call GET /api/accounts/users/me "" "Authorization: Bearer ${CUSTOMER_TOKEN}")
check "own profile" "$code" "200"
if grep -qE '\$2[aby]\$' /tmp/smoke-body.json; then
	fail "password hash leaked into /users/me"
else
	pass "no BCrypt hash in profile response"
fi

code=$(call POST /api/accounts/auth/login '{"email":"admin@nusiss.edu","password":"Admin123!"}')
check "admin login (seeded)" "$code" "200"
ADMIN_TOKEN=$(json "d['token']")

code=$(call GET /api/accounts/admin/users "" "Authorization: Bearer ${CUSTOMER_TOKEN}")
check "customer refused admin endpoint" "$code" "403"

# ----------------------------------------------------------------- catalog
printf "\nCatalog\n"
code=$(call GET /api/catalog/cars)
check "browse cars (public)" "$code" "200"
CAR_ID=$(json "d[0]['id']")
CAR_RATE=$(json "d[0]['dailyRate']")
pass "fleet has $(json 'len(d)') cars; using car ${CAR_ID} at ${CAR_RATE}/day"

# ------------------------------------------------------------- availability
printf "\nAvailability (moved from catalog to reservation)\n"
START="2031-03-01"; END="2031-03-03"
code=$(call GET "/api/reservations/availability?startDate=${START}&endDate=${END}")
check "availability (public)" "$code" "200"
AVAIL_BEFORE=$(json 'len(d)')
pass "${AVAIL_BEFORE} cars free ${START}..${END}"

# ------------------------------------------------------------------ booking
printf "\nBooking\n"
code=$(call POST /api/reservations/bookings "{\"carId\":${CAR_ID},\"startDate\":\"${START}\",\"endDate\":\"${END}\",\"pickupLocation\":\"Jurong\"}" "Authorization: Bearer ${CUSTOMER_TOKEN}")
check "create booking" "$code" "201"
BOOKING_ID=$(json "d['id']")
check "status PENDING_PAYMENT" "$(json "d['status']")" "PENDING_PAYMENT"
# 3 inclusive days at the snapshotted rate.
pass "total $(json "d['totalAmount']") for 3 days at $(json "d['dailyRate']") (snapshotted: $(json "d['carMake']") $(json "d['carModel']"))"

code=$(call POST /api/reservations/bookings "{\"carId\":${CAR_ID},\"startDate\":\"2031-03-02\",\"endDate\":\"2031-03-04\"}" "Authorization: Bearer ${CUSTOMER_TOKEN}")
check "overlapping booking refused" "$code" "409"

code=$(call GET "/api/reservations/availability?startDate=${START}&endDate=${END}")
AVAIL_AFTER=$(json 'len(d)')
check "booked car left availability" "$AVAIL_AFTER" "$((AVAIL_BEFORE - 1))"

# ------------------------------------------------------------------ payment
printf "\nPayment (saga)\n"
IDEM="smoke-$(date +%s)"
code=$(call POST /api/payments "{\"bookingId\":${BOOKING_ID},\"method\":\"CARD\",\"cardNumber\":\"4111111111111111\"}" "Authorization: Bearer ${CUSTOMER_TOKEN}" "Idempotency-Key: ${IDEM}")
check "pay" "$code" "201"
PAYMENT_ID=$(json "d['id']")
check "payment SUCCESS" "$(json "d['status']")" "SUCCESS"
check "booking confirmed via saga" "$(json "d['confirmState']")" "CONFIRMED"

code=$(call POST /api/payments "{\"bookingId\":${BOOKING_ID},\"method\":\"CARD\",\"cardNumber\":\"4111111111111111\"}" "Authorization: Bearer ${CUSTOMER_TOKEN}" "Idempotency-Key: ${IDEM}")
check "idempotent replay returns same payment" "$(json "d['id']")" "$PAYMENT_ID"

code=$(call GET "/api/reservations/bookings/${BOOKING_ID}" "" "Authorization: Bearer ${CUSTOMER_TOKEN}")
check "booking now CONFIRMED" "$(json "d['status']")" "CONFIRMED"

# ------------------------------------------------------------- notification
printf "\nNotification (outbox relay)\n"
printf "  waiting for relays to tick"
for _ in $(seq 1 10); do
	call GET /api/notifications/me "" "Authorization: Bearer ${CUSTOMER_TOKEN}" >/dev/null
	if [ "$(json 'len(d)')" -ge 2 ] 2>/dev/null; then break; fi
	printf "."; sleep 2
done
printf "\n"
code=$(call GET /api/notifications/me "" "Authorization: Bearer ${CUSTOMER_TOKEN}")
check "notifications readable" "$code" "200"
COUNT=$(json 'len(d)')
if [ "${COUNT:-0}" -ge 2 ]; then
	pass "${COUNT} notifications delivered: $(json "sorted(t['type'] for t in d)")"
else
	fail "expected at least 2 notifications (BOOKING_CONFIRMED + PAYMENT_RECEIPT), got ${COUNT:-0}"
fi

# ------------------------------------------------------------------- refund
printf "\nRefund (compensation)\n"
code=$(call POST "/api/payments/${PAYMENT_ID}/refund" "" "Authorization: Bearer ${ADMIN_TOKEN}")
check "admin refund" "$code" "200"
check "payment REFUNDED" "$(json "d['status']")" "REFUNDED"

code=$(call GET "/api/reservations/bookings/${BOOKING_ID}" "" "Authorization: Bearer ${CUSTOMER_TOKEN}")
check "booking released to CANCELLED" "$(json "d['status']")" "CANCELLED"

code=$(call GET "/api/reservations/availability?startDate=${START}&endDate=${END}")
check "car back on the market" "$(json 'len(d)')" "$AVAIL_BEFORE"

# ------------------------------------------------------------------ reports
printf "\nReporting (composed from 3 services)\n"
code=$(call GET /api/accounts/admin/reports/summary "" "Authorization: Bearer ${ADMIN_TOKEN}")
check "admin report" "$code" "200"
check "not partial — all services answered" "$(json "d['partial']")" "False"
pass "cars=$(json "d['totalCars']") bookings=$(json "d['totalBookings']") revenue=$(json "d['totalRevenue']") byType=$(json "d['bookingsByCarType']")"

# ------------------------------------------------------------------- result
printf "\n"
if [ "$FAIL" -eq 0 ]; then
	printf "\033[0;32m%s checks passed, 0 failed\033[0m\n\n" "$PASS"
	exit 0
fi
printf "\033[0;31m%s passed, %s FAILED\033[0m\n\n" "$PASS" "$FAIL"
exit 1
