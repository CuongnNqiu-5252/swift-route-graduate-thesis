# Routing and optimisation specification

## Status and scope

The deployment plan specifies Held–Karp bitmask DP for small single-driver TSP, Nearest Neighbor and Genetic Algorithm baselines, then ML top-k pruning and clustering/assignment. The repository currently contains Python dependencies (FastAPI, NumPy, scikit-learn, Torch) but no implementation. All algorithm behaviour below is therefore **planned**, not verified implementation.

## Contract and constraints

Batch input is a server-authorised snapshot: tenant settings/weights, candidate order IDs with pickup/delivery coordinates, UTC windows, weight/priority, drivers with capacity/shift/start location, and optional traffic matrix/version. Output is a job/plan with per-driver ordered stops, ETA, unassigned orders with reason, total distance/duration/objective, algorithm and model version, input hash and constraint violations (must be empty for accepted plan).

Constraints: tenant separation; one stop/order per accepted plan; capacity; driver shift; service/travel time; delivery windows; cancellation/staleness; optional urgent priority. Objective is a configured weighted time/cost function, initially tenant `time_weight + cost_weight = 1`. Metrics: feasibility rate, optimisation duration, distance/duration, on-time rate, objective cost, and percent deviation from optimum for instances where DP ground truth exists.

## Algorithm and operations

1. Validate and cluster/assign feasible orders to drivers.
2. Run exact Held–Karp only under a configured small `n` limit; use baseline heuristic/GA for larger sets.
3. ML pruning is experimental: training labels come from complete small DP transitions; features include distance, deadline slack and neighbourhood density. Record feature/model version and compare with no-pruning baseline.
4. On timeout/infeasible/error, return best feasible baseline if one exists; otherwise `FAILED`/`INFEASIBLE`, never publish partial plan as accepted.

Training artefacts reside in object storage; `model_registry` retains URI, metrics and activated version. Tests include brute-force equality at tiny `n`, constraint/property tests, deterministic seed regression fixtures, performance benchmarks at plan sizes 10/30/50/100, and event/API contract tests.
