#!/usr/bin/env bash
# RentEZ — one-time per-account setup. Creates everything that is free and
# permanent, so that `make aws-up` afterwards creates only what costs money.
#
#   make aws-bootstrap NOTIFY_EMAIL=you@u.nus.edu
#
# Safe to re-run: every step is either idempotent or explicitly skipped when the
# resource already exists. Run it again after editing 10-persistent.yaml.

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

# ---------------------------------------------------------------- persistent
step "Persistent stack"
say "looking up the CloudFront origin-facing prefix list"
PREFIX_LIST="$(aws ec2 describe-managed-prefix-lists \
	--filters Name=prefix-list-name,Values=com.amazonaws.global.cloudfront.origin-facing \
	--query 'PrefixLists[0].PrefixListId' --output text)"
[ -n "$PREFIX_LIST" ] && [ "$PREFIX_LIST" != "None" ] \
	|| die "could not find the CloudFront prefix list in $AWS_REGION."
ok "prefix list $PREFIX_LIST"

say "deploying $PERSISTENT_STACK (CloudFront takes a few minutes on first create)"
aws cloudformation deploy \
	--stack-name "$PERSISTENT_STACK" \
	--template-file "$REPO_ROOT/aws/cloudformation/10-persistent.yaml" \
	--capabilities CAPABILITY_IAM \
	--parameter-overrides "CloudFrontPrefixListId=$PREFIX_LIST" "ClusterName=$CLUSTER_NAME" \
	--no-fail-on-empty-changeset >/dev/null

APP_URL="$(stack_output "$PERSISTENT_STACK" AppUrl)"

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
