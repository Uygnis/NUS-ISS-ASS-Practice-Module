#!/usr/bin/env bash
# RentEZ — build and push the five service images to ECR.
#
#   make aws-images              tag with the current commit
#   make aws-images TAG=wip1     tag explicitly
#
# Normally you do NOT run this by hand: .github/workflows/build-images.yml
# builds and pushes on every merge to main, so `make aws-up` just deploys a tag
# that already exists. Use this when you want to test an unmerged branch on the
# real cluster.

source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

require_tools aws docker git
require_credentials
require_persistent_stack

REGISTRY="$(stack_output "$PERSISTENT_STACK" EcrRegistry)"
TAG="${TAG:-$(git -C "$REPO_ROOT" rev-parse --short=7 HEAD)}"

say "registry $REGISTRY"
say "tag      $TAG"

aws ecr get-login-password --region "$AWS_REGION" \
	| docker login --username AWS --password-stdin "$REGISTRY" >/dev/null
ok "logged in to ECR"

for svc in "${SERVICES[@]}"; do
	step "$svc"
	# --platform linux/amd64 is REQUIRED when building on an Apple Silicon Mac.
	# Without it the image is arm64, the t3.large nodes are x86, and the pod
	# fails with "exec format error" - which does not mention architecture
	# anywhere and reads like a corrupt image.
	# --provenance=false: without it, buildx attaches a provenance attestation and
	# pushes an image INDEX rather than a plain image. ECR then shows extra
	# "unknown/unknown" artefacts, and each one counts towards the repository's
	# "keep last 5 images" lifecycle rule - so real images get evicted roughly
	# twice as fast as intended.
	docker build \
		--provenance=false \
		--platform linux/amd64 \
		--file "$REPO_ROOT/services/$svc/Dockerfile" \
		--tag "$REGISTRY/rentez-$svc:$TAG" \
		"$REPO_ROOT/services/$svc"
	docker push "$REGISTRY/rentez-$svc:$TAG"
	ok "pushed rentez-$svc:$TAG"
done

step "Done"
printf "\n  Deploy it with:  make aws-up TAG=%s\n\n" "$TAG"
