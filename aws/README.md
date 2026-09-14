# RentEZ on AWS

Everything here is optional. **Local development needs none of it** — `make up`
runs the whole stack on your laptop for free, and that is still the right place
to write code. This directory is for the days you need the thing running on real
infrastructure: an integration test against a shared URL, the graded load test,
or the demo.

The design goal is narrow and worth stating plainly: **the environment should
cost almost nothing when nobody is using it, and anyone on the team should be
able to bring it back without asking permission.**

---

## The one number that shapes everything

The EKS control plane costs **$0.10/hour, and there is no way to stop it.** You
either have a cluster and pay, or you delete it. Everything else in the design
follows from that.

| State | Cost |
|---|---|
| Running (2 spot nodes, database, one ALB) | **~$0.21/hr** |
| Running during a load test (5 nodes) | ~$0.31/hr |
| After `make aws-down` | **~$0.80/month** |
| Left up 24/7 — what we are avoiding | ~$155/month |

Sixty hours of use in a month is roughly **$13**. The same environment left
running is **$155**. That gap is the whole point of the tooling below.

Two decisions save most of the rest:

- **No NAT gateway.** A NAT gateway is ~$43/month in `ap-southeast-1` whether or
  not a byte flows through it — more than the control plane. Worker nodes sit in
  public subnets and the security groups do the work instead. Correct for a test
  environment, wrong for production, and worth saying so in the report.
- **SSM Parameter Store, not Secrets Manager.** Standard-tier parameters are
  free; Secrets Manager is $0.40 per secret per month. The architecture document
  says Secrets Manager — this is a deliberate deviation.

---

## First time on a machine

```bash
make aws-check
```

Reports every missing tool at once rather than stopping at the first. You need
`aws`, `eksctl`, `kubectl`, `helm`, `envsubst`, `python3`, `git` and `npm`.

```bash
brew install awscli kubectl helm gettext eksctl && brew link --force gettext
```

### Authenticating the CLI

`aws configure` asks four questions. Only the first two are hard to find.

| Prompt | Value | Where it comes from |
|---|---|---|
| `AWS Access Key ID` | starts `AKIA…`, 20 chars | IAM → Users → *your user* → Security credentials → Create access key |
| `AWS Secret Access Key` | 40 chars | shown **once**, on the same screen. Not recoverable — make a new key if you lose it |
| `Default region name` | `ap-southeast-1` | every cost figure and doc here assumes Singapore; `make aws-check` warns if it differs |
| `Default output format` | `json` | the scripts parse JSON in places |

Answers are written to `~/.aws/credentials` and `~/.aws/config`.

**Do not create an access key for the root user.** A fresh AWS account signs you
in as root, and AWS deliberately makes root keys awkward because they cannot be
scoped or easily revoked. Before anything else:

1. Turn on MFA for root (IAM → Security credentials).
2. Create an IAM user — IAM → Users → Create user, then **Attach policies
   directly → `AdministratorAccess`**.
3. Create the access key against *that* user, choosing the **Command Line
   Interface (CLI)** use case.

`AdministratorAccess` is broader than it should be, and that is a considered
choice rather than laziness: `make aws-bootstrap` creates IAM roles, a VPC,
CloudFront, Lambda and EventBridge, and `eksctl` needs more again. Scoping it
down properly is a worthwhile exercise for the report, but not one to attempt on
the day you are trying to get a cluster up.

Verify before spending anything:

```bash
aws sts get-caller-identity && make aws-check
```

The ARN it prints should end in the IAM user you just made — if it says
`:root`, stop and go back to step 2.

**At the end of the project, delete the access key** (IAM → Users → Security
credentials → Actions → Delete). A long-lived key on a laptop is the one piece
of this setup with no expiry date. If you would rather avoid static keys
entirely, `aws configure sso` with IAM Identity Center issues short-lived
credentials instead; it is the better practice and the more involved setup.

## First time in an AWS account

```bash
make aws-bootstrap NOTIFY_EMAIL=you@u.nus.edu
```

Roughly ten minutes, and **creates nothing that bills by the hour**: budget
alarms, the two secrets, a VPC, five ECR repositories, two S3 buckets, a
CloudFront distribution, the DynamoDB tables and SQS queues, and the reaper.

It prints your permanent URL. Bookmark it — it survives every teardown and only
changes if you run `make aws-nuke`.

Confirm the budget confirmation email when it arrives, or the alarms are inert.

## Every day

```bash
make aws-up          # ~20 min, 4-hour lease
make aws-status      # what is running, burn rate, time left
make aws-down        # dump to S3, then destroy everything hourly-billed
```

Useful variants:

```bash
make aws-up TTL_HOURS=8      # longer lease for a demo day
make aws-up TAG=abc1234      # deploy a specific image
make aws-up RESTORE=0        # start from an empty database
make aws-down KEEP_DB=1      # keep RDS running (~$13/month) for tomorrow
```

### Redeploying without rebuilding the environment

`make aws-up` is two things bolted together: it *provisions* (database, cluster,
add-ons, schema) and then it *deploys* (five Helm releases, the frontend bundle,
the CloudFront origin). Only the second half changes when the code changes, and
it is available on its own:

```bash
make aws-deploy              # ~3 min, deploys the current commit
make aws-deploy TAG=abc1234  # ~3 min, deploys a specific tag
```

It refuses to run if there is no cluster — it will not resurrect a
$0.21/hour environment for you — and it never touches the lease, so redeploying
does not buy you more time.

On the shared account this is what the pipeline runs. Merging to main builds the
five images and then deploys them automatically; see below.

---

## Continuous deployment (shared account only)

Four workflows. `ci.yml` tests, and on success one of the two branch workflows
calls the shared `deploy.yml`:

```
Rentez CI ──success──> Deploy Dev  (dev)  ─┐
                                            ├─> Rentez Deploy ─> ECR ─> EKS
          ──success──> Deploy Production ──┘        (deploy.yml)
                       (main)
```

`deploy.yml` builds the five service images to ECR and then runs
`make aws-deploy`. It is dormant until `AWS_DEPLOY_ROLE_ARN` is set, so
per-member accounts are unaffected by any of this.

The daily loop on the shared account becomes: someone runs `make aws-up` once in
the morning, and from then on **merging deploys**. Nobody else needs AWS
credentials to ship.

**Both branches deploy to the same cluster.** There is one environment, not a
dev and a prod, so the most recent deploy is what is live regardless of which
branch produced it. Each run's step summary names its branch, which is the only
way to work out who overwrote whom.

### Why OIDC and not `GITHUB_TOKEN`

An earlier version of `deploy.yml` pushed images to GitHub Container Registry.
That proved the artifact publishing worked and was then dropped: EKS pulls from
ECR using the node instance profile, so there is **no registry credential in the
cluster at all**. GHCR would have needed one.

With GHCR gone, every leg of the pipeline talks to AWS:

| Leg | Credential |
|---|---|
| Push image → ECR | OIDC role |
| Deploy → EKS | OIDC role, plus an EKS access entry (step 4 below) |
| EKS pull ← ECR | node instance profile — nothing to configure |

`GITHUB_TOKEN` is a GitHub credential; AWS will not accept it under any
configuration. So it has no role here, and `packages: write` is gone from all
three workflows. The genuine alternative to OIDC is a long-lived AWS access key
pair in GitHub secrets, and it loses on every axis: it never expires, it sits in
a secret store indefinitely, it must be rotated by hand, and it has to be
revoked when a teammate leaves. OIDC mints a fresh credential per run and there
is nothing to leak.

### One-time setup

1. **OIDC provider** — the commands are in the header of
   `.github/workflows/deploy.yml`.

2. **One role**, trusting only this repository through that provider, with the
   policy in `aws/iam/ci-deploy-policy.json`:

   ```bash
   aws iam put-role-policy --role-name rentez-ci-deploy \
     --policy-name rentez-deploy \
     --policy-document file://aws/iam/ci-deploy-policy.json
   ```

   Both jobs assume it. Be aware that it is cluster-admin on EKS — which is why
   the policy is scoped statement by statement rather than reaching for
   `AdministratorAccess`.

3. **Repository variables** (Settings → Secrets and variables → Actions →
   Variables — *variables*, not secrets; none of these are sensitive, and there
   are deliberately no long-lived AWS keys anywhere):

   | Variable | Value |
   |---|---|
   | `AWS_DEPLOY_ROLE_ARN` | the role from step 2 |
   | `AWS_REGION` | `ap-southeast-1` |

4. **Give the role access to the cluster.** IAM permission is not cluster
   permission: `eksctl` makes only the creating principal a cluster admin, so
   the pipeline needs an EKS *access entry* as well. `make aws-up` creates one
   when told the role ARN, and must be told every time, because the cluster is
   ephemeral:

   ```bash
   export CI_ROLE_ARN=arn:aws:iam::<account>:role/rentez-ci-deploy
   make aws-up
   ```

   Put that `export` in the shared account's shell profile and forget about it.
   Skip it and deploys fail with `You must be logged in to the server
   (Unauthorized)`, which mentions neither IAM nor the missing access entry.

### A red Deploy run is often correct

The environment holds a four-hour lease and the reaper tears it down when it
expires, so most merges outside a working session land with nothing to deploy
to. The run fails in about thirty seconds with `no cluster 'rentez' — someone
has to run 'make aws-up' first`. That is the honest answer, not a broken
pipeline. Bring the environment up and re-run the workflow.

---

## Why it cannot quietly bill you for a month

`make aws-up` writes a deadline to SSM at `/rentez/env/expires-at`. A Lambda
checks it **every five minutes** and, once it passes, performs one step of
teardown per invocation: load balancers, then node groups, then the cluster,
then the database. No waits, no timeouts, idempotent, and free.

This exists because the realistic failure in a student project is not a bad
architecture. It is somebody closing their laptop on a Friday. `make aws-down`
is the polite path; the reaper is what happens when nobody takes it.

`make aws-status` shows `lease: DISARMED` in red whenever something is running
with no deadline set. If you ever see that, set one.

---

## How the pieces fit

```
Browser ──HTTPS──▶ CloudFront (permanent, free at rest)
                     /       →  S3 bucket with the React build
                     /api/*  →  ALB  (origin rewritten by every aws-up)
                                 │  security group admits CloudFront only
                     ┌───────────▼────────────┐
                     │ ONE ALB, IngressGroup  │   5 Ingresses, not 5 ALBs
                     │ "rentez", path routing │
                     └───────────┬────────────┘
   EKS · 2→5 t3.large SPOT nodes in PUBLIC subnets (no NAT gateway)
     account 2→4 · catalog 2→6 · reservation 2→10 · payment 2→4 · notification 1
                                 │ JDBC
                     ┌───────────▼────────────────────────┐
                     │ RDS PostgreSQL 16 · db.t4g.micro   │
                     │ private subnets · 5 schemas, 5 roles│
                     └────────────────────────────────────┘
```

**The single-origin trick is what makes "no domain name" work.** Because
`/api/*` is served from the same CloudFront distribution as the app, the browser
only ever speaks HTTPS to `*.cloudfront.net` using CloudFront's own free
certificate. No mixed content, no ACM certificate, no domain to buy — and the
frontend needs no API base URL compiled into it, so a recreated ALB does not
require a frontend rebuild. See the long note in `frontend/vite.config.js`.

### Layers

| File | Lifetime | Cost at rest |
|---|---|---|
| `cloudformation/00-guardrails.yaml` | forever, survives `aws-nuke` | $0 |
| `cloudformation/10-persistent.yaml` | until `aws-nuke` | ~$0.80/mo |
| `cloudformation/20-database.yaml` | `aws-up` → `aws-down` | — |
| `eksctl/cluster.yaml` | `aws-up` → `aws-down` | — |

`eksctl` is still CloudFormation: it generates and deletes
`eksctl-rentez-*` stacks. Choosing it over hand-written EKS YAML saves several
hundred lines of IRSA and launch-template boilerplate without changing the
tooling decision.

---

## Backups

`make aws-down` runs `pg_dump` of all five schemas to
`s3://rentez-backups-<account>/dumps/<timestamp>.sql.gz`, verifies the uploaded
object is a plausible size, and **refuses to delete anything if the dump fails.**
`make aws-up` restores the newest dump automatically if one exists; otherwise
Flyway and `SPRING_PROFILES_ACTIVE=seed` build a fresh world.

There is deliberately **one** backup mechanism, not two. The RDS stack's
`DeletionPolicy` is `Delete` rather than `Snapshot`, because one path that always
runs and is verified beats two that are each half-trusted. A plain SQL dump is
also portable — it restores into any Postgres 16, where a snapshot only restores
into RDS.

RDS is in private subnets with no public route, so the dump runs from a
throwaway pod inside the cluster. **This is why `aws-down` backs up before it
deletes the cluster:** afterwards there is no route to the data at all.

```bash
aws s3 ls s3://rentez-backups-$(aws sts get-caller-identity --query Account --output text)/dumps/
```

---

## Per-member accounts

Everyone runs these commands against **their own** AWS account with their own
credentials. There are no shared secrets and nothing to configure in the
repository, which is what makes this work without a shared account.

The consequence is worth being clear about: **there are four environments, not
one.** Each teammate gets their own CloudFront URL, cluster and database. Nobody
can send a colleague a link to *the* environment.

The recommendation is to keep personal accounts for day-to-day work and create
**one shared account for the graded load test and the demo**, funded by its
signup credits. Every template here is account-agnostic, so supporting both costs
nothing: run `make aws-bootstrap` once in the shared account and the same
commands work.

If all four of you run `make aws-up` at once, you are paying four control planes.

`.github/workflows/deploy.yml` is the one piece that assumes a single account.
It stays dormant until `AWS_DEPLOY_ROLE_ARN` is set (see *Continuous deployment*
above); until then use `make aws-images` to build and `make aws-deploy` to
deploy, both of which run against whichever account you are authenticated to.

---

## Things that will bite

**A Deploy workflow run fails with `You must be logged in to the server
(Unauthorized)`.** The IAM role is fine; the cluster has never been told about
it. `eksctl create cluster` grants admin only to the principal that ran it, and
the access entry has to be recreated with every cluster. Re-run `make aws-up`
with `CI_ROLE_ARN` set, or add it by hand:

```bash
eksctl create accessentry --cluster rentez --principal-arn <role-arn> \
  --access-policy "arn:aws:eks::aws:cluster-access-policy/AmazonEKSClusterAdminPolicy,accessScope={type=cluster}"
```

**`kubectl get hpa` shows `<unknown>` for CPU.** metrics-server did not install.
The HPAs cannot scale without it, and nothing else reports an error.

**An Ingress never gets an ADDRESS.** Usually the AWS Load Balancer Controller.
`kubectl -n kube-system logs deploy/aws-load-balancer-controller`. If it
complains about discovering subnets, check the `kubernetes.io/role/elb` tags on
the public subnets.

**`exec format error` in a pod.** An arm64 image built on an Apple Silicon Mac
running on x86 nodes. `make aws-images` passes `--platform linux/amd64`; if you
built by hand, that is the flag you missed.

**More than one ALB exists.** An Ingress lost its
`alb.ingress.kubernetes.io/group.name: rentez` annotation. Each extra ALB is
about $16/month and nothing warns you.

**`/api/reservations/internal/...` returns data instead of 404.**
`deploy/k8s/00-internal-deny.yaml` was not applied. An ALB path rule for
`/api/reservations` matches the `/internal` paths beneath it, unlike the local
nginx gateway which explicitly shadows them. Without that manifest the saga
confirm/cancel and stats endpoints are public.

---

## Status

The application layer (Phase 1 — the PostgreSQL migration) is **verified**: 71
backend tests, 33/33 end-to-end smoke checks against a clean volume.

**The AWS layer in this directory has not been deployed.** It is statically
validated — YAML parses, the eksctl template renders, `helm lint` and
`helm template` pass for all five services, HPA bounds and path prefixes match
the architecture document and `scripts/gateway.conf`, and every script passes
`bash -n` — but no part of it has been run against a real account. Expect the
first `make aws-bootstrap` and `make aws-up` to need iteration, and budget an
afternoon for it. Run `make aws-status` liberally while you do.
