// SCALABILITY: ramp well past what the minimum replicas can serve, hold long
// enough for the HPA and then the Cluster Autoscaler to react, then back off.
//
// Run with perf/watch-scaling.sh alongside (perf/run.sh does both). The
// evidence is the pair: latency climbs as CPU crosses 60% of the 200m request,
// replicas rise, pending pods pull in a new node, and latency comes back down
// while the load is still at its peak.
//
//   PEAK=400 k6 run perf/stress.js    (peak VUs, default 300)
import { sleep } from 'k6';
import { registerCustomer, userJourney } from './lib/flow.js';

const PEAK = Number(__ENV.PEAK || 300);

export const options = {
	stages: [
		{ target: Math.round(PEAK * 0.1), duration: '1m' },  // baseline at min replicas
		{ target: Math.round(PEAK * 0.5), duration: '3m' },  // HPA should start adding pods
		{ target: PEAK, duration: '3m' },                     // pods go Pending -> new node
		{ target: PEAK, duration: '6m' },                     // hold: latency should recover
		{ target: 0, duration: '2m' },                        // scale-down starts ~5 min later
	],
	// Deliberately loose - a stress test is expected to hurt. These only catch
	// a system that fell over outright.
	thresholds: {
		http_req_failed: ['rate<0.05'],
		http_req_duration: ['p(95)<3000'],
	},
};

export function setup() {
	return { tokens: Array.from({ length: 20 }, registerCustomer).filter(Boolean) };
}

export default function (data) {
	userJourney(data.tokens[__VU % data.tokens.length]);
	sleep(0.5);
}
