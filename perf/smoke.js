// One user for thirty seconds. Run this before anything heavier: if it fails,
// the load numbers would be measuring a broken deployment.
import { sleep } from 'k6';
import { registerCustomer, browseCars, availability, bookAndPay, randomWindow } from './lib/flow.js';

export const options = {
	vus: 1,
	duration: '30s',
	thresholds: { checks: ['rate==1'], http_req_failed: ['rate==0'] },
};

export function setup() {
	return { token: registerCustomer() };
}

export default function (data) {
	browseCars();
	availability(randomWindow());
	bookAndPay(data.token);
	sleep(1);
}
