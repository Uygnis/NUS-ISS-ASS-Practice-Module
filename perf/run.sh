#!/usr/bin/env bash
# RentEZ - run one k6 test and keep the evidence.
#
#   perf/run.sh <smoke|load|stress|spike>                 against the local gateway
#   TARGET=aws perf/run.sh stress                          against the deployed CloudFront URL
#   TARGET=cluster perf/run.sh stress                      k6 as a Job inside EKS, hitting the ALB
#   BASE_URL=https://... perf/run.sh load                  anywhere else
#
# Extra env passes through to k6 (RATE=, PEAK=). Everything lands in
# perf/results/<test>-<timestamp>/: k6 summary JSON, raw CSV metrics, the k6
# HTML report, and - when a cluster is involved - scaling.csv from
# watch-scaling.sh. perf/plot.py turns that directory into one chart.

set -euo pipefail

TEST="${1:?usage: run.sh <smoke|load|stress|spike>}"
TARGET="${TARGET:-local}"
PERF_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(dirname "$PERF_DIR")"
SCRIPT="$PERF_DIR/$TEST.js"
[ -f "$SCRIPT" ] || { echo "no such test: $SCRIPT" >&2; exit 1; }

RUN_DIR="$PERF_DIR/results/$TEST-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$RUN_DIR"
NAMESPACE="${NAMESPACE:-rentez}"
export NAMESPACE

# --------------------------------------------------------------- base url
if [ -z "${BASE_URL:-}" ]; then
	case "$TARGET" in
	local) BASE_URL="http://localhost:8080" ;;
	aws)
		# Same lookup `make aws-status` uses for its "url" row.
		BASE_URL="$(
			set +u
			source "$REPO_ROOT/aws/scripts/lib.sh"
			require_credentials >/dev/null
			adopt_legacy_stack >/dev/null 2>&1 || true
			stack_output "$ENVIRONMENT_STACK" AppUrl
		)"
		;;
	cluster)
		# Straight at the ALB, skipping CloudFront - so the load is on the pods,
		# not on the edge, and not limited by a laptop's uplink.
		BASE_URL="http://$(kubectl get ingress -n "$NAMESPACE" -o jsonpath='{.items[0].status.loadBalancer.ingress[0].hostname}')"
		;;
	*) echo "TARGET must be local, aws or cluster" >&2; exit 1 ;;
	esac
fi
[ -n "$BASE_URL" ] || { echo "could not resolve BASE_URL for TARGET=$TARGET" >&2; exit 1; }
echo "$TEST against $BASE_URL -> $RUN_DIR"

# ------------------------------------------------------- scaling evidence
WATCH_PID=""
if [ "$TARGET" != "local" ] && kubectl get hpa -n "$NAMESPACE" >/dev/null 2>&1; then
	bash "$PERF_DIR/watch-scaling.sh" "$RUN_DIR/scaling.csv" 15 >"$RUN_DIR/watch.log" 2>&1 &
	WATCH_PID=$!
	echo "recording HPA/node counts to scaling.csv (pid $WATCH_PID); live view: tail -f $RUN_DIR/watch.log"
fi
# Keep recording five minutes past the end, so the slow scale-down is on the
# chart too. Ctrl-C skips the wait.
stop_watch() {
	[ -n "$WATCH_PID" ] || return 0
	if [ "$TEST" != "smoke" ] && [ -z "${NO_COOLDOWN:-}" ]; then
		echo "load finished; recording scale-down for 6 more minutes (Ctrl-C to stop)"
		sleep 360 || true
	fi
	kill "$WATCH_PID" 2>/dev/null || true
}
trap stop_watch EXIT

# ------------------------------------------------------------------- run
PASS_ENV=(-e "BASE_URL=$BASE_URL")
for v in RATE PEAK; do [ -n "${!v:-}" ] && PASS_ENV+=(-e "$v=${!v}"); done

if [ "$TARGET" = "cluster" ]; then
	JOB="k6-$TEST"
	kubectl delete job "$JOB" -n "$NAMESPACE" --ignore-not-found >/dev/null
	kubectl create configmap k6-scripts -n "$NAMESPACE" \
		--from-file="$PERF_DIR/$TEST.js" --from-file=flow.js="$PERF_DIR/lib/flow.js" \
		--dry-run=client -o yaml | kubectl apply -f - >/dev/null
	sed -e "s|__TEST__|$TEST|g" -e "s|__NAMESPACE__|$NAMESPACE|g" -e "s|__BASE_URL__|$BASE_URL|g" \
		-e "s|__RATE__|${RATE:-}|g" -e "s|__PEAK__|${PEAK:-}|g" \
		"$PERF_DIR/k8s/k6-job.yaml" | kubectl apply -f - >/dev/null
	kubectl wait -n "$NAMESPACE" --for=condition=Ready pod -l job-name="$JOB" --timeout=180s >/dev/null
	kubectl logs -n "$NAMESPACE" -f "job/$JOB" | tee "$RUN_DIR/k6.log"
	kubectl delete job "$JOB" -n "$NAMESPACE" --ignore-not-found >/dev/null
	exit 0
fi

command -v k6 >/dev/null || { echo "k6 is not installed: brew install k6" >&2; exit 1; }
set +e
K6_WEB_DASHBOARD=true K6_WEB_DASHBOARD_EXPORT="$RUN_DIR/report.html" \
	k6 run "${PASS_ENV[@]}" \
	--summary-export "$RUN_DIR/summary.json" \
	--out csv="$RUN_DIR/metrics.csv" \
	"$SCRIPT" | tee "$RUN_DIR/k6.log"
STATUS=${PIPESTATUS[0]}
set -e
echo
[ "$STATUS" -eq 0 ] && echo "PASS - every threshold met" || echo "FAIL - k6 exited $STATUS (a threshold was breached, see above)"
echo "evidence: $RUN_DIR   chart: python3 perf/plot.py $RUN_DIR"
exit "$STATUS"
