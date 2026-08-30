#!/usr/bin/env bash
# RentEZ — deploy the application to an environment that is already running.
#
#   make aws-deploy                 deploy the current commit
#   make aws-deploy TAG=abc1234     deploy a specific image tag
#
# About three minutes. This is the half of `aws-up` that changes when the CODE
# changes: five Helm releases, the frontend bundle, and the CloudFront origin.
# Everything it needs it reads back out of CloudFormation, so it holds no state
# of its own and can run from a laptop or from GitHub Actions unchanged.
#
# `make aws-up` calls this as its last step, so bringing an environment up and
# deploying to it are still one command. The split exists so that redeploying a
# code change does not mean waiting twenty minutes for a cluster that is already
# there — and so CI can do it on every merge.
#
# It deliberately does NOT create anything billed by the hour. If the cluster is
# gone, this fails fast and tells you to run `make aws-up`; it will not quietly
# resurrect a $0.21/hour environment because a workflow fired.

source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

TAG="${TAG:-}"
# aws-up prints its own summary — URL, holder, lease, burn rate — and arms the
# lease itself, so it calls this with SUMMARY=0. Note what is NOT here: this
# script never touches the reaper. A deploy from CI must not silently hand
# somebody another four hours; the person who ran `aws-up` chose the deadline,
# and a merge by a teammate is not a reason to move it.
SUMMARY="${SUMMARY:-1}"

# No eksctl and no python3 — this half creates no infrastructure and does no
# date arithmetic.
require_tools aws kubectl helm npm git
require_credentials
require_persistent_stack

REGISTRY="$(stack_output "$PERSISTENT_STACK" EcrRegistry)"
ALB_SG="$(stack_output "$PERSISTENT_STACK" AlbSecurityGroupId)"
APP_URL="$(stack_output "$PERSISTENT_STACK" AppUrl)"
FRONTEND_BUCKET="$(stack_output "$PERSISTENT_STACK" FrontendBucketName)"
DISTRIBUTION_ID="$(stack_output "$PERSISTENT_STACK" DistributionId)"

# ------------------------------------------------------------------ preflight
# All four checks exist because this script now runs unattended, against an
# environment that may have been reaped since anyone last looked at it. Each one
# turns a confusing late failure into an immediate, obvious one.

# 1. Is there a cluster at all? The lease may have expired hours ago.
cluster_exists || die "no cluster '$CLUSTER_NAME'. Someone has to run 'make aws-up' first."
aws eks update-kubeconfig --name "$CLUSTER_NAME" --region "$AWS_REGION" >/dev/null

# 2. Has the namespace been bootstrapped? Without rentez-secrets the pods start
#    and then crash on a missing DB_PASSWORD, which reads as an application bug
#    rather than as a half-built environment.
kubectl get secret rentez-secrets --namespace "$NAMESPACE" >/dev/null 2>&1 \
	|| die "namespace '$NAMESPACE' is not bootstrapped (no rentez-secrets). Run 'make aws-up'."

# 3. Which tag? Default to the current commit, which is what CI tagged.
if [ -z "$TAG" ]; then
	TAG="$(git -C "$REPO_ROOT" rev-parse --short=7 HEAD)"
	say "no TAG given, using the current commit: $TAG"
fi

# 4. Does that tag exist? Refuse to guess: deploying a tag that was never built
#    fails minutes later with ImagePullBackOff, after four of the five releases
#    have already been rolled.
if ! aws ecr describe-images --repository-name rentez/account-service \
		--image-ids "imageTag=$TAG" >/dev/null 2>&1; then
	die "no image tagged '$TAG' in ECR. Push one first (merge to main, or 'make aws-images'), or pass TAG=<sha>."
fi

# ------------------------------------------------------------------ 1. services
step "1/2  Services"
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

# ------------------------------------------------------------ 2. frontend + edge
step "2/2  Frontend and edge"

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
#
# ONLY WHEN IT HAS ACTUALLY MOVED. The ALB survives a code deploy — it is
# recreated by `aws-up`, not by helm — so on a normal merge the origin is
# already correct. Skipping the no-op saves a few minutes per deploy and, more
# importantly, avoids invalidating the entire distribution on every commit.
CURRENT_ALB="$(aws cloudformation describe-stacks --stack-name "$PERSISTENT_STACK" \
	--query "Stacks[0].Parameters[?ParameterKey=='AlbDnsName'].ParameterValue" \
	--output text 2>/dev/null || true)"

if [ "$CURRENT_ALB" = "$ALB_DNS" ]; then
	ok "CloudFront already points at this ALB — nothing to repoint"
	say "invalidating the frontend paths only"
	aws cloudfront create-invalidation --distribution-id "$DISTRIBUTION_ID" \
		--paths '/' '/index.html' '/assets/*' >/dev/null
	ok "invalidated"
else
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
fi

if [ "$SUMMARY" = "1" ]; then
	step "Deployed"
	printf "\n  URL        %s\n  Image tag  %s\n\n" "$APP_URL" "$TAG"
fi
