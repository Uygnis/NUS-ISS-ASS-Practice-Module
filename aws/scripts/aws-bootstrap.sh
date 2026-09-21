#!/usr/bin/env bash
# RentEZ — one-time per-account setup. Creates everything that is free and
# permanent, so that `make aws-up` afterwards creates only what costs money.
#
#   make aws-bootstrap NOTIFY_EMAIL=you@u.nus.edu
#
# Safe to re-run: every step is either idempotent or explicitly skipped when the
# resource already exists. Run it again after editing 10-account.yaml or
# 15-environment.yaml.

source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

NOTIFY_EMAIL="${NOTIFY_EMAIL:-}"
[ -n "$NOTIFY_EMAIL" ] || die "set NOTIFY_EMAIL, e.g. make aws-bootstrap NOTIFY_EMAIL=you@u.nus.edu"

require_tools aws python3 openssl
require_credentials

say "account $AWS_ACCOUNT_ID, region $AWS_REGION"

# ---------------------------------------------------------------- guardrails
# FIRST, before anything that can bill. If the rest of this script fails
# halfway, the budget alarms still exist.
step "Spend guardrails"
aws cloudformation deploy \
	--stack-name "$GUARDRAILS_STACK" \
	--template-file "$REPO_ROOT/aws/cloudformation/00-guardrails.yaml" \
	--parameter-overrides "NotifyEmail=$NOTIFY_EMAIL" \
	--no-fail-on-empty-changeset >/dev/null
ok "budgets deployed — confirm the subscription email at $NOTIFY_EMAIL"

# ------------------------------------------------------------------ secrets
# Created OUT OF BAND, not by CloudFormation, because AWS::SSM::Parameter cannot
# create a SecureString. Putting them in the template would mean either
# plaintext in the repo or a stack parameter readable via DescribeStacks.
step "Secrets"
put_secret_if_absent() {
	local name="$1" generator="$2"
	if aws ssm get-parameter --name "$name" >/dev/null 2>&1; then
		ok "$name already exists — left alone"
		return
	fi
	aws ssm put-parameter --name "$name" --type SecureString \
		--value "$($generator)" --tags Key=Project,Value=rentez >/dev/null
	ok "$name created"
}
gen_jwt() { openssl rand -base64 48 | tr -d '\n'; }
gen_pw()  { openssl rand -base64 24 | tr -dc 'A-Za-z0-9' | head -c 32; }

# NEVER regenerated on a re-run. Rotating the JWT key invalidates every issued
# token; rotating the master password locks the service out of a database
# restored from a dump taken under the old one.
put_secret_if_absent /rentez/jwt-secret gen_jwt
put_secret_if_absent /rentez/db/master-password gen_pw

# One per service role, matching db/init/01-schemas.sql's five roles.
for role in auth fleet booking payment notification; do
	put_secret_if_absent "/rentez/db/${role}-password" gen_pw
done

# ------------------------------------------------- account and environment
step "Account and environment stacks"
say "looking up the CloudFront origin-facing prefix list"
PREFIX_LIST="$(aws ec2 describe-managed-prefix-lists \
	--filters Name=prefix-list-name,Values=com.amazonaws.global.cloudfront.origin-facing \
	--query 'PrefixLists[0].PrefixListId' --output text)"
[ -n "$PREFIX_LIST" ] && [ "$PREFIX_LIST" != "None" ] \
	|| die "could not find the CloudFront prefix list in $AWS_REGION."
ok "prefix list $PREFIX_LIST"

# REFUSE TO BUILD A SECOND COPY OF AN ACCOUNT THAT ALREADY HAS ONE.
#
# An account bootstrapped before the split has rentez-persistent, holding the
# VPC and the buckets. Deploying a fresh account stack beside it would create a
# second VPC, and a fresh environment stack with no EnvironmentName would ask
# for bucket names that already exist - failing halfway, after the VPC.
#
# The way out is adoption rather than migration: rentez-persistent publishes
# all twelve outputs the two new stacks publish between them, so pointing both
# variables at it leaves that environment exactly as it is, URL included.
if [ "$ACCOUNT_STACK" != "$LEGACY_STACK" ] && stack_exists "$LEGACY_STACK"; then
	die "this account already has '$LEGACY_STACK', from before the stack split.

  Creating '$ACCOUNT_STACK' alongside it would build a second VPC, and
  '$ENVIRONMENT_STACK' would collide on bucket names that already exist.

  To keep using the existing environment, adopt it - no migration, and the
  CloudFront URL is unchanged:

    export ACCOUNT_STACK=$LEGACY_STACK ENVIRONMENT_STACK=$LEGACY_STACK

  To add a SECOND environment beside it, keep the legacy stack as the account
  stack and give the new environment its own environment stack:

    export ACCOUNT_STACK=$LEGACY_STACK
    export ENVIRONMENT_STACK=rentez-environment-dev ENVIRONMENT_NAME=dev
    export CLUSTER_NAME=rentez-dev NAMESPACE=rentez-dev DB_NAME=rentez_dev

  See 'Adopting an account bootstrapped before the split' in aws/README.md."
fi

# Skipped when the legacy stack is serving as the account stack: it already has
# the VPC, the security groups and the ECR repositories, and re-deploying
# 10-account.yaml over it would try to create all of them a second time.
if [ "$ACCOUNT_STACK" = "$LEGACY_STACK" ]; then
	ok "using $LEGACY_STACK as the account stack (adopted, not redeployed)"
else
	# The account stack first: the environment stack does not import from it,
	# but the cluster and database both do, and creating them in this order
	# keeps the failure modes in the obvious sequence.
	say "deploying $ACCOUNT_STACK (VPC, security groups, ECR)"
	aws cloudformation deploy \
		--stack-name "$ACCOUNT_STACK" \
		--template-file "$REPO_ROOT/aws/cloudformation/10-account.yaml" \
		--parameter-overrides "CloudFrontPrefixListId=$PREFIX_LIST" "ClusterName=$CLUSTER_NAME" \
		--no-fail-on-empty-changeset >/dev/null
	ok "$ACCOUNT_STACK ready"
fi

# One of these per environment. ENVIRONMENT_NAME empty keeps the original
# unsuffixed resource names, so the environment that predates the split is
# adopted rather than rebuilt.
if [ "$ENVIRONMENT_STACK" = "$LEGACY_STACK" ]; then
	ok "using $LEGACY_STACK as the environment stack (adopted, not redeployed)"
else
	say "deploying $ENVIRONMENT_STACK (CloudFront takes a few minutes on first create)"
	aws cloudformation deploy \
		--stack-name "$ENVIRONMENT_STACK" \
		--template-file "$REPO_ROOT/aws/cloudformation/15-environment.yaml" \
		--capabilities CAPABILITY_IAM \
		--parameter-overrides "EnvironmentName=$ENVIRONMENT_NAME" "ClusterName=$CLUSTER_NAME" \
		--no-fail-on-empty-changeset >/dev/null
	ok "$ENVIRONMENT_STACK ready"
fi

APP_URL="$(stack_output "$ENVIRONMENT_STACK" AppUrl)"

step "Done"
cat <<EOF

  This account is ready. Nothing here bills by the hour.

  Permanent URL   $APP_URL
                  (bookmark it — it survives every teardown)

  Next:
    make aws-up          bring the cluster and database up  (~20 min)
    make aws-status      see what is running and when it expires
    make aws-down        dump to S3 and tear it all down    (~15 min)

  The reaper is armed by aws-up and will tear the cluster down on its own if
  you forget. Confirm the budget email so you hear about it if it does not.

EOF
