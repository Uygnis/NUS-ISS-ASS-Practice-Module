# RentEZ — Presentation Script

> Group 2 — John Ling · Sachin Agrawal · Quek Shen An · Muhammad Nur Hakim Bin Azman
>
> 16 Oct 2026, 3:30 PM SGT · 40 minutes including Q&A · follows *Project Presentation Guidelines V2.8*
>
> This is the spoken script. Each section answers **what we did** and **why we did it** — the decision, the alternative we rejected, and the evidence that it worked. Slide numbers refer to [RentEZ-Presentation.pptx](RentEZ-Presentation.pptx) (slides 26–36 are hidden template slides); interactive diagrams are in `.archify/`. Detail and Q&A prep: [presentation.md](presentation.md).
>
> Speaking pace assumed: ~130 words/minute. Times are `talk + Q&A`.

| # | Section | Time | Presenter | Visual |
|---|---|---|---|---|
| 1 | Overview | 2 + 1 | _TBD_ | Slides 3–5 |
| 2 | Project Conduct | 1.5 + 0.5 | _TBD_ | Slides 6–7 |
| 3 | Logical Architecture & Design | 7 + 2 | _TBD_ | Slides 8–10 |
| 4 | Physical Architecture & Design | 7 + 2 | _TBD_ | Slides 11–16 |
| 5 | DevOps & Development Lifecycle | 6 + 1 | _TBD_ | Slides 17–21 |
| 6 | Demo | 8 + 2 | _TBD_ | Slides 22–24, live demo |

---

## 1. Overview — 2 min (+1 Q&A)

**[Slide: Title]**

Good afternoon, we're Group 2. We're presenting RentEZ, a car rental platform built as five independently deployable microservices on AWS.

**[Slide: The business problem]**

**What:** Customers browse a fleet, book a car for a date range, pay, and get notified. Staff manage vehicles and maintenance. Admins manage users, issue refunds and read a dashboard.

**Why it's harder than it looks:** a car exists *once*. Two customers clicking "Book" at the same moment must not both succeed. A charged card with an unconfirmed booking is a support ticket. Last year's receipt still has to make sense after the car is repriced. A car sent for maintenance must disappear from availability immediately. And a notification failure must never fail a booking.

**Our solution, in business terms:** every booking a customer receives is guaranteed, every payment matches a booking, and capacity grows with demand — so the business never sells the same car twice, never chases unmatched payments, and pays only for the capacity it uses.

Every one of those is easy in a single database. Our real subject is: **how do you keep them true when the data is split across five services that cannot see each other's tables?** That question drives every decision you'll hear today.

**[Slide: Platform thinking & scope]**

**Platform thinking.** We didn't build one app; we built reusable capabilities — identity, fleet, booking, payment, notification — that producers and consumers meet through one API edge. Staff and admins *produce* the fleet; customers *consume* it; services publish domain events that other services consume. The infrastructure is self-service too: any teammate brings up a whole environment with one command, and CI runs the same command.

**Scope.** In: the full customer, staff and admin flows, two AWS environments, CI/CD with security scanning, and load and scalability testing. Deliberately out: a real payment provider, refresh tokens, queue-based autoscaling and multi-region. We cut those so we could go deep on correctness and scalability rather than wide on features.

---

## 2. Project Conduct — 1.5 min (+0.5 Q&A)

**[Slide: Status & milestones]**

**Where we are:** all five services and the React frontend are complete and deployed on AWS in separate dev and prod environments. 71 backend tests run against a real PostgreSQL, plus a 33-step end-to-end smoke test.

**How we got here:** services and the payment saga in August, frontend integrated end of August, AWS infrastructure in early September. The last week of September was load testing — which turned out to be the most valuable work of the project, and I'll explain why in DevOps.

**Team:** John Ling led DevOps, Sachin Agrawal the frontend, Quek Shen An the architecture, and Muhammad Nur Hakim Bin Azman the backend. _[each states rough effort]_

**Outstanding issues — we're stating these up front:** CPU-based autoscaling reacts late when the real bottleneck is database connections; the frontend tests are disabled in CI; there's no token revocation or refresh; and CloudFront masks API 404s as HTML 200s. Each one has a known fix that we'll mention where it comes up.

---

## 3. Logical Architecture & Design — 7 min (+2 Q&A)

### 3a. Key decisions — ~2.5 min

**[Slide: Key logical decisions]**

Six decisions shape the logical architecture. For each: what, and why.

1. **Database per service.** Each service owns its own schema and connects with its own database role, with no grants across schemas.
   *Why:* a boundary you can cross "just this once" isn't a boundary. We made it physically impossible: `make db-isolation` proves the auth role cannot read the booking tables.

2. **No cross-service joins.** If a service needs another's data, it either copies it at write time or asks the owner's API.
   *Why:* the alternative — reading another schema — couples deploys and makes the schema a shared API nobody can change.

3. **No distributed transactions.** Payment and booking are two systems. We use idempotent operations plus compensation — a saga — instead of a two-phase commit.
   *Why:* two-phase commit needs a coordinator, blocks on failure, and doesn't work across HTTP. Idempotency plus retries does.

4. **No dependency cycles.** Availability search lives in *reservation*, not catalog.
   *Why:* availability needs booking data. If catalog answered it, catalog would call reservation while reservation already calls catalog — a cycle where neither can be built, tested or deployed alone. Moving one endpoint bought us a catalog service with zero outbound dependencies.

5. **Stateless identity.** Every service validates JWTs locally; role and user id are claims in the token.
   *Why:* if every request asked the account service who's calling, identity would be a bottleneck and a single point of failure. The trade-off is that permissions are only as fresh as the token — which is why tokens expire in 15 minutes.

6. **Degrade, don't cascade.** When the admin dashboard can't reach a service, it returns that section as `null` with `partial: true`.
   *Why:* `totalRevenue: 0` reads as a business fact. `null` reads as "we don't know". Showing a confident zero is worse than showing nothing.

### 3b. Logical components — ~2.5 min

**[Diagram: `logical.html` — bounded contexts, producers and consumers]**

Here are the five contexts and who talks to whom. Read it from the outside in.

- **Staff and admins** on the top left *produce* the fleet into the **Fleet** context.
- **Customers** search and book through **Booking**, and pay through **Payments**.
- **Payments** calls back into Booking to confirm or cancel — that's the saga.
- Both Booking and Payments publish **domain events** to **Notification**, which customers read.
- **Identity** on the top right issues tokens and serves the admin dashboard; it reads statistics from the others, fail-soft.

Two things to notice. **Fleet and Notification call nothing** — they can be deployed entirely on their own. **Nothing calls Identity at runtime** — which is precisely why it's safe to host the admin dashboard there: a service with no callers can fan out without creating a cycle.

### 3c. Domain-Driven Design and the platform — ~2 min

**[Slide: Domain-Driven Design]**

**What:** we drew service boundaries along bounded contexts, and used DDD relationship patterns between them.

- **Identity publishes a language** — the JWT claims. Every context understands `role` and `userId` without asking.
- **Fleet is an open host service** — it exposes a stable internal API for car details.
- **Booking protects itself with an anti-corruption layer** — it *snapshots* make, model, type and daily rate onto the booking when it's made.
  *Why:* this removed two cross-service reads outright, and a booking now survives the car being repriced or deleted. It's not denormalisation for speed; it's a boundary.
- **Payments and Booking are customer/supplier** — Payments depends on Booking's confirm and cancel, which Booking guarantees are idempotent.
- **Notification is a conformist consumer** — it accepts events as published and de-duplicates them by `event_id`.

**How the platform meets producers and consumers:**
- Producers write through public APIs that only ever return DTOs — never database entities. *Why:* the original codebase serialised password hashes into API responses by returning entities directly. DTOs make that impossible.
- Services produce **domain events through a transactional outbox**: the business change and its event commit in the same transaction, and a relay delivers afterwards. *Why:* writing the event separately is a second, non-atomic write — one of them will eventually be lost.
- Service-to-service endpoints live under `/internal`, are blocked at the edge, **and** require a service token. *Why both:* locally, every service port is published, so the edge alone isn't enough.

---

## 4. Physical Architecture & Design — 7 min (+2 Q&A)

### 4a–b. Key decisions and the physical architecture — ~2.5 min

**[Diagram: `deployment.html` — AWS deployment and cost layers]**

**What:** a request enters CloudFront, which serves the React app from S3 and forwards `/api/*` to a single Application Load Balancer. The ALB routes by path to five services on EKS, running on 2–5 spot nodes. They talk to RDS PostgreSQL 17 in private subnets. Secrets come from SSM, images from ECR, and logs go to CloudWatch.

The decisions, and why:

- **CloudFront is the only public entry, for both the app and the API.** Same origin means no CORS, no certificate to buy and no domain to register.
- **One ALB, not five.** All five ingresses join one group. Five load balancers would cost five times as much for no benefit.
- **Spot nodes and no NAT gateway.** About 70% cheaper. It's safe because every pod is stateless; all state is in RDS, so a reclaimed node loses nothing.
- **Two layers with two lifetimes.** The *persistent* layer — CloudFront, S3, ECR, secrets — costs about 80 cents a month and stays up. The *ephemeral* layer — EKS, ALB, RDS — costs about 21 cents an hour and is created daily.
  *Why:* a student account can't afford an always-on cluster. And a **lease Lambda** tears the ephemeral layer down when its deadline passes, so a forgotten cluster can't become a $155 month.
- **Separate dev and prod environments**, each with its own cluster, namespace, URL, database and logs. *Why:* so a load test on dev can never starve a demo on prod — and our own testing proved that matters.

**Scaling:** each service has a Horizontal Pod Autoscaler (reservation 2–10 pods, catalog 3–6), and the Cluster Autoscaler adds nodes when pods can't be placed. Notification deliberately has no HPA: it's a queue consumer, and when a consumer falls behind it's blocked, not busy, so CPU would never trigger scaling.

### 4c. Persistence design — ~2 min

**[Diagram: `persistence.html` — schema per service]**

**What:** one PostgreSQL instance per environment, five schemas, five roles. Foreign keys are mandatory *inside* a schema and forbidden *across* them — across, we store a plain id. Flyway owns every schema; Hibernate only validates.

**Why one instance and not five?** Cost. Isolation is enforced by roles and grants, not by separate servers — and any service can move to its own database by changing a connection string, with no schema change.

The part we're proudest of is that **correctness lives in the database, not in application code:**

- **No double-booking:** `booking_day` holds one row per car per rented day, with the primary key `(car_id, day)`. *Why not just check availability first?* Check-then-insert leaves a window between the two. A primary key has no window. Our test races eight threads for one car and asserts exactly one wins.
- **Safe payment retries:** `idempotency_key` is unique, so a retried request returns the original payment.
- **At most one successful payment per booking:** a generated column is non-null only for successful payments, with a unique index on it. Unlimited declined attempts are fine; a second success is refused by the database.
- **Outbox tables** sit in the producers' schemas and commit with the business change; Notification's unique `event_id` absorbs duplicates.

### 4d. Representative use case: book and pay — ~2.5 min

**[Diagram: `book-and-pay.html` — sequence]**

We chose "customer books and pays" because it exercises almost every decision we've described.

**Book:**
1. The SPA posts to reservation. Reservation validates the JWT *locally* — no call to identity.
2. It fetches the car from catalog — **with no database transaction open**. *Why:* holding a connection across a network call starves the pool. This exact bug capped us at under 50 actions per second until load testing found it.
3. In **one transaction** it inserts the booking with the car snapshot, the `booking_day` rows, and the outbox event. If another customer got there first, the primary key rejects it and the customer gets a `409`.
4. Returns `201 PENDING_PAYMENT`.

**Pay — the saga:**
5. The SPA posts to payment with an `Idempotency-Key`.
6. Payment checks the booking is awaiting payment.
7. It writes the payment as `INITIATED` **before** charging. *Why:* if we saved only after the gateway answered, a crash in between would mean money moved with nothing recorded.
8. It charges, records `SUCCESS` plus an outbox row, then calls reservation's idempotent **confirm**.
9. If confirm is refused, payment **compensates** — refunds and cancels. If confirm times out, a **reconciliation sweeper** retries later; that's safe because confirm is idempotent.

**Notify:** the outbox relay delivers asynchronously. Notification can be down for an hour and no booking fails.

---

## 5. DevOps & Development Lifecycle — 6 min (+1 Q&A)

### 5a. Source code and artifacts — ~1.5 min

**[Slide: Source & artifact management]**

**What:** three branch types. `feature/**` for work, `dev` for integration, `main` for production. Both `dev` and `main` are protected — pull request plus green CI. A workflow automatically opens pull requests from `main` into every open feature branch so nobody drifts.

**Why this and not trunk-based?** We have two environments, and we wanted a merge to `dev` to deploy to dev without ever touching the prod demo. The branch *is* the environment selector.

**Monorepo, but no shared library.** Each service is a standalone Maven project with its own Docker build context. *Why:* a shared library would be invisible to each service's isolated image build, and it would couple their release cycles. A small amount of duplication buys independent buildability.

**Artifacts:** one image per service in ECR, tagged with both the short and full commit SHA, so every running pod traces back to an exact commit.

### 5b. The pipeline and procedures — ~2 min

**[Diagram: `cicd.html` — branch to running pods]**

Every push and pull request runs CI:
1. **Test** — all five services in parallel, against a real PostgreSQL in Testcontainers, with JaCoCo coverage sent to SonarCloud. *Why real Postgres, not H2?* H2 behaves differently on reserved words and JSON; it already bit us once. Tests are also the only place our migrations and entity mappings are validated together.
2. **CodeQL** static analysis and **OWASP Dependency-Check** for known-vulnerable libraries. Both blocking.

On success, a merge to `dev` or `main` triggers deploy: build the five images, push to ECR, then run **`make aws-deploy`** — five Helm releases, the frontend to S3, and a CloudFront refresh.

**The key design choice:** CI runs the **same `make` command a developer runs**. *Why:* a deployment reimplemented in YAML drifts from the one people test locally. One script, two callers.

**Procedures:** `make aws-up-dev` provisions in about 20 minutes; `aws-deploy` ships code in about 3; `aws-down` dumps the database and destroys everything billed by the hour. `aws-status` shows who holds the cluster and how long is left on the lease.

### 5c. Technical findings — ~2.5 min

**[Slide: Findings only visible under load]**

This is the most important slide in DevOps. **All 71 tests were green.** Then we load-tested, and the system collapsed at 50 actions per second — **with CPU under 1%.**

What we found:
1. **The audit log used a nested transaction**, so every booking took a *second* connection while holding the first. With a pool of three, three concurrent bookings deadlocked. *Fix:* the audit joins the caller's transaction.
2. **Connections were held across HTTP calls.** *Fix:* make the call with no transaction open.
3. **No timeouts**, so overload queued for 30–60 seconds and never drained. *Fix:* a 2-second pool timeout and HTTP timeouts — overload now fails fast with a 503 and recovers.
4. **Every error came back as 401**, hiding the real failures — 11,000 of them in one run. *Fix:* let Spring's error dispatch through security.
5. **The Cluster Autoscaler had never worked** — wrong version for the cluster, so 8 pods sat Pending for 15 minutes. *Fix:* pinned the version, and the script now refuses a mismatch.

**Why this matters for scalability:** blocked services don't use CPU, so the CPU-based autoscaler *could never have helped* until these were fixed. **Result: from under 50 to at least 400 actions per second, with no new infrastructure.**

And the pipeline taught us some lessons too: our dev deploys were silently shipping `main`'s code, and for a while prod could deploy even when CI was red. Both are fixed, and both are the kind of bug you only find by actually running the pipeline.

---

## 6. Demo — 8 min (+2 Q&A)

> Environment up ≥30 minutes before. Backup screen recording and `docs/perf/*.png` ready. If the live demo fails, switch to the recording without apologising at length.

### 6a. Use cases — ~4 min

Narrate each in one sentence of *what* and one of *why it matters*:

1. **Customer flow** — browse, search availability, book, pay, see the notification.
   "This is the happy path through all five services."
2. **Two concurrent bookings for the same car** — one gets `201`, the other `409`.
   "That `409` comes from a primary key, not from our code."
3. **Replay the payment with the same Idempotency-Key** — the original payment comes back.
   "A customer double-clicking Pay is never charged twice."
4. **Staff puts a car into maintenance** — it disappears from availability.
   "Same transaction, so there's no window where it's bookable."
5. **Admin dashboard with payment-service stopped** — `partial: true`, revenue `null`.
   "It tells you it doesn't know, instead of telling you revenue is zero."

### 6b. Quality attributes — ~4 min

**Performance** — **[Slide: <50 → ≥400 actions/s]**
"Locally, the same hardware went from failing at 50 actions per second to passing at 400 with a p95 of 18 milliseconds. On AWS at 100 per second: p95 56 ms, zero errors."

**Scalability** — live: `watch kubectl get hpa,nodes` + `make perf-spike TARGET=aws`. Backup: **[Slide: capacity follows demand]**
"Watch the CPU column cross 60%... reservation scales from 2 to 10 pods... pods go Pending... the Cluster Autoscaler adds a third node... and per-pod CPU falls from 200% to 60% *while load is still at peak*. Capacity followed demand. When the load stops, replicas hold for five minutes, deliberately, so spot nodes don't thrash."

**Maintainability** — show a pipeline run in GitHub Actions: test, CodeQL, OWASP, build, deploy, and the new image SHA running.

**Close with honesty** — **[Slide: Known limits]**
"Three limits we found and can explain. CPU is the wrong scaling signal when the bottleneck is connections — the fix is scaling on pending connections with KEDA. The `db.t4g.micro` database is the ceiling. And autoscaler maximums cap peak throughput. These are findings, not failures — we know where the next bottleneck is because we measured it."

**[Slide: Thank you]**

"To summarise: correctness enforced by the database, a dependency graph with no cycles, and scalability we proved with evidence rather than claimed. Happy to take questions."

---

## Rehearsal checklist

- [ ] Fill in presenters and per-member effort
- [ ] Time each section aloud; cut 3c and 5c first if over
- [ ] Re-run the AWS stress test on the latest build and update the numbers
- [ ] Record the backup demo
- [x] Diagrams placed in the deck
- [ ] Fill in effort hours on slide 7
