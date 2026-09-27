// SPIKE: jump from quiet to ten times the load with no ramp. Shows the HPA's
// scale-up stabilisation window of 0s (templates/hpa.yaml) doing its job - the
// replica count should move within one metrics-server cycle (~30-60s).
//
//   PEAK=300 k6 run perf/spike.js
import { sleep } from 'k6';
import { registerCustomer, userJourney } from './lib/flow.js';

const PEAK = Number(__ENV.PEAK || 300);

export const options = {
	stages: [
		{ target: Math.round(PEAK / 10), duration: '2m' },
		{ target: PEAK, duration: '10s' },
		{ target: PEAK, duration: '5m' },
		{ target: Math.round(PEAK / 10), duration: '10s' },
		{ target: Math.round(PEAK / 10), duration: '3m' },
	],
	thresholds: { http_req_failed: ['rate<0.05'] },
};

export function setup() {
	return { tokens: Array.from({ length: 20 }, registerCustomer).filter(Boolean) };
}

export default function (data) {
	userJourney(data.tokens[__VU % data.tokens.length]);
	sleep(0.5);
}
