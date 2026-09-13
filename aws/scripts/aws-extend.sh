#!/usr/bin/env bash
# RentEZ — push the lease out without redeploying anything.
#
#   make aws-extend              +4 hours from now
#   make aws-extend HOURS=8      +8 hours from now
#
# WHY THIS EXISTS
# The reaper does not care who is using the cluster, only what the deadline says.
# On a shared account the predictable failure is: someone brings the environment
# up at 10am with a four-hour lease, someone else starts testing at half past
# one, and at two o'clock it is deleted underneath them - correctly, because
# nobody extended it.
#
# Before this, the only way to extend was to re-run `make aws-up`, which
# redeploys all five services and rebuilds the frontend to change one timestamp.
# This is that one timestamp.
#
# It does NOT take the environment over. The holder record is left alone, so
# extending someone else's session is a courtesy rather than a claim - which is
# the behaviour you want when a teammate is mid-demo.

source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

HOURS="${HOURS:-4}"

require_tools aws python3
require_credentials
require_persistent_stack

# Refuse on an empty account. Arming the reaper with nothing to reap would leave
# a deadline ticking against no resources, and `aws-status` would then show a
# lease that means nothing.
if ! cluster_exists && ! stack_exists "$DATABASE_STACK"; then
	die "nothing is running — there is no lease to extend. Start with: make aws-up"
fi

CURRENT="$(aws ssm get-parameter --name /rentez/env/expires-at \
	--query Parameter.Value --output text 2>/dev/null || echo none)"
HOLDER="$(holder_name)"
ME="$(caller_name)"

printf "\n"
if [ "$CURRENT" = "none" ]; then
	warn "the lease was DISARMED while resources were running — arming it now"
else
	say "current deadline $CURRENT"
fi

if [ "$HOLDER" != "none" ] && [ "$HOLDER" != "$ME" ]; then
	say "held by $HOLDER since $(holder_since) — extending on their behalf, not taking it over"
fi

DEADLINE="$(arm_reaper "$HOURS")"
ok "lease now runs to $DEADLINE (${HOURS}h from now)"

printf "\n  Still costing roughly \$0.21/hour. Finish with: make aws-down\n\n"
