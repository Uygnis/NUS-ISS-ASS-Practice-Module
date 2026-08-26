# RentEZ — one entry point for local development
# rentez_startup_project_v1_1_20260810_1421SGT
#
# Every target has a plain `docker compose` equivalent — see
# docs/ch01.startup-project.adoc, "Without make".
#
#   make check   verify tooling            make down    stop, keep data
#   make infra   Mode A: infra only        make clean   stop, DELETE data
#   make up      Mode B: everything        make db-check verify DB connections
#
# AWS (see aws/README.md). Local development needs none of this.
#   make aws-check      verify tooling and credentials
#   make aws-bootstrap  once per account; creates only free things
#   make aws-up         cluster + database, ~20 min, ~$0.21/hr
#   make aws-status     what is running, and when it expires
#   make aws-down       dump to S3, then destroy everything hourly-billed

SHELL         := /bin/bash
.DEFAULT_GOAL := help

DC       := docker compose
APP      := $(DC) --profile app
SERVICES := account-service reservation-service catalog-service notification-service payment-service

# service:port:schema — used by db-check
TARGETS := account-service:8081:rentez_auth \
           reservation-service:8082:rentez_booking \
           catalog-service:8083:rentez_fleet \
           notification-service:8084:rentez_notification \
           payment-service:8085:rentez_payment

POSTGRES_PASSWORD ?= rentez
POSTGRES_DB       ?= rentez

# Every psql below runs inside the container as the superuser. ON_ERROR_STOP=1
# so a failed statement is a failed target, rather than a zero exit with the
# error printed halfway up the scrollback.
PSQL := $(DC) exec -T postgres psql -v ON_ERROR_STOP=1 -U postgres -d $(POSTGRES_DB)

.PHONY: help
help: ## Show this help
	@echo ""
	@echo "RentEZ — local development"
	@echo "--------------------------"
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) \
	  | awk 'BEGIN {FS=":.*?## "}; {printf "  \033[36m%-12s\033[0m %s\n", $$1, $$2}'
	@echo ""

# ============================================================== preflight
.PHONY: check
check: ## Verify tooling, Docker memory, and that .env matches the init SQL
	@fail=0; \
	printf "\n  Required tools\n"; \
	for t in docker java mvn node git; do \
	  if command -v $$t >/dev/null 2>&1; then \
	    printf "    \033[0;32mok\033[0m   %-6s %s\n" "$$t" "$$($$t --version 2>&1 | head -n1 | cut -c1-60)"; \
	  else \
	    printf "    \033[0;31mMISS\033[0m %-6s not installed\n" "$$t"; fail=1; \
	  fi; \
	done; \
	if ! docker info >/dev/null 2>&1; then \
	  printf "    \033[0;31mMISS\033[0m docker daemon not running — start Docker Desktop\n"; fail=1; \
	fi; \
	printf "\n  Java version\n"; \
	jv=$$(java -version 2>&1 | head -n1 | sed -E 's/.*"([0-9]+).*/\1/'); \
	if [ "$$jv" = "21" ]; then printf "    \033[0;32mok\033[0m   Java 21\n"; \
	else printf "    \033[0;33mwarn\033[0m Java $$jv — this project targets 21\n"; fi; \
	printf "\n  Docker memory\n"; \
	mem=$$(docker info --format '{{.MemTotal}}' 2>/dev/null || echo 0); \
	gb=$$(( mem / 1024 / 1024 / 1024 )); \
	if [ "$$gb" -ge 8 ]; then printf "    \033[0;32mok\033[0m   $${gb} GB\n"; \
	elif [ "$$gb" -gt 0 ]; then \
	  printf "    \033[0;33mwarn\033[0m only $${gb} GB. Raise to 8 GB in Docker Desktop >\n"; \
	  printf "         Settings > Resources, or containers will be OOM-killed\n"; \
	  printf "         with no useful error message.\n"; fi; \
	printf "\n  Configuration\n"; \
	if [ ! -f .env ]; then cp .env.example .env; \
	  printf "    \033[0;32mok\033[0m   created .env from .env.example\n"; \
	else printf "    \033[0;32mok\033[0m   .env present\n"; fi; \
	$(MAKE) --no-print-directory check-passwords || fail=1; \
	echo ""; \
	if [ "$$fail" -eq 0 ]; then \
	  printf "  \033[0;32mReady. Next: make up\033[0m\n\n"; \
	else \
	  printf "  \033[0;31mFix the items above, then re-run: make check\033[0m\n\n"; exit 1; fi

.PHONY: check-passwords
check-passwords: ## Confirm .env passwords match db/init/01-schemas.sql
	@drift=0; \
	for pair in AUTH_DB_PASSWORD:auth_user FLEET_DB_PASSWORD:fleet_user \
	            BOOKING_DB_PASSWORD:booking_user PAYMENT_DB_PASSWORD:payment_user \
	            NOTIFICATION_DB_PASSWORD:notification_user; do \
	  var=$${pair%%:*}; user=$${pair##*:}; \
	  envpw=$$(grep -E "^$$var=" .env 2>/dev/null | head -1 | cut -d= -f2-); \
	  sqlpw=$$(grep -E "CREATE ROLE $$user LOGIN PASSWORD" \
	            db/init/01-schemas.sql 2>/dev/null | sed -E "s/.*PASSWORD '([^']*)'.*/\1/"); \
	  if [ -n "$$envpw" ] && [ -n "$$sqlpw" ] && [ "$$envpw" != "$$sqlpw" ]; then \
	    printf "    \033[0;31mMISMATCH\033[0m %s: .env='%s' but 01-schemas.sql='%s'\n" "$$user" "$$envpw" "$$sqlpw"; \
	    drift=1; \
	  fi; \
	done; \
	if [ "$$drift" -eq 0 ]; then \
	  printf "    \033[0;32mok\033[0m   .env passwords match db/init/01-schemas.sql\n"; \
	else \
	  printf "         Fix one to match the other, then: make clean && make up\n"; exit 1; fi

# ================================================================== run
.PHONY: infra
infra: ## Mode A — Postgres, DynamoDB, Adminer, gateway (run services in your IDE)
	$(DC) up -d
	@$(MAKE) --no-print-directory wait-postgres
	@echo ""
	@# Read the port back from Docker rather than echoing a constant: POSTGRES_PORT
	@# lives in .env, which make does not parse, so a remapped port printed here
	@# as 5432 sends people to a database that is not listening.
	@echo "  Postgres   localhost:$$($(DC) port postgres 5432 | cut -d: -f2)    (Adminer: http://localhost:$$($(DC) port adminer 8080 | cut -d: -f2))"
	@echo "  DynamoDB   localhost:8000"
	@echo "  Gateway    http://localhost:8080"
	@echo ""
	@echo "  Now run the services from IntelliJ. They default to localhost."
	@echo ""

.PHONY: up
up: ## Mode B — the whole stack, backend and frontend together
	$(APP) up -d --build
	@$(MAKE) --no-print-directory wait-postgres
	@echo ""
	@echo "  Frontend   http://localhost:3000"
	@echo "  Gateway    http://localhost:8080"
	@echo "  Adminer    http://localhost:8090"
	@echo "  account 8081 | reservation 8082 | catalog 8083 | notification 8084 | payment 8085"
	@echo ""
	@echo "  Services need ~60s to boot. Check with: make db-check"
	@echo ""

.PHONY: wait-postgres
wait-postgres:
	@printf "  waiting for PostgreSQL"
	@for i in $$(seq 1 60); do \
	  if [ "$$($(DC) ps --format '{{.Service}} {{.Health}}' 2>/dev/null | grep '^postgres ' | awk '{print $$2}')" = "healthy" ]; then \
	    echo " — ready"; exit 0; \
	  fi; \
	  printf "."; sleep 2; \
	done; \
	echo ""; echo "  PostgreSQL did not become healthy. Check: make logs S=postgres"; exit 1

.PHONY: down
down: ## Stop everything, KEEP database data
	$(APP) down

.PHONY: clean
clean: ## Stop everything and DELETE all data volumes
	$(APP) down -v
	@echo '  Volumes removed. The next "make up" re-runs db/init/01-schemas.sql.'

.PHONY: restart
restart: ## Rebuild and restart one service (make restart S=catalog-service)
	$(APP) up -d --build $(S)

.PHONY: logs
logs: ## Tail logs (make logs S=catalog-service; omit S for everything)
	$(APP) logs -f $(S)

.PHONY: ps
ps: ## Show container status and health
	@$(APP) ps

# ============================================================= database
.PHONY: db-check
db-check: ## Verify every service can reach its own schema
	@echo ""
	@printf "  %-22s %-7s %-8s %s\n" SERVICE PORT STATUS SCHEMA
	@printf "  ---------------------- ------- -------- --------------------\n"
	@for t in $(TARGETS); do \
	  svc=$$(echo $$t | cut -d: -f1); port=$$(echo $$t | cut -d: -f2); schema=$$(echo $$t | cut -d: -f3); \
	  body=$$(curl -s --max-time 3 http://localhost:$$port/actuator/health 2>/dev/null); \
	  if [ -z "$$body" ]; then st="DOWN"; \
	  elif command -v jq >/dev/null 2>&1; then \
	    st=$$(echo "$$body" | jq -r '.components.db.status // (if .status then "NO-DB" else "?" end)' 2>/dev/null || echo "?"); \
	  else \
	    case "$$body" in *'"db"'*) st="UP";; *'"UP"'*) st="NO-DB";; *) st="?";; esac; \
	  fi; \
	  case "$$st" in \
	    UP)    c="\033[0;32m";; \
	    NO-DB) c="\033[0;33m";; \
	    *)     c="\033[0;31m";; \
	  esac; \
	  printf "  %-22s :%-6s $$c%-8s\033[0m %s\n" "$$svc" "$$port" "$$st" "$$schema"; \
	done
	@echo ""
	@echo "  UP     = datasource reachable"
	@echo "  NO-DB  = service is running but exposes no db health component."
	@echo "           Add a JDBC driver + spring.datasource.* and set"
	@echo "           management.endpoint.health.show-details=when_authorized"
	@echo "  DOWN   = not responding. See: make logs S=<service>"
	@echo ""

.PHONY: db-schemas
db-schemas: ## List the schemas and roles that actually exist
	@$(PSQL) -c "\\dn" \
	         -c "SELECT rolname, rolcanlogin FROM pg_roles WHERE rolname LIKE '%\\_user' ORDER BY rolname;"

.PHONY: db-isolation
db-isolation: ## Prove a service user CANNOT read another schema (for the report)
	@echo ""
	@echo "  Expected: auth_user is denied access to rentez_booking."
	@echo ""
	@$(DC) exec -T -e PGPASSWORD=auth_pw postgres \
	  psql -U auth_user -d $(POSTGRES_DB) \
	  -c "SELECT COUNT(*) FROM rentez_booking.booking;" 2>&1 | sed 's/^/    /' || true
	@echo ""
	@echo "  A 'permission denied for schema rentez_booking' above is the CORRECT result."
	@echo "  It is the evidence that schema-per-service isolation is real."
	@echo ""

.PHONY: psql
psql: ## Open a psql shell as the superuser
	@$(DC) exec postgres psql -U postgres -d $(POSTGRES_DB)

.PHONY: seed
seed: ## Load demo data from db/seed/*.sql
	@for f in db/seed/*.sql; do \
	  [ -e "$$f" ] || continue; \
	  echo "  applying $$f"; \
	  $(PSQL) -f - < "$$f" | sed 's/^/    /'; \
	done

.PHONY: dynamo
dynamo: ## List the local DynamoDB tables
	@$(DC) run --rm --no-deps -T dynamodb-init sh -c \
	  'aws dynamodb list-tables --endpoint-url http://dynamodb:8000'

# ================================================================ tests
.PHONY: test
test: test-backend test-frontend ## Run every test

.PHONY: test-backend
test-backend: ## Run all Java tests
	@for s in $(SERVICES); do \
	  echo ""; echo "  ==> $$s"; \
	  (cd services/$$s && ./mvnw -B test) || exit 1; \
	done

.PHONY: test-frontend
test-frontend: ## Run the frontend tests
	@cd frontend && npm test

.PHONY: build
build: ## Build all images without starting anything
	$(APP) build

# ================================================================== AWS
# Thin wrappers. All the logic lives in aws/scripts/ so it is reviewable as
# shell rather than as Make, and so CI can call the same code paths.
#
# Every target runs against YOUR OWN AWS account with YOUR OWN credentials.
# There are no shared secrets and nothing to configure in the repository - which
# is what makes this work with a per-member account model.

# Invoked through `bash` rather than executed directly. The exec bit survives a
# normal git clone, but not a zip download or some Windows checkouts, and
# "Permission denied" is a needlessly confusing first impression of the AWS
# tooling. This costs nothing and removes the failure mode.
AWS_SCRIPTS := bash aws/scripts

.PHONY: aws-check
aws-check: ## Verify AWS tooling and credentials before spending anything
	@$(AWS_SCRIPTS)/aws-check.sh

.PHONY: aws-bootstrap
aws-bootstrap: ## Once per account: budgets, secrets, VPC, ECR, CloudFront (all free)
	@NOTIFY_EMAIL="$(NOTIFY_EMAIL)" $(AWS_SCRIPTS)/aws-bootstrap.sh

.PHONY: aws-up
aws-up: ## Bring up the cluster and database (~20 min). TTL_HOURS=4 by default
	@TTL_HOURS="$(or $(TTL_HOURS),4)" TAG="$(TAG)" RESTORE="$(or $(RESTORE),1)" $(AWS_SCRIPTS)/aws-up.sh

.PHONY: aws-status
aws-status: ## What is running, the burn rate, and when the lease expires
	@$(AWS_SCRIPTS)/aws-status.sh

.PHONY: aws-down
aws-down: ## Dump to S3, then destroy everything billed by the hour
	@KEEP_DB="$(KEEP_DB)" SKIP_BACKUP="$(SKIP_BACKUP)" $(AWS_SCRIPTS)/aws-down.sh

.PHONY: aws-images
aws-images: ## Build and push all five service images to ECR
	@TAG="$(TAG)" $(AWS_SCRIPTS)/aws-images.sh

.PHONY: aws-nuke
aws-nuke: ## aws-down, then delete the persistent stack too. End of semester only.
	@$(AWS_SCRIPTS)/aws-nuke.sh
