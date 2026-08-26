#!/usr/bin/env bash
# RentEZ — bring the AWS environment up.
#
#   make aws-up                 4-hour lease (the default)
#   make aws-up TTL_HOURS=8     longer lease, for a demo day
#   make aws-up TAG=abc1234     deploy a specific image tag
#
# Roughly 20 minutes, most of it the EKS control plane. Idempotent: run it again
# after a failure and it picks up from wherever it stopped.
#
# From here the environment costs about $0.21/hour. The lease is what stops that
# becoming $155/month — see the reaper in 10-persistent.yaml.

source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

TTL_HOURS="${TTL_HOURS:-4}"
TAG="${TAG:-}"

require_tools aws eksctl kubectl helm python3
require_credentials
require_persistent_stack

REGISTRY="$(stack_output "$PERSISTENT_STACK" EcrRegistry)"
VPC_ID="$(stack_output "$PERSISTENT_STACK" VpcId)"
ALB_SG="$(stack_output "$PERSISTENT_STACK" AlbSecurityGroupId)"
RDS_SG="$(stack_output "$PERSISTENT_STACK" RdsSecurityGroupId)"
APP_URL="$(stack_output "$PERSISTENT_STACK" AppUrl)"
FRONTEND_BUCKET="$(stack_output "$PERSISTENT_STACK" FrontendBucketName)"
DISTRIBUTION_ID="$(stack_output "$PERSISTENT_STACK" DistributionId)"

# Assigned to a plain variable FIRST, deliberately. stack_output aborts with
# `die` on a missing output, but inside a herestring that exit only kills the
# subshell - `set -e` does not see it, and the script would carry on with empty
# subnet ids and fail ten minutes later inside eksctl.
PUBLIC_SUBNETS="$(stack_output "$PERSISTENT_STACK" PublicSubnetIds)"
PRIVATE_SUBNETS="$(stack_output "$PERSISTENT_STACK" PrivateSubnetIds)"
IFS=',' read -r PUBLIC_SUBNET_A PUBLIC_SUBNET_B <<<"$PUBLIC_SUBNETS"
IFS=',' read -r PRIVATE_SUBNET_A PRIVATE_SUBNET_B <<<"$PRIVATE_SUBNETS"
export VPC_ID PUBLIC_SUBNET_A PUBLIC_SUBNET_B PRIVATE_SUBNET_A PRIVATE_SUBNET_B AWS_REGION AWS_ACCOUNT_ID

# The image tag defaults to the current commit, which is what CI tagged. Refuse
# to guess: deploying a tag that was never built fails 10 minutes later with
# ImagePullBackOff, long after the expensive resources exist.
if [ -z "$TAG" ]; then
	TAG="$(git -C "$REPO_ROOT" rev-parse --short=7 HEAD)"
	say "no TAG given, using the current commit: $TAG"
fi
if ! aws ecr describe-images --repository-name rentez/account-service \
		--image-ids "imageTag=$TAG" >/dev/null 2>&1; then
	die "no image tagged '$TAG' in ECR. Push one first (merge to main, or 'make aws-images'), or pass TAG=<sha>."
fi

# ------------------------------------------------------------- arm the lease
# BEFORE the first billable resource, not after the last one.
#
# This used to be the final step, and that was wrong in the one case that
# matters: a run that fails at step 4 leaves a cluster and a database alive with
# no deadline attached, which is exactly when somebody gives up for the evening.
# Arming first means a half-finished `aws-up` is still cleaned up automatically.
# Step 6 re-arms with a fresh TTL once everything is actually working, so a
# successful run still gets its full lease.
INITIAL_DEADLINE="$(arm_reaper "$TTL_HOURS")"
say "lease armed until $INITIAL_DEADLINE — even if this run fails, nothing is left running forever"

# ---------------------------------------------------------------- 1. database
# First, because it takes ~8 minutes and can create while the cluster does.
step "1/6  Database"
if stack_exists "$DATABASE_STACK"; then
	ok "$DATABASE_STACK already exists"
else
	say "creating RDS PostgreSQL (about 8 minutes)"
	aws cloudformation deploy \
		--stack-name "$DATABASE_STACK" \
		--template-file "$REPO_ROOT/aws/cloudformation/20-database.yaml" \
		--no-fail-on-empty-changeset >/dev/null &
	DB_PID=$!
fi

# ----------------------------------------------------------------- 2. cluster
step "2/6  EKS cluster"
if cluster_exists; then
	ok "cluster '$CLUSTER_NAME' already exists"
	aws eks update-kubeconfig --name "$CLUSTER_NAME" --region "$AWS_REGION" >/dev/null
else
	say "creating the cluster (about 15 minutes — this is the slow part)"
	# envsubst because eksctl has no ImportValue equivalent and the subnet ids
	# differ per account.
	envsubst < "$REPO_ROOT/aws/eksctl/cluster.yaml" > /tmp/rentez-cluster.yaml
	eksctl create cluster -f /tmp/rentez-cluster.yaml
fi

# Wait for the database only now — by this point it has almost certainly
# finished, so the two creations overlapped instead of queueing.
if [ -n "${DB_PID:-}" ]; then
	say "waiting for the database"
	wait "$DB_PID" || die "database stack failed. See the CloudFormation console."
	ok "database ready"
fi

DB_HOST="$(stack_output "$DATABASE_STACK" DbEndpoint)"

# Let the cluster's nodes reach Postgres. eksctl creates the node security group,
# so this rule cannot live in the persistent template.
NODE_SG="$(aws eks describe-cluster --name "$CLUSTER_NAME" \
	--query 'cluster.resourcesVpcConfig.clusterSecurityGroupId' --output text)"
aws ec2 authorize-security-group-ingress --group-id "$RDS_SG" \
	--protocol tcp --port 5432 --source-group "$NODE_SG" >/dev/null 2>&1 \
	&& ok "opened 5432 from the cluster to RDS" \
	|| ok "5432 already open from the cluster to RDS"

# --------------------------------------------------------------- 3. add-ons
step "3/6  Cluster add-ons"
kubectl create namespace "$NAMESPACE" --dry-run=client -o yaml | kubectl apply -f - >/dev/null

helm repo add eks https://aws.github.io/eks-charts >/dev/null 2>&1 || true
helm repo add autoscaler https://kubernetes.github.io/autoscaler >/dev/null 2>&1 || true
helm repo add metrics-server https://kubernetes-sigs.github.io/metrics-server/ >/dev/null 2>&1 || true
helm repo update >/dev/null

# REQUIRED for the HPA. Without a working metrics API every HPA reports
# <unknown> for its target and never scales — which reads as "autoscaling does
# not work" rather than "a component is missing".
#
# INSTALL IT ONLY IF IT IS ABSENT. Current EKS ships metrics-server as a MANAGED
# ADD-ON (see `aws eks list-addons`), so on a fresh cluster it is already there.
# Installing our own on top does not merely duplicate it: Helm 4 uses
# server-side apply, and the EKS add-on already owns
# `app.kubernetes.io/managed-by` and `app.kubernetes.io/version` on seven
# objects, so the upgrade aborts with a wall of field-conflict errors that names
# neither EKS nor add-ons.
#
# What matters is that the metrics API answers, not who installed it.
if kubectl get deployment metrics-server -n kube-system >/dev/null 2>&1; then
	ok "metrics-server already present (EKS add-on) — leaving it alone"
else
	helm upgrade --install metrics-server metrics-server/metrics-server \
		--namespace kube-system --wait >/dev/null
	ok "metrics-server installed"
fi

# Verify rather than assume. This is the exact call the HPA controller makes, and
# it is far better to fail here than to discover it during the load test.
say "waiting for the metrics API"
METRICS_OK=0
for _ in $(seq 1 30); do
	if kubectl top nodes >/dev/null 2>&1; then METRICS_OK=1; break; fi
	sleep 5
done
[ "$METRICS_OK" = "1" ] \
	&& ok "metrics API answering — HPAs will have real CPU numbers" \
	|| warn "metrics API not answering yet; HPAs may show <unknown> until it settles"

# Turns the five Ingresses into ONE ALB.
helm upgrade --install aws-load-balancer-controller eks/aws-load-balancer-controller \
	--namespace kube-system \
	--set "clusterName=$CLUSTER_NAME" \
	--set serviceAccount.create=false \
	--set serviceAccount.name=aws-load-balancer-controller \
	--set "region=$AWS_REGION" \
	--set "vpcId=$VPC_ID" \
	--wait >/dev/null
ok "aws-load-balancer-controller"

# WAIT FOR THE ADMISSION WEBHOOK, NOT JUST FOR THE PODS.
#
# `--wait` returns when the controller pods are Ready, which is NOT the same as
# the webhook being usable. The chart mints a fresh self-signed CA on every
# `helm upgrade` and writes it to both the Secret and the
# ValidatingWebhookConfiguration at once — but the running pods only pick the new
# certificate up when the kubelet re-syncs the mounted Secret, which can lag by
# up to a minute. In that window the API server presents the new CA and the pod
# still serves the old certificate, so any Ingress apply dies with:
#
#     failed calling webhook "vingress.elbv2.k8s.aws": tls: failed to verify
#     certificate: x509: certificate signed by unknown authority
#
# That reads like a broken install and is really a race, which is why it only
# ever appears on a SECOND run. A server-side dry-run is the honest probe: it
# exercises the exact webhook path the real apply will take.
say "waiting for the ingress admission webhook to serve a matching certificate"
WEBHOOK_OK=0
for _ in $(seq 1 30); do
	if kubectl apply --dry-run=server -f "$REPO_ROOT/deploy/k8s/00-internal-deny.yaml" >/dev/null 2>&1; then
		WEBHOOK_OK=1; break
	fi
	sleep 5
done
[ "$WEBHOOK_OK" = "1" ] \
	&& ok "webhook ready" \
	|| die "the ingress webhook never became usable. Try: kubectl -n kube-system rollout restart deploy/aws-load-balancer-controller, then re-run."

helm upgrade --install cluster-autoscaler autoscaler/cluster-autoscaler \
	--namespace kube-system \
	--set "autoDiscovery.clusterName=$CLUSTER_NAME" \
	--set "awsRegion=$AWS_REGION" \
	--set rbac.serviceAccount.create=false \
	--set rbac.serviceAccount.name=cluster-autoscaler \
	--wait >/dev/null
ok "cluster-autoscaler"

# ------------------------------------------------------- 4. schema + restore
step "4/6  Database bootstrap"

# Config every service reads.
kubectl create configmap rentez-config --namespace "$NAMESPACE" \
	--from-literal=db-host="$DB_HOST" --from-literal=db-port=5432 \
	--dry-run=client -o yaml | kubectl apply -f - >/dev/null

# One Secret holding the JWT key and the five role passwords, pulled from SSM.
# The alternative — the Secrets Store CSI driver — is another controller to
# install and debug for a benefit this environment does not need.
SECRET_ARGS=(--from-literal=jwt-secret="$(aws ssm get-parameter --name /rentez/jwt-secret \
	--with-decryption --query Parameter.Value --output text)")
for role in auth fleet booking payment notification; do
	SECRET_ARGS+=(--from-literal="${role}-db-password=$(aws ssm get-parameter \
		--name "/rentez/db/${role}-password" --with-decryption --query Parameter.Value --output text)")
done
kubectl create secret generic rentez-secrets --namespace "$NAMESPACE" \
	"${SECRET_ARGS[@]}" --dry-run=client -o yaml | kubectl apply -f - >/dev/null
ok "config and secrets applied"

# RDS has no /docker-entrypoint-initdb.d, so the schemas and roles that Postgres
# creates automatically in Docker have to be applied by hand exactly once. Same
# file, both environments.
say "creating schemas and roles"
BOOTSTRAP_SQL=$(mktemp)
{
	# The passwords in the checked-in file are local throwaways. Replace them
	# with the real ones from SSM before this runs against RDS.
	sed -e "s/'auth_pw'/'$(kubectl get secret rentez-secrets -n "$NAMESPACE" -o jsonpath='{.data.auth-db-password}' | base64 -d)'/" \
	    -e "s/'fleet_pw'/'$(kubectl get secret rentez-secrets -n "$NAMESPACE" -o jsonpath='{.data.fleet-db-password}' | base64 -d)'/" \
	    -e "s/'booking_pw'/'$(kubectl get secret rentez-secrets -n "$NAMESPACE" -o jsonpath='{.data.booking-db-password}' | base64 -d)'/" \
	    -e "s/'payment_pw'/'$(kubectl get secret rentez-secrets -n "$NAMESPACE" -o jsonpath='{.data.payment-db-password}' | base64 -d)'/" \
	    -e "s/'notification_pw'/'$(kubectl get secret rentez-secrets -n "$NAMESPACE" -o jsonpath='{.data.notification-db-password}' | base64 -d)'/" \
	    "$REPO_ROOT/db/init/01-schemas.sql"
} > "$BOOTSTRAP_SQL"

# run_db_pod delivers a single run.sh, so the SQL travels inside it as a quoted
# heredoc. 'SQLEOF' is quoted so the shell does not expand the $$ in the file's
# DO blocks — unquoted, every DO $$ ... $$ would collapse to the PID and the
# whole bootstrap would fail with a syntax error.
RUNNER=$(mktemp)
{
	printf 'set -e\ncat > /tmp/schemas.sql <<'"'"'SQLEOF'"'"'\n'
	cat "$BOOTSTRAP_SQL"
	printf '\nSQLEOF\npsql -v ON_ERROR_STOP=1 -f /tmp/schemas.sql\necho "schemas and roles applied"\n'
} > "$RUNNER"
run_db_pod rentez-db-bootstrap "$RUNNER" || die "schema bootstrap failed"
rm -f "$BOOTSTRAP_SQL" "$RUNNER"

# Restore the newest dump if there is one. Otherwise Flyway plus the seed
# profile build a fresh world when the services start.
BACKUP_BUCKET="$(stack_output "$PERSISTENT_STACK" BackupBucketName)"
# `|| true` is load-bearing: `aws s3 ls` exits 1 when a prefix matches nothing,
# and under `set -euo pipefail` that kills the script mid-assignment with no
# message at all. It bites every freshly bootstrapped account, which has no
# dumps yet - i.e. everyone's first `make aws-up`.
LATEST_LINE="$(aws s3 ls "s3://$BACKUP_BUCKET/dumps/" 2>/dev/null | sort | tail -1 || true)"
LATEST="$(printf '%s' "$LATEST_LINE" | awk '{print $4}')"
LATEST_SIZE="$(printf '%s' "$LATEST_LINE" | awk '{print $3}')"

# Do not restore something that cannot be a real dump. An empty gzip stream is
# 20 bytes, and restoring one succeeds silently - psql is perfectly happy to
# apply no statements - so the run reports "restored" while loading nothing.
# Checking the size here means a bad artefact degrades to "start fresh" instead
# of to "start empty and believe otherwise".
if [ -n "${LATEST:-}" ] && [ "${LATEST_SIZE:-0}" -le 1000 ] 2>/dev/null; then
	warn "ignoring $LATEST — only ${LATEST_SIZE} bytes, so it is not a real dump"
	LATEST=""
fi

if [ -n "${LATEST:-}" ] && [ "${RESTORE:-1}" = "1" ]; then
	say "restoring $LATEST"
	RESTORE_SH=$(mktemp)
	cat > "$RESTORE_SH" <<SH
set -e
apk add --no-cache aws-cli >/dev/null 2>&1
aws s3 cp "s3://\$BACKUP_BUCKET/dumps/$LATEST" - | gunzip | psql -v ON_ERROR_STOP=1
echo "restored $LATEST"
SH
	run_db_pod rentez-db-restore "$RESTORE_SH" || warn "restore failed — the services will build a fresh schema instead"
	rm -f "$RESTORE_SH"
else
	ok "no dump to restore — Flyway and the seed profile will build a fresh database"
fi

# ---------------------------------------------------------------- 5. deploy
step "5/6  Services"
kubectl apply -f "$REPO_ROOT/deploy/k8s/00-internal-deny.yaml" >/dev/null
ok "internal-path deny rule"

for svc in "${SERVICES[@]}"; do
	helm upgrade --install "$svc" "$REPO_ROOT/deploy/helm/rentez-service" \
		--namespace "$NAMESPACE" \
		--values "$REPO_ROOT/deploy/helm/values/${svc%-service}.yaml" \
		--set "image.registry=$REGISTRY" \
		--set "image.tag=$TAG" \
		--set "ingress.albSecurityGroup=$ALB_SG" \
		--wait --timeout 5m >/dev/null
	ok "$svc"
done

# ------------------------------------------------------------- 6. edge + lease
step "6/6  Frontend and edge"

say "building and uploading the frontend"
( cd "$REPO_ROOT/frontend" && npm ci --silent && npm run build --silent )
aws s3 sync "$REPO_ROOT/frontend/dist" "s3://$FRONTEND_BUCKET" --delete >/dev/null
ok "uploaded to $FRONTEND_BUCKET"

say "waiting for the ALB address"
ALB_DNS=""
for _ in $(seq 1 60); do
	ALB_DNS="$(kubectl get ingress -n "$NAMESPACE" account-service \
		-o jsonpath='{.status.loadBalancer.ingress[0].hostname}' 2>/dev/null || true)"
	[ -n "$ALB_DNS" ] && break
	sleep 10
done
[ -n "$ALB_DNS" ] || die "the ALB never got an address. Check: kubectl -n kube-system logs deploy/aws-load-balancer-controller"
ok "ALB $ALB_DNS"

# Point CloudFront's /api/* behaviour at this ALB. The distribution itself lives
# in the persistent stack so the team's URL never changes; only its origin moves.
say "repointing CloudFront at the new ALB (takes a few minutes to propagate)"
aws cloudformation deploy \
	--stack-name "$PERSISTENT_STACK" \
	--template-file "$REPO_ROOT/aws/cloudformation/10-persistent.yaml" \
	--capabilities CAPABILITY_IAM \
	--parameter-overrides "AlbDnsName=$ALB_DNS" "ClusterName=$CLUSTER_NAME" \
		"CloudFrontPrefixListId=$(aws ec2 describe-managed-prefix-lists \
			--filters Name=prefix-list-name,Values=com.amazonaws.global.cloudfront.origin-facing \
			--query 'PrefixLists[0].PrefixListId' --output text)" \
	--no-fail-on-empty-changeset >/dev/null
aws cloudfront create-invalidation --distribution-id "$DISTRIBUTION_ID" --paths '/*' >/dev/null
ok "CloudFront updated"

# Re-arm, so the full TTL is measured from a WORKING environment rather than
# from whenever this run happened to start.
DEADLINE="$(arm_reaper "$TTL_HOURS")"

step "Up"
cat <<EOF

  URL        $APP_URL
  Image tag  $TAG
  Expires    $DEADLINE  (in ${TTL_HOURS}h)

  The reaper will tear this down automatically at that time. To finish early
  and take a backup:            make aws-down
  To extend the lease:          make aws-up TTL_HOURS=8

  Costing roughly \$0.21/hour from now.

EOF
