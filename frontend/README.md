# RentEz Frontend

React + Vite single-page app for the RentEz car rental platform. Talks
to the five backend services through the gateway

Gateway service must allow CORS from wherever this frontend is served,
since all requests are made client-side.

## Run locally (dev server)

```bash
npm install
npm run dev
```

Opens on http://localhost:3000 with hot reload.

## Build for production

```bash
npm install
npm run build   # outputs static assets to dist/
npm run preview # serve the production build locally on :3000
```

## Run with Docker

```bash
docker build -t rentez-frontend .
docker run -p 8080:80 rentez-frontend
```

The app is then served at http://localhost:8080.

## Configuring backend URLs

Default URLs live in `public/config.js`, loaded at runtime

## Notes

- Auth uses a JWT returned by the accounts service's login/register
  endpoints, stored in local storage so a page refresh doesn't sign you out.
  For production hardening, consider moving to httpOnly cookies issued by
  the backend instead.
- Roles (`CUSTOMER`, `STAFF`, `ADMIN`) control which tabs and routes are
  visible/reachable; the backend should still enforce authorization
  independently, since this is a client-side check only.
