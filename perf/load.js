// PERFORMANCE: expected production-like load, held steady, judged against SLOs.
//
// Arrival-rate rather than VU-based, so the request rate stays fixed even when
// responses slow down - otherwise a slow system quietly receives less load and
// looks better than it is.
//
//   RATE=50 k6 run perf/load.js       (requests/iteration per second, default 30)
import { registerCustomer, userJourney } from './lib/flow.js';

const RATE = Number(__ENV.RATE || 30);

export const options = {
	scenarios: {
		steady: {
			executor: 'ramping-arrival-rate',
			startRate: 1,
			timeUnit: '1s',
			preAllocatedVUs: 50,
			maxVUs: 1000,
			stages: [
				{ target: RATE, duration: '1m' },  // warm up (JIT, connection pools)
				{ target: RATE, duration: '5m' },  // measured window
				{ target: 0, duration: '30s' },
			],
		},
	},
	// The SLOs. A breach makes k6 exit non-zero, so this is a pass/fail test.
	thresholds: {
		http_req_failed: ['rate<0.01'],
		// Guards against a hollow pass: if bookings stop finding free cars, the
		// write path stops running and every other number looks better than it is.
		'checks{check:found a free car}': ['rate>0.95'],
		http_req_duration: ['p(95)<500', 'p(99)<1000'],
		'http_req_duration{name:catalog_list}': ['p(95)<300'],
		'http_req_duration{name:availability}': ['p(95)<400'],
		'http_req_duration{name:create_booking}': ['p(95)<800'],
		'http_req_duration{name:pay}': ['p(95)<1000'],
	},
};

export function setup() {
	// A small pool of customers shared across VUs; registering per iteration
	// would turn this into a BCrypt benchmark.
	return { tokens: Array.from({ length: 20 }, registerCustomer).filter(Boolean) };
}

export default function (data) {
	userJourney(data.tokens[__VU % data.tokens.length]);
}
