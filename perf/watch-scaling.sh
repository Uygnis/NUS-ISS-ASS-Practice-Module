#!/usr/bin/env bash
# RentEZ - record what the autoscalers do while a load test runs.
#
#   perf/watch-scaling.sh perf/results/<run>/scaling.csv [interval-seconds]
#
# Every INTERVAL seconds, appends one row per HPA-managed deployment:
#   epoch,deployment,current_replicas,desired_replicas,cpu_pct,ready_nodes,pending_pods
# and prints the same as a live table for the demo. Stop it with Ctrl-C (or
# kill; perf/run.sh does that when k6 finishes).

set -uo pipefail

OUT="${1:?usage: watch-scaling.sh <out.csv> [interval]}"
INTERVAL="${2:-15}"
NAMESPACE="${NAMESPACE:-rentez}"

mkdir -p "$(dirname "$OUT")"
[ -s "$OUT" ] || echo "epoch,deployment,current_replicas,desired_replicas,cpu_pct,ready_nodes,pending_pods" >"$OUT"

while :; do
	now=$(date +%s)
	nodes=$(kubectl get nodes --no-headers 2>/dev/null | awk '$2=="Ready"' | wc -l | tr -d ' ')
	pending=$(kubectl get pods -n "$NAMESPACE" --field-selector=status.phase=Pending --no-headers 2>/dev/null | wc -l | tr -d ' ')

	rows=$(kubectl get hpa -n "$NAMESPACE" -o jsonpath='{range .items[*]}{.spec.scaleTargetRef.name},{.status.currentReplicas},{.status.desiredReplicas},{.status.currentMetrics[0].resource.current.averageUtilization}{"\n"}{end}' 2>/dev/null)

	printf "\n%s  nodes=%s  pending_pods=%s\n" "$(date '+%H:%M:%S')" "$nodes" "$pending"
	printf "  %-24s %8s %8s %6s\n" deployment current desired cpu%
	while IFS=, read -r name cur des cpu; do
		[ -n "$name" ] || continue
		printf "  %-24s %8s %8s %6s\n" "$name" "$cur" "$des" "${cpu:--}"
		echo "$now,$name,$cur,$des,${cpu:-},$nodes,$pending" >>"$OUT"
	done <<<"$rows"

	sleep "$INTERVAL"
done
