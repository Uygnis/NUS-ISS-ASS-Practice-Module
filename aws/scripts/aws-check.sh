#!/usr/bin/env bash
# RentEZ — AWS preflight. The mirror of `make check`, for the cloud half.
#
#   make aws-check
#
# Read-only and free. Run it before `make aws-bootstrap` on a new machine, and
# any time something behaves oddly - most AWS problems in this project are a
# missing tool or an expired session rather than anything in the templates.

# Deliberately NOT `set -e`: this script's job is to report every problem at
# once, not to stop at the first one.
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh" 2>/dev/null || true
set +e

green() { printf "    \033[0;32mok\033[0m   %-12s %s\n" "$1" "$2"; }
red()   { printf "    \033[0;31mMISS\033[0m %-12s %s\n" "$1" "$2"; }
amber() { printf "    \033[0;33mwarn\033[0m %-12s %s\n" "$1" "$2"; }

fail=0

printf "\n  Required tools\n"
# $1 command, $2 install hint, $3... how to ask it for its version.
# Not every tool takes --version: kubectl wants `version --client` and helm
# wants `version --short`, and calling them with --version prints an error that
# then gets displayed as though it were the version string.
check_tool() {
	local name="$1" hint="$2"; shift 2
	local version
	if command -v "$name" >/dev/null 2>&1; then
		version="$("$name" "$@" 2>&1 | head -n1 | cut -c1-52)"
		green "$name" "$version"
	else
		red "$name" "$hint"
		fail=1
	fi
}
check_tool aws      "brew install awscli"                                  --version
check_tool eksctl   "brew tap weaveworks/tap && brew install eksctl"       version
check_tool kubectl  "brew install kubectl"                                 version --client
check_tool helm     "brew install helm"                                    version --short
check_tool envsubst "brew install gettext && brew link --force gettext"    --version
check_tool python3  "ships with macOS"                                     --version
check_tool git      "brew install git"                                     --version
check_tool npm      "brew install node   (needed to build the frontend bundle)" --version

printf "\n  Credentials\n"
if IDENTITY="$(aws sts get-caller-identity --output json 2>&1)"; then
	ACCOUNT="$(printf '%s' "$IDENTITY" | python3 -c 'import json,sys; print(json.load(sys.stdin)["Account"])' 2>/dev/null)"
	ARN="$(printf '%s' "$IDENTITY" | python3 -c 'import json,sys; print(json.load(sys.stdin)["Arn"])' 2>/dev/null)"
	green "identity" "$ARN"
	green "account" "$ACCOUNT"
else
	red "identity" "no usable credentials — run 'aws configure' or 'aws sso login'"
	fail=1
fi

REGION="${AWS_REGION:-$(aws configure get region 2>/dev/null)}"
if [ -n "$REGION" ]; then
	if [ "$REGION" = "ap-southeast-1" ]; then
		green "region" "$REGION"
	else
		# Not fatal, but worth flagging: the architecture doc, the cost figures
		# and the RDS free-tier assumptions are all written for Singapore.
		amber "region" "$REGION — the docs and cost estimates assume ap-southeast-1"
	fi
else
	red "region" "not set — export AWS_REGION or run 'aws configure'"
	fail=1
fi

printf "\n  Account state\n"
if [ "$fail" -eq 0 ]; then
	for stack in rentez-guardrails rentez-persistent rentez-database; do
		status="$(aws cloudformation describe-stacks --stack-name "$stack" \
			--query 'Stacks[0].StackStatus' --output text 2>/dev/null)"
		if [ -z "$status" ]; then
			case "$stack" in
				rentez-database) green "$stack" "absent (correct when torn down)" ;;
				*)               amber "$stack" "absent — run 'make aws-bootstrap'" ;;
			esac
		else
			green "$stack" "$status"
		fi
	done

	EXPIRES="$(aws ssm get-parameter --name /rentez/env/expires-at \
		--query Parameter.Value --output text 2>/dev/null)"
	if [ -n "$EXPIRES" ] && [ "$EXPIRES" != "none" ]; then
		amber "lease" "$EXPIRES — something is running. 'make aws-status' for details"
	fi
fi

printf "\n"
if [ "$fail" -eq 0 ]; then
	printf "  \033[0;32mReady.\033[0m Next: make aws-bootstrap NOTIFY_EMAIL=you@u.nus.edu\n\n"
else
	printf "  \033[0;31mFix the items above, then re-run: make aws-check\033[0m\n\n"
	exit 1
fi
