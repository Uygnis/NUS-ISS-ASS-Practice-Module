#!/usr/bin/env bash
# RentEZ — back up and tear down.
#
#   make aws-down              dump to S3, then destroy everything hourly-billed
#   make aws-down KEEP_DB=1    destroy the cluster, leave the database running
#
# ORDER MATTERS AND IS NOT NEGOTIABLE.
#
#   1. pg_dump to S3        — RDS is in private subnets, so this can only run
#                             from inside the cluster. Once the cluster is gone
#                             there is no route to the data at all.
#   2. Delete the ingresses — frees the ALB. Deleting the cluster first orphans
#                             it, and an orphaned ALB keeps billing ~$16/month
#                             with nothing pointing at it.
#   3. Delete the cluster   — the $0.10/hour item.
#   4. Delete the database  — the last billed resource.
#   5. Disarm the reaper    — nothing left to reap.
#
# The script REFUSES TO CONTINUE if the dump fails. That is the whole safety
# model: DeletionPolicy on the RDS stack is Delete, so this dump is the only
# copy of the data.

source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

KEEP_DB="${KEEP_DB:-0}"
SKIP_BACKUP="${SKIP_BACKUP:-0}"

require_tools aws eksctl kubectl
require_credentials
require_persistent_stack

# ------------------------------------------------------------ whose is this?
# Only meaningful on a shared account, and there it matters a lot: the reaper
# aside, `aws-down` is the one command that destroys a colleague's work with no
# undo. A holder record cannot prevent that - SSM has no locking - but it can
# make sure nobody does it by accident.
#
# FORCE=1 skips the prompt, which is what aws-nuke passes and what a
# non-interactive shell requires.
HOLDER="$(holder_name)"
ME="$(caller_name)"
if [ "$HOLDER" != "none" ] && [ "$HOLDER" != "$ME" ] && [ "${FORCE:-0}" != "1" ]; then
	printf "\n"
	warn "this environment was brought up by $HOLDER, not you"
	warn "held since $(holder_since)"
	if [ -t 0 ]; then
		printf "\n  Tear it down anyway? Type yes to continue: "
		read -r CONFIRM
		[ "$CONFIRM" = "yes" ] || die "cancelled — nothing deleted."
	else
		die "refusing to destroy $HOLDER's environment from a non-interactive shell. Re-run with FORCE=1 if you are certain."
	fi
fi

# ------------------------------------------------------------------ 1. backup
step "1/5  Backup"
if ! cluster_exists; then
	warn "no cluster — cannot reach RDS to take a dump (it has no public route)"
	if [ "$SKIP_BACKUP" != "1" ] && stack_exists "$DATABASE_STACK"; then
		die "the database still exists but is unreachable. Run 'make aws-up' to restore access and dump it, or re-run with SKIP_BACKUP=1 to destroy it WITHOUT a backup."
	fi
elif ! stack_exists "$DATABASE_STACK"; then
	ok "no database to back up"
elif [ "$SKIP_BACKUP" = "1" ]; then
	warn "SKIP_BACKUP=1 — destroying without a backup"
else
	aws eks update-kubeconfig --name "$CLUSTER_NAME" --region "$AWS_REGION" >/dev/null
	STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
	say "dumping all five schemas to s3://.../dumps/$STAMP.sql.gz"

	DUMP_SH=$(mktemp)
	cat > "$DUMP_SH" <<SH
set -euo pipefail
apk add --no-cache aws-cli >/dev/null 2>&1

# --clean --if-exists so the dump can be restored over a freshly bootstrapped
# database without colliding with the schemas 01-schemas.sql just created.
# The roles themselves are NOT dumped (--no-owner, no --create): they are
# created by the bootstrap step from SSM, and a dump carrying stale passwords
# would lock the services out after a restore.
pg_dump --no-owner --no-privileges --clean --if-exists \\
    --schema=rentez_auth --schema=rentez_fleet --schema=rentez_booking \\
    --schema=rentez_payment --schema=rentez_notification \\
  | gzip -9 \\
  | aws s3 cp - "s3://\$BACKUP_BUCKET/dumps/$STAMP.sql.gz"

# Verify rather than trust. A truncated upload is silent otherwise, and this is
# the only copy of the data.
SIZE=\$(aws s3api head-object --bucket "\$BACKUP_BUCKET" --key "dumps/$STAMP.sql.gz" --query ContentLength --output text)
echo "uploaded dumps/$STAMP.sql.gz (\${SIZE} bytes)"

# DELETE A REJECTED DUMP, do not merely refuse it.
# The upload streams, so a pg_dump that fails still leaves an object behind - an
# empty gzip stream is 20 bytes. Left in place it becomes the NEWEST dump, and
# the next `make aws-up` restores it in preference to the last good one,
# reporting success while loading nothing. Failing loudly is not enough; the
# artefact has to go too.
if [ "\$SIZE" -le 1000 ]; then
    echo "DUMP TOO SMALL (\${SIZE} bytes) — pg_dump produced nothing. Removing the artefact."
    aws s3 rm "s3://\$BACKUP_BUCKET/dumps/$STAMP.sql.gz" || true
    exit 1
fi
SH
	run_db_pod rentez-db-dump "$DUMP_SH" \
		|| die "the dump FAILED. Nothing has been deleted. Fix the problem and re-run, or use SKIP_BACKUP=1 if you genuinely do not want this data."
	rm -f "$DUMP_SH"
	ok "backup verified"
fi

# --------------------------------------------------------------- 2. ingresses
step "2/5  Load balancer"
if cluster_exists; then
	aws eks update-kubeconfig --name "$CLUSTER_NAME" --region "$AWS_REGION" >/dev/null 2>&1 || true
	if kubectl get namespace "$NAMESPACE" >/dev/null 2>&1; then
		# Deleting the Ingresses lets the load balancer controller remove the ALB
		# properly. `eksctl delete cluster` does not do this for you, and the
		# leftover ALB also holds ENIs that stall the cluster deletion.
		kubectl delete ingress --all --namespace "$NAMESPACE" --timeout=180s >/dev/null 2>&1 || true
		say "waiting for the controller to remove the ALB"
		for _ in $(seq 1 30); do
			COUNT="$(aws elbv2 describe-load-balancers \
				--query "length(LoadBalancers[?VpcId=='$(stack_output "$PERSISTENT_STACK" VpcId)'])" \
				--output text 2>/dev/null || echo 0)"
			[ "$COUNT" = "0" ] && break
			sleep 10
		done
		ok "ALB released"
	fi
else
	ok "no cluster"
fi

# ----------------------------------------------------------------- 3. cluster
step "3/5  Cluster"
if cluster_exists; then
	say "deleting the cluster (about 10 minutes)"
	eksctl delete cluster --name "$CLUSTER_NAME" --region "$AWS_REGION" --wait \
		|| warn "eksctl reported a problem — check for leftover CloudFormation stacks named eksctl-rentez-*"
	ok "cluster deleted — the \$0.10/hour charge has stopped"
else
	ok "no cluster to delete"
fi

# ---------------------------------------------------------------- 4. database
step "4/5  Database"
if [ "$KEEP_DB" = "1" ]; then
	warn "KEEP_DB=1 — leaving RDS running at about \$0.018/hour (~\$13/month)"
	warn "it is now UNREACHABLE until the next 'make aws-up', because nothing else is in the VPC"
elif stack_exists "$DATABASE_STACK"; then
	say "deleting $DATABASE_STACK"
	aws cloudformation delete-stack --stack-name "$DATABASE_STACK"
	aws cloudformation wait stack-delete-complete --stack-name "$DATABASE_STACK" \
		|| warn "delete did not complete cleanly — check the CloudFormation console"
	ok "database deleted"
else
	ok "no database to delete"
fi

# ------------------------------------------------------------------ 5. edge
step "5/5  Edge and lease"
# Reset the CloudFront origin. The distribution stays — that is what keeps the
# team's URL stable — but pointing it at a deleted ALB would leave it serving
# confusing errors instead of an honest 502.
aws cloudformation deploy \
	--stack-name "$PERSISTENT_STACK" \
	--template-file "$REPO_ROOT/aws/cloudformation/10-persistent.yaml" \
	--capabilities CAPABILITY_IAM \
	--parameter-overrides "AlbDnsName=placeholder.example.com" "ClusterName=$CLUSTER_NAME" \
		"CloudFrontPrefixListId=$(aws ec2 describe-managed-prefix-lists \
			--filters Name=prefix-list-name,Values=com.amazonaws.global.cloudfront.origin-facing \
			--query 'PrefixLists[0].PrefixListId' --output text)" \
	--no-fail-on-empty-changeset >/dev/null
ok "CloudFront origin reset"

disarm_reaper
release_env
ok "reaper disarmed and the environment released"

step "Down"
cat <<EOF

  Everything billed by the hour is gone.

  Still there, and still costing roughly \$0.80/month:
    ECR images, S3 backups, the CloudFront distribution, SSM parameters, the VPC.

  Your URL is unchanged and will work again after the next 'make aws-up'.
  Backups:  aws s3 ls s3://$(stack_output "$PERSISTENT_STACK" BackupBucketName)/dumps/

EOF
