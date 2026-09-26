// RentEZ - the request flow every k6 script drives, ported from scripts/smoke.sh.
//
// Everything goes through BASE_URL (the local gateway, or the CloudFront URL of
// a deployed environment), never a service port, so a result measures what a
// real client would meet.
import http from 'k6/http';
import { check } from 'k6';

export const BASE_URL = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/$/, '');

// A 409 from `create booking` means two virtual users picked the same car for
// overlapping dates. That is the service being correct under contention, not a
// failure, so it must not count against http_req_failed.
http.setResponseCallback(http.expectedStatuses({ min: 200, max: 399 }, 409));

const JSON_HEADERS = { 'Content-Type': 'application/json' };

function auth(token) {
	return { ...JSON_HEADERS, Authorization: `Bearer ${token}` };
}

// A random three-day window far enough out that it never collides with seed
// data. Spread over ~100 years because every successful booking is CONFIRMED
// and never released: the local fleet is five cars, and a 2000-day range
// filled up after a few load runs, at which point no window had a free car and
// the booking path silently stopped running. Starts at 2040 to stay clear of
// the 2031-2036 range those earlier runs used up.
export function randomWindow() {
	const base = new Date(Date.UTC(2040, 0, 1) + Math.floor(Math.random() * 36500) * 86400000);
	const end = new Date(base.getTime() + 2 * 86400000);
	const iso = (d) => d.toISOString().slice(0, 10);
	return { start: iso(base), end: iso(end) };
}

// Registers a fresh customer and returns its JWT. One per VU, so no single
// account becomes a hot row.
export function registerCustomer() {
	const email = `perf-${__VU}-${Date.now()}-${Math.floor(Math.random() * 1e6)}@example.com`;
	const res = http.post(
		`${BASE_URL}/api/accounts/auth/register`,
		JSON.stringify({ fullName: 'Perf Test', email, password: 'Sup3rSecret!', phone: '90001234' }),
		{ headers: JSON_HEADERS, tags: { name: 'register' } },
	);
	check(res, { 'register 201': (r) => r.status === 201 });
	return res.status === 201 ? res.json('token') : null;
}

export function browseCars() {
	const res = http.get(`${BASE_URL}/api/catalog/cars`, { tags: { name: 'catalog_list' } });
	check(res, { 'catalog 200': (r) => r.status === 200 });
	return res;
}

export function availability(win) {
	const res = http.get(
		`${BASE_URL}/api/reservations/availability?startDate=${win.start}&endDate=${win.end}`,
		{ tags: { name: 'availability' } },
	);
	check(res, { 'availability 200': (r) => r.status === 200 });
	return res.status === 200 ? res.json() : [];
}

// Books a free car and pays for it: account -> reservation -> payment, and the
// saga back into reservation. The heaviest path in the system.
export function bookAndPay(token) {
	const win = randomWindow();
	const cars = availability(win);
	// Recorded as a check so a full fleet shows up in the results (and fails the
	// load test's threshold) instead of quietly skipping the write path.
	check(cars, { 'found a free car': (c) => c.length > 0 });
	if (!token || !cars.length) return;
	const car = cars[Math.floor(Math.random() * cars.length)];

	const booking = http.post(
		`${BASE_URL}/api/reservations/bookings`,
		JSON.stringify({ carId: car.carId, startDate: win.start, endDate: win.end, pickupLocation: 'Jurong' }),
		{ headers: auth(token), tags: { name: 'create_booking' } },
	);
	check(booking, { 'booking 201 or 409': (r) => r.status === 201 || r.status === 409 });
	if (booking.status !== 201) return;

	const pay = http.post(
		`${BASE_URL}/api/payments`,
		JSON.stringify({ bookingId: booking.json('id'), method: 'CARD', cardNumber: '4111111111111111' }),
		{
			headers: { ...auth(token), 'Idempotency-Key': `perf-${__VU}-${__ITER}-${Date.now()}` },
			tags: { name: 'pay' },
		},
	);
	check(pay, { 'payment 201': (r) => r.status === 201 });
}

// The traffic mix every scenario uses: mostly browsing, some searching, a few
// bookings - roughly what a rental site sees.
export function userJourney(token) {
	const roll = Math.random();
	if (roll < 0.7) browseCars();
	else if (roll < 0.9) availability(randomWindow());
	else bookAndPay(token);
}
