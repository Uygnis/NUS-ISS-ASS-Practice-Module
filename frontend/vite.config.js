import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// The API base URL is RELATIVE — "" — in every environment, and that is a
// deliberate deployment decision rather than a shortcut.
//
// On AWS the app is served by a CloudFront distribution whose default behaviour
// points at the S3 bucket holding this build, and whose /api/* behaviour points
// at the ALB. Both therefore live on the same origin, so a request to
// "/api/catalog" resolves without a hostname at all. That matters twice over:
//
//   1. The page is HTTPS (CloudFront's free *.cloudfront.net certificate) while
//      the ALB origin is plain HTTP. If the browser addressed the ALB directly
//      the request would be blocked as mixed content, and fixing that properly
//      means buying a domain and an ACM certificate.
//   2. The ALB's DNS name changes every time the cluster is torn down and
//      recreated. A build-time VITE_API_BASE_URL would bake in a hostname that
//      is wrong by the next morning; a relative URL never goes stale, so the
//      frontend does not need rebuilding on every `make aws-up`.
//
// Locally the same relative URL is made to work by the dev-server proxy below,
// which forwards /api to the nginx gateway on :8080 — the container that mirrors
// the ALB's path routing. See scripts/gateway.conf.
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      "/api": {
        target: process.env.VITE_DEV_API_PROXY ?? "http://localhost:8080",
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: "./src/setupTests.js",
  },
});
