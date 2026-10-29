from typing import Dict, List, Tuple

def held_karp_tsp(
    dist_matrix: List[List[float]], start: int = 0
) -> Tuple[List[int], float]:
    """
    Solve the Traveling Salesperson Problem (TSP) to exact optimality
    using the Held-Karp Dynamic Programming algorithm with bitmasking.

    State definition:
        dp[mask][u] = minimum cost of a path starting at 'start', visiting exactly
                      the subset of vertices represented by the bitmask 'mask',
                      and terminating at vertex 'u'.

    Recurrence relation:
        dp[mask][u] = min_{v in mask, v != u} (dp[mask \\ {u}][v] + dist_matrix[v][u])

    Args:
        dist_matrix: Symmetric or asymmetric N x N cost matrix.
        start: Starting and ending vertex (depot index), default is 0.

    Returns:
        A tuple of:
            - route: The exact optimal sequence of stops starting and ending at 'start'.
            - total_distance: The minimum total distance of the tour.

    Computational Complexity:
        - Time: O(N^2 * 2^N)
        - Space: O(N * 2^N)
        Recommended only for N <= 16 due to exponential growth.
    """
    n = len(dist_matrix)
    if n == 0:
        return [], 0.0
    if n == 1:
        return [start, start], 0.0
    if n == 2:
        other = 1 if start == 0 else 0
        dist = dist_matrix[start][other] + dist_matrix[other][start]
        return [start, other, start], dist

    if n > 18:
        raise ValueError(
            f"Held-Karp DP cannot run on N={n} (N > 18) due to exponential memory limits. "
            "Use Nearest Neighbor or ML-pruned DP instead."
        )

    # dp[mask] will store a mapping of vertex u -> min_cost
    # parent[mask] will store vertex u -> previous vertex v in the optimal path
    dp: List[Dict[int, float]] = [{} for _ in range(1 << n)]
    parent: List[Dict[int, int]] = [{} for _ in range(1 << n)]

    # Base case: start at the depot
    start_mask = 1 << start
    dp[start_mask][start] = 0.0

    # Iterate through all subset sizes from 1 to n
    for mask in range(1 << n):
        # The subset must include the start depot
        if not (mask & (1 << start)):
            continue

        for u, current_cost in list(dp[mask].items()):
            # Try to extend path to all unvisited vertices v
            for v in range(n):
                if not (mask & (1 << v)):
                    next_mask = mask | (1 << v)
                    new_cost = current_cost + dist_matrix[u][v]

                    if v not in dp[next_mask] or new_cost < dp[next_mask][v]:
                        dp[next_mask][v] = new_cost
                        parent[next_mask][v] = u

    # Find the optimal return to depot from full visited mask
    full_mask = (1 << n) - 1
    min_total_cost = float("inf")
    best_last_node = -1

    for last_node, cost in dp[full_mask].items():
        if last_node == start:
            continue
        total_cost = cost + dist_matrix[last_node][start]
        if total_cost < min_total_cost:
            min_total_cost = total_cost
            best_last_node = last_node

    if best_last_node == -1:
        return [], float("inf")

    # Reconstruct optimal path backwards
    route: List[int] = []
    curr = best_last_node
    curr_mask = full_mask

    while curr != start:
        route.append(curr)
        prev = parent[curr_mask][curr]
        curr_mask = curr_mask ^ (1 << curr)
        curr = prev

    route.append(start)
    route.reverse()
    route.append(start)

    return route, min_total_cost
