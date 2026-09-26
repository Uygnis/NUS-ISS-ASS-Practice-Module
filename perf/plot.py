#!/usr/bin/env python3
"""RentEZ - turn one perf/run.sh results directory into the report chart.

    pip install matplotlib
    python3 perf/plot.py perf/results/stress-20260923-140000

Writes chart.png into that directory: request rate and p95 latency on top,
replicas per deployment and node count underneath, on one time axis. That
alignment is the whole argument for scalability - load rises, latency rises,
replicas and nodes follow, latency comes back down while load is still high.
Without scaling.csv (a local run) it draws the top panel only.
"""
import csv
import sys
from collections import defaultdict
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402

BUCKET = 10  # seconds


def p95(values):
    values = sorted(values)
    return values[min(len(values) - 1, int(len(values) * 0.95))] if values else 0


def load_k6(path):
    reqs, durations, failed = defaultdict(int), defaultdict(list), defaultdict(int)
    with open(path) as f:
        for row in csv.DictReader(f):
            bucket = int(row["timestamp"]) // BUCKET * BUCKET
            if row["metric_name"] == "http_reqs":
                reqs[bucket] += 1
            elif row["metric_name"] == "http_req_duration":
                durations[bucket].append(float(row["metric_value"]))
            elif row["metric_name"] == "http_req_failed" and row["metric_value"] == "1":
                failed[bucket] += 1
    times = sorted(reqs)
    return (
        times,
        [reqs[t] / BUCKET for t in times],
        [p95(durations[t]) for t in times],
        [failed[t] / BUCKET for t in times],
    )


def load_scaling(path):
    per_deploy, nodes = defaultdict(list), {}
    with open(path) as f:
        for row in csv.DictReader(f):
            t = int(row["epoch"])
            per_deploy[row["deployment"]].append((t, int(row["current_replicas"] or 0)))
            nodes[t] = int(row["ready_nodes"] or 0)
    return per_deploy, sorted(nodes.items())


def main(run_dir):
    run_dir = Path(run_dir)
    times, rps, lat, err = load_k6(run_dir / "metrics.csv")
    t0 = times[0]
    mins = lambda ts: [(t - t0) / 60 for t in ts]  # noqa: E731

    scaling = run_dir / "scaling.csv"
    panels = 2 if scaling.exists() else 1
    fig, axes = plt.subplots(panels, 1, figsize=(12, 4 * panels), sharex=True, squeeze=False)
    top = axes[0][0]

    top.plot(mins(times), rps, label="requests/s", color="tab:blue")
    top.plot(mins(times), err, label="failed/s", color="tab:red", linewidth=1)
    top.set_ylabel("requests / s")
    lat_ax = top.twinx()
    lat_ax.plot(mins(times), lat, label="p95 latency", color="tab:orange")
    lat_ax.set_ylabel("p95 latency (ms)")
    lines = top.get_legend_handles_labels()
    lat_lines = lat_ax.get_legend_handles_labels()
    top.legend(lines[0] + lat_lines[0], lines[1] + lat_lines[1], loc="upper left")
    top.set_title(f"RentEZ {run_dir.name}")
    top.grid(alpha=0.3)

    if panels == 2:
        bottom = axes[1][0]
        per_deploy, nodes = load_scaling(scaling)
        for name, points in sorted(per_deploy.items()):
            bottom.step(mins([t for t, _ in points]), [r for _, r in points], where="post", label=name)
        bottom.step(mins([t for t, _ in nodes]), [n for _, n in nodes], where="post",
                    label="ready nodes", color="black", linestyle="--", linewidth=2)
        bottom.set_ylabel("pods / nodes")
        bottom.legend(loc="upper left", fontsize=8)
        bottom.grid(alpha=0.3)

    axes[-1][0].set_xlabel("minutes since start")
    fig.tight_layout()
    out = run_dir / "chart.png"
    fig.savefig(out, dpi=150)
    print(out)


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])
