from typing import List, Tuple

def nearest_neighbor_tsp(
    dist_matrix: List[List[float]], start: int = 0
) -> Tuple[List[int], float]:
    """
    Solve the Traveling Salesperson Problem (TSP) using the Nearest Neighbor heuristic.

    Greedy principle: From the current stop, repeatedly transition to the nearest
    unvisited stop until all stops are visited, then return to the starting depot.

    Args:
        dist_matrix: N x N distance or cost matrix where dist_matrix[i][j] is the cost from i to j.
        start: Index of the starting location (depot), default is 0.

    Returns:
        A tuple of:
            - route: List of stop indices starting and ending at 'start' (length N + 1).
            - total_distance: Total distance/cost of the complete round trip.

    Computational Complexity:
        - Time: O(N^2) where N is the number of locations.
        - Space: O(N) to store visited states and the resulting route.
    """
    n = len(dist_matrix)
    if n == 0:
        return [], 0.0
    if n == 1:
        return [start, start], 0.0

    visited = [False] * n
    route: List[int] = [start]
    visited[start] = True
    total_distance = 0.0
    current_node = start

    # Visit all remaining n - 1 nodes
    for _ in range(n - 1):
        nearest_node = -1
        min_dist = float("inf")

        for candidate in range(n):
            if not visited[candidate]:
                cost = dist_matrix[current_node][candidate]
                if cost < min_dist:
                    min_dist = cost
                    nearest_node = candidate

        if nearest_node == -1:
            break

        visited[nearest_node] = True
        route.append(nearest_node)
        total_distance += min_dist
        current_node = nearest_node

    # Complete the tour by returning to the depot
    return_dist = dist_matrix[current_node][start]
    route.append(start)
    total_distance += return_dist

    return route, total_distance
