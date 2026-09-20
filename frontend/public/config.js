// Runtime configuration for RentEz frontend.
//
// EMPTY MEANS SAME-ORIGIN, AND THAT IS THE RIGHT DEFAULT EVERYWHERE.
// On AWS, CloudFront serves this app and forwards /api/* to the ALB, so the API
// is on the page's own origin and a relative URL reaches it with no hostname.
// Locally, the Vite dev server proxies /api to the gateway on :8080, so the
// same relative URL works there too. See frontend/vite.config.js.
//
// It used to say "http://localhost:8080", which is correct only on a developer
// laptop. Deployed, every call went to the reader's own machine and login
// failed with "Could not reach http://localhost:8080".
//
// Override it by mounting your own config.js (Docker) or from the in-app
// settings panel, which stores a per-browser override in localStorage.
window.__RENTEZ_GATEWAY_ENDPOINT__ = "";
