#!/usr/bin/env bash
# RentEZ — remove everything, including the free layer.
#
#   make aws-nuke
#
# END OF SEMESTER ONLY. `make aws-down` is what you want on an ordinary day: it
# already removes everything billed by the hour and leaves about $0.80/month.
#
# This additionally destroys the CloudFront distribution, the ECR images, the S3
# buckets INCLUDING EVERY DATABASE BACKUP, the VPC and the SSM secrets. The
# team's permanent URL changes if you ever bootstrap again.
#
# The guardrails stack is deliberately NOT deleted. Budget alarms cost nothing
# and are the last thing you want to remove from an account you are walking away
# from - a forgotten resource somewhere else should still be able to reach you.

source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

require_tools aws eksctl kubectl
require_credentials

if ! stack_exists "$PERSISTENT_STACK"; then
	ok "nothing to remove — the persistent stack does not exist"
	exit 0
fi

BACKUP_BUCKET="$(stack_output "$PERSISTENT_STACK" BackupBucketName)"
FRONTEND_BUCKET="$(stack_output "$PERSISTENT_STACK" FrontendBucketName)"
DUMPS="$(aws s3 ls "s3://$BACKUP_BUCKET/dumps/" 2>/dev/null | wc -l | tr -d ' ' || true)"
DUMPS="${DUMPS:-0}"

cat <<EOF

  This will PERMANENTLY delete, in account $AWS_ACCOUNT_ID:

    - the CloudFront distribution   (your URL changes if you rebuild)
    - all five ECR repositories and every image in them
    - s3://$BACKUP_BUCKET  including $DUMPS database backup(s)
    - s3://$FRONTEND_BUCKET
    - the VPC, the DynamoDB tables, the SQS queues
    - /rentez/* in SSM, including the JWT key and every database password

  Budget alarms are kept.

  If you only want to stop paying by the hour, press Ctrl-C and run:
      make aws-down

EOF
printf "  Type the account id (%s) to confirm: " "$AWS_ACCOUNT_ID"
read -r CONFIRM
[ "$CONFIRM" = "$AWS_ACCOUNT_ID" ] || die "not confirmed — nothing deleted."

# Everything hourly first, including the backup this makes redundant. Reusing
# aws-down means the teardown ordering (ALB, then cluster, then database) is
# defined in exactly one place.
step "Tearing down the running environment first"
SKIP_BACKUP=1 bash "$(dirname "${BASH_SOURCE[0]}")/aws-down.sh" || warn "aws-down reported problems; continuing"

step "Emptying buckets"
# CloudFormation refuses to delete a non-empty bucket, and a versioned bucket is
# not empty until its old versions and delete markers are gone too.
for bucket in "$BACKUP_BUCKET" "$FRONTEND_BUCKET"; do
	aws s3 rm "s3://$bucket" --recursive >/dev/null 2>&1 || true
	aws s3api list-object-versions --bucket "$bucket" \
		--query '{Objects: Versions[].{Key:Key,VersionId:VersionId}}' --output json 2>/dev/null \
		| python3 -c '
import json, sys
d = json.load(sys.stdin)
print(json.dumps(d) if d.get("Objects") else "")' > /tmp/rentez-versions.json 2>/dev/null || true
	[ -s /tmp/rentez-versions.json ] && aws s3api delete-objects --bucket "$bucket" \
		--delete "file:///tmp/rentez-versions.json" >/dev/null 2>&1 || true
	aws s3api list-object-versions --bucket "$bucket" \
		--query '{Objects: DeleteMarkers[].{Key:Key,VersionId:VersionId}}' --output json 2>/dev/null \
		| python3 -c '
import json, sys
d = json.load(sys.stdin)
print(json.dumps(d) if d.get("Objects") else "")' > /tmp/rentez-markers.json 2>/dev/null || true
	[ -s /tmp/rentez-markers.json ] && aws s3api delete-objects --bucket "$bucket" \
		--delete "file:///tmp/rentez-markers.json" >/dev/null 2>&1 || true
	ok "emptied $bucket"
done
rm -f /tmp/rentez-versions.json /tmp/rentez-markers.json

step "Deleting the persistent stack"
say "CloudFront takes several minutes to disable and delete"
aws cloudformation delete-stack --stack-name "$PERSISTENT_STACK"
aws cloudformation wait stack-delete-complete --stack-name "$PERSISTENT_STACK" \
	|| warn "delete did not complete — check the CloudFormation console for the reason"

step "Deleting secrets"
for name in /rentez/jwt-secret /rentez/db/master-password \
            /rentez/db/auth-password /rentez/db/fleet-password \
            /rentez/db/booking-password /rentez/db/payment-password \
            /rentez/db/notification-password /rentez/env/expires-at; do
	aws ssm delete-parameter --name "$name" >/dev/null 2>&1 && ok "deleted $name" || true
done

step "Nuked"
printf "\n  The account is clean. Budget alarms remain in %s.\n\n" "$GUARDRAILS_STACK"
