#!/usr/bin/env bash
# RentEZ — shared helpers for the aws-* scripts. Sourced, never run directly.

set -euo pipefail

# ------------------------------------------------------------------ constants
CLUSTER_NAME="${CLUSTER_NAME:-rentez}"
PERSISTENT_STACK="${PERSISTENT_STACK:-rentez-persistent}"
DATABASE_STACK="${DATABASE_STACK:-rentez-database}"
GUARDRAILS_STACK="${GUARDRAILS_STACK:-rentez-guardrails}"
NAMESPACE="${NAMESPACE:-rentez}"
SERVICES=(account-service catalog-service reservation-service payment-service notification-service)

# Pod image used for every one-off database task. Chosen so that no custom image
# has to be built and pushed before the first teardown can take a backup.
DB_TOOLS_IMAGE="postgres:16-alpine"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

# ------------------------------------------------------------------ output
_c() { printf "\033[%sm%s\033[0m" "$1" "$2"; }
say()  { printf "  %s %s\n" "$(_c '0;36' '›')" "$*"; }
ok()   { printf "  %s %s\n" "$(_c '0;32' 'ok')" "$*"; }
warn() { printf "  %s %s\n" "$(_c '0;33' '!!')" "$*"; }
die()  { printf "\n  %s %s\n\n" "$(_c '0;31' 'ERROR')" "$*" >&2; exit 1; }

step() { printf "\n%s\n" "$(_c '1;37' "== $*")"; }

# ------------------------------------------------------------------ preflight
require_tools() {
	local missing=0 t
	for t in "$@"; do
		command -v "$t" >/dev/null 2>&1 || { warn "missing: $t"; missing=1; }
	done
	[ "$missing" -eq 0 ] || die "install the tools above, then re-run. See aws/README.md."
}

require_credentials() {
	aws sts get-caller-identity >/dev/null 2>&1 \
		|| die "no usable AWS credentials. Run 'aws configure' or 'aws sso login'."
	AWS_ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text)"
	AWS_REGION="${AWS_REGION:-$(aws configure get region || true)}"
	[ -n "${AWS_REGION:-}" ] || die "no region set. Export AWS_REGION or run 'aws configure'."
	export AWS_ACCOUNT_ID AWS_REGION
}

# ------------------------------------------------------------------ stacks
stack_status() {
	aws cloudformation describe-stacks --stack-name "$1" \
		--query 'Stacks[0].StackStatus' --output text 2>/dev/null || echo "MISSING"
}

stack_exists() { [ "$(stack_status "$1")" != "MISSING" ]; }

# Read one Output from a stack. Fails loudly rather than returning an empty
# string, because an empty registry or subnet id fails much later and much more
# confusingly than it needs to.
stack_output() {
	local stack="$1" key="$2" value
	value="$(aws cloudformation describe-stacks --stack-name "$stack" \
		--query "Stacks[0].Outputs[?OutputKey=='$key'].OutputValue" --output text 2>/dev/null || true)"
	[ -n "$value" ] && [ "$value" != "None" ] \
		|| die "stack '$stack' has no output '$key'. Has 'make aws-bootstrap' been run in this account?"
	printf '%s' "$value"
}

require_persistent_stack() {
	stack_exists "$PERSISTENT_STACK" \
		|| die "the persistent stack is missing. Run 'make aws-bootstrap' once per account first."
}

# ------------------------------------------------------------------ cluster
cluster_exists() {
	aws eks describe-cluster --name "$CLUSTER_NAME" >/dev/null 2>&1
}

# ------------------------------------------------------------------ db access
#
# RDS sits in private subnets with no public IP, so nothing on a laptop can
# reach it. Every database task therefore runs as a throwaway pod inside the
# cluster. This is also why `make aws-down` must take its dump BEFORE deleting
# the cluster - once the cluster is gone there is no route to the data at all.
#
# run_db_pod <pod-name> <script-file>
#
# The script is delivered through a ConfigMap rather than interpolated into the
# pod spec. That is not fussiness: a dump command contains quotes, dollars and
# newlines, and embedding it in `kubectl run --overrides` JSON mangles it in
# ways that surface as a half-written backup rather than an error.
run_db_pod() {
	local name="$1" script="$2"
	local db_host db_pass bucket
	db_host="$(stack_output "$DATABASE_STACK" DbEndpoint)"
	db_pass="$(aws ssm get-parameter --name /rentez/db/master-password \
		--with-decryption --query Parameter.Value --output text)"
	bucket="$(stack_output "$PERSISTENT_STACK" BackupBucketName)"

	kubectl create configmap "$name-script" --namespace "$NAMESPACE" \
		--from-file=run.sh="$script" --dry-run=client -o yaml | kubectl apply -f - >/dev/null

	# The master password goes in via a Secret, so it is not visible in
	# `kubectl get pod -o yaml` or in anyone's shell history.
	kubectl create secret generic "$name-creds" --namespace "$NAMESPACE" \
		--from-literal=PGPASSWORD="$db_pass" --dry-run=client -o yaml | kubectl apply -f - >/dev/null

	kubectl delete pod "$name" --namespace "$NAMESPACE" --ignore-not-found >/dev/null 2>&1 || true
	kubectl apply -f - >/dev/null <<-YAML
	apiVersion: v1
	kind: Pod
	metadata:
	  name: $name
	  namespace: $NAMESPACE
	spec:
	  restartPolicy: Never
	  serviceAccountName: rentez-backup
	  containers:
	    - name: db
	      image: $DB_TOOLS_IMAGE
	      command: ["sh", "/scripts/run.sh"]
	      env:
	        - name: PGHOST
	          value: "$db_host"
	        - name: PGUSER
	          value: "rentez_admin"
	        - name: PGDATABASE
	          value: "rentez"
	        - name: PGPASSWORD
	          valueFrom:
	            secretKeyRef: { name: $name-creds, key: PGPASSWORD }
	        - name: AWS_REGION
	          value: "$AWS_REGION"
	        - name: BACKUP_BUCKET
	          value: "$bucket"
	      volumeMounts:
	        - { name: scripts, mountPath: /scripts }
	  volumes:
	    - name: scripts
	      configMap: { name: $name-script }
	YAML

	local rc=0
	# Wait for a TERMINAL state, not just Succeeded: waiting only for success
	# means a failed pod hangs here until the timeout with nothing printed.
	kubectl wait --namespace "$NAMESPACE" --for=jsonpath='{.status.phase}'=Succeeded \
		"pod/$name" --timeout=900s >/dev/null 2>&1 || rc=1

	kubectl logs --namespace "$NAMESPACE" "$name" 2>&1 | sed 's/^/      /' || true

	kubectl delete pod "$name" --namespace "$NAMESPACE" --ignore-not-found >/dev/null 2>&1 || true
	kubectl delete configmap "$name-script" --namespace "$NAMESPACE" --ignore-not-found >/dev/null 2>&1 || true
	kubectl delete secret "$name-creds" --namespace "$NAMESPACE" --ignore-not-found >/dev/null 2>&1 || true
	return $rc
}

# ------------------------------------------------------------------ identity
# A short human name for whoever is running this, used for the holder record and
# for the Owner tag on ephemeral resources.
#
# Takes the LAST path segment of the caller ARN, which lands on something
# readable under both access models:
#
#   IAM user   arn:aws:iam::123:user/rentez-cli                    -> rentez-cli
#   Identity   arn:aws:sts::123:assumed-role/AWSReservedSSO_x/a@b  -> a@b
#             Center
#
# Under Identity Center that is the person's email, which is exactly what you
# want on a shared account: the role name is the same for everyone, the email
# is not.
caller_name() {
	local arn
	arn="$(aws sts get-caller-identity --query Arn --output text 2>/dev/null || true)"
	[ -n "$arn" ] || { printf 'unknown'; return; }
	printf '%s' "${arn##*/}"
}

# ------------------------------------------------------------------ reaper
# The deadline the reaper Lambda reads every five minutes. Writing "none"
# disarms it; writing a timestamp arms it.
arm_reaper() {
	local hours="$1" deadline
	deadline="$(python3 -c "
import datetime as dt
print((dt.datetime.now(dt.timezone.utc) + dt.timedelta(hours=$hours)).replace(microsecond=0).isoformat())
")"
	aws ssm put-parameter --name /rentez/env/expires-at --type String \
		--value "$deadline" --overwrite >/dev/null
	printf '%s' "$deadline"
}

disarm_reaper() {
	aws ssm put-parameter --name /rentez/env/expires-at --type String \
		--value none --overwrite >/dev/null 2>&1 || true
}

# ------------------------------------------------------------------ holder
# WHO CURRENTLY HAS THE ENVIRONMENT.
#
# Irrelevant with one account per person, and important the moment an account is
# shared: the lease is a single timestamp with no notion of ownership, so
# without this nobody can tell whether the cluster they are about to delete is
# in use, and `aws-status` cannot say whose four-hour window is running out.
#
# Deliberately a CONVENTION, NOT A LOCK. SSM has no compare-and-swap, so two
# simultaneous `aws-up` runs would still collide inside eksctl. Making that
# impossible needs real distributed locking, which is disproportionate for a
# four-person team who can see each other. This makes the collision visible and
# attributable, which is the part that actually helps.
#
# Stored as "<name>|<iso-8601 UTC>".
hold_env() {
	aws ssm put-parameter --name /rentez/env/held-by --type String \
		--value "$(caller_name)|$(python3 -c "
import datetime as dt
print(dt.datetime.now(dt.timezone.utc).replace(microsecond=0).isoformat())
")" --overwrite >/dev/null 2>&1 || true
}

release_env() {
	aws ssm put-parameter --name /rentez/env/held-by --type String \
		--value none --overwrite >/dev/null 2>&1 || true
}

# Prints "<name>|<iso>" or "none".
current_holder() {
	aws ssm get-parameter --name /rentez/env/held-by \
		--query Parameter.Value --output text 2>/dev/null || printf 'none'
}

holder_name() {
	local raw; raw="$(current_holder)"
	[ "$raw" = "none" ] && { printf 'none'; return; }
	printf '%s' "${raw%%|*}"
}

holder_since() {
	local raw; raw="$(current_holder)"
	[ "$raw" = "none" ] && { printf ''; return; }
	printf '%s' "${raw#*|}"
}
