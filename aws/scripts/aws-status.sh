#!/usr/bin/env bash
# RentEZ — what is running, what it costs, and when it disappears.
#
#   make aws-status
#
# Read-only and quick. Run it before asking anyone why something is broken, and
# run it at the end of the day to check you actually tore down.

source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

require_tools aws
require_credentials

printf "\n  account %s · region %s\n\n" "$AWS_ACCOUNT_ID" "$AWS_REGION"

row() { printf "  %-22s %s\n" "$1" "$2"; }

# ------------------------------------------------------------------ stacks
row "guardrails" "$(stack_status "$GUARDRAILS_STACK")"
row "persistent" "$(stack_status "$PERSISTENT_STACK")"
row "database"   "$(stack_status "$DATABASE_STACK")"

if ! stack_exists "$PERSISTENT_STACK"; then
	printf "\n  Not bootstrapped in this account yet. Run: make aws-bootstrap NOTIFY_EMAIL=you@u.nus.edu\n\n"
	exit 0
fi

row "url" "$(stack_output "$PERSISTENT_STACK" AppUrl)"

# ------------------------------------------------------------------ cluster
printf "\n"
HOURLY=0
if cluster_exists; then
	STATE="$(aws eks describe-cluster --name "$CLUSTER_NAME" --query 'cluster.status' --output text)"
	row "cluster" "$CLUSTER_NAME ($STATE)"
	NODES="$(aws eks list-nodegroups --cluster-name "$CLUSTER_NAME" --query 'nodegroups' --output text 2>/dev/null || true)"
	for ng in $NODES; do
		SIZE="$(aws eks describe-nodegroup --cluster-name "$CLUSTER_NAME" --nodegroup-name "$ng" \
			--query 'nodegroup.scalingConfig.desiredSize' --output text)"
		row "  nodegroup $ng" "$SIZE nodes"
		HOURLY="$(python3 -c "print($HOURLY + 0.10 + $SIZE * 0.033)")"
	done
	[ -n "$NODES" ] || HOURLY="$(python3 -c "print($HOURLY + 0.10)")"
else
	row "cluster" "none"
fi

if stack_exists "$DATABASE_STACK"; then
	row "database" "$(stack_output "$DATABASE_STACK" DbEndpoint 2>/dev/null || echo 'creating')"
	HOURLY="$(python3 -c "print($HOURLY + 0.018)")"
fi

ALBS="$(aws elbv2 describe-load-balancers \
	--query "length(LoadBalancers[?VpcId=='$(stack_output "$PERSISTENT_STACK" VpcId)'])" \
	--output text 2>/dev/null || echo 0)"
if [ "$ALBS" != "0" ]; then
	row "load balancers" "$ALBS"
	HOURLY="$(python3 -c "print($HOURLY + $ALBS * 0.0225)")"
fi

# ------------------------------------------------------------------ the lease
printf "\n"
EXPIRES="$(aws ssm get-parameter --name /rentez/env/expires-at --query Parameter.Value --output text 2>/dev/null || echo none)"
if [ "$EXPIRES" = "none" ]; then
	if [ "$HOURLY" != "0" ]; then
		# The dangerous state: things are running and nothing will stop them.
		row "lease" "DISARMED — nothing will tear this down automatically"
	else
		row "lease" "disarmed (nothing running)"
	fi
else
	REMAINING="$(python3 -c "
import datetime as dt
d = dt.datetime.fromisoformat('$EXPIRES'.replace('Z','+00:00'))
left = (d - dt.datetime.now(dt.timezone.utc)).total_seconds()
print('EXPIRED — the reaper is tearing it down now' if left <= 0
      else '%dh %dm remaining' % (left // 3600, (left % 3600) // 60))
" 2>/dev/null || echo "unparseable — the reaper treats this as expired")"
	row "lease" "$EXPIRES"
	row "" "$REMAINING"
fi

# ------------------------------------------------------------------ cost
printf "\n"
if [ "$HOURLY" = "0" ]; then
	row "burn rate" "\$0.00/hr — idle, roughly \$0.80/month for storage"
else
	row "burn rate" "$(python3 -c "print('~\$%.2f/hr  (~\$%.2f/day if left up)' % ($HOURLY, $HOURLY*24))")"
fi

# ------------------------------------------------------------------ workloads
if cluster_exists && command -v kubectl >/dev/null 2>&1; then
	if kubectl get namespace "$NAMESPACE" >/dev/null 2>&1; then
		printf "\n"
		kubectl get hpa -n "$NAMESPACE" --no-headers 2>/dev/null \
			| awk '{printf "  %-22s %s replicas, cpu %s\n", $1, $6, $3}' || true
		# <unknown> in the cpu column means metrics-server is not working, which
		# is the single most common reason "the HPA does nothing".
	fi
fi

printf "\n"
