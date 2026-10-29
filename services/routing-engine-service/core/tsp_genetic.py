import random
from typing import List, Tuple

def compute_tour_distance(tour: List[int], dist_matrix: List[List[float]]) -> float:
    """Calculate the total round-trip distance of a complete tour."""
    total = 0.0
    for i in range(len(tour) - 1):
        total += dist_matrix[tour[i]][tour[i + 1]]
    return total


def ordered_crossover(parent1: List[int], parent2: List[int]) -> List[int]:
    """
    Ordered Crossover (OX) for permutation-encoded chromosomes.
    Preserves relative order of stops from parent1 and fills in remaining from parent2.
    """
    size = len(parent1)
    if size <= 1:
        return list(parent1)

    pt1, pt2 = sorted(random.sample(range(size), 2))

    child = [None] * size
    # Copy segment from parent1
    child[pt1 : pt2 + 1] = parent1[pt1 : pt2 + 1]

    # Fill remaining from parent2 in circular order
    p2_idx = (pt2 + 1) % size
    child_idx = (pt2 + 1) % size

    filled = 0
    target_count = size - (pt2 - pt1 + 1)
    in_child = set(child[pt1 : pt2 + 1])

    while filled < target_count:
        candidate = parent2[p2_idx]
        if candidate not in in_child:
            child[child_idx] = candidate
            in_child.add(candidate)
            child_idx = (child_idx + 1) % size
            filled += 1
        p2_idx = (p2_idx + 1) % size

    return child


def swap_mutation(individual: List[int], mutation_rate: float = 0.02) -> List[int]:
    """Swap two random genes in the permutation with given probability."""
    mutated = list(individual)
    if len(mutated) < 2:
        return mutated

    if random.random() < mutation_rate:
        idx1, idx2 = random.sample(range(len(mutated)), 2)
        mutated[idx1], mutated[idx2] = mutated[idx2], mutated[idx1]

    return mutated


def genetic_tsp(
    dist_matrix: List[List[float]],
    start: int = 0,
    population_size: int = 60,
    generations: int = 250,
    mutation_rate: float = 0.05,
    tournament_size: int = 4,
    random_seed: int = 42,
) -> Tuple[List[int], float]:
    """
    Solve the Traveling Salesperson Problem (TSP) using a Genetic Algorithm (GA).

    Characteristics:
        - Chromosome: Permutation of intermediate delivery stops (excluding start depot).
        - Selection: Tournament Selection.
        - Crossover: Ordered Crossover (OX1).
        - Mutation: Swap Mutation.
        - Elitism: Best individual from previous generation is carried over directly.

    Args:
        dist_matrix: N x N distance matrix.
        start: Starting and ending node index (depot), default is 0.
        population_size: Number of candidate tours per generation.
        generations: Number of evolutionary iterations.
        mutation_rate: Probability of mutation occurring in an offspring.
        tournament_size: Number of competitors in tournament selection.
        random_seed: Random seed for reproducibility.

    Returns:
        A tuple of:
            - best_route: Tour starting and ending at 'start'.
            - best_distance: Total distance of the best tour found.

    Computational Complexity:
        - Time: O(generations * population_size * N)
        - Space: O(population_size * N)
    """
    random.seed(random_seed)
    n = len(dist_matrix)
    if n == 0:
        return [], 0.0
    if n == 1:
        return [start, start], 0.0
    if n == 2:
        other = 1 if start == 0 else 0
        cost = dist_matrix[start][other] + dist_matrix[other][start]
        return [start, other, start], cost

    # Other nodes to visit
    stops = [i for i in range(n) if i != start]

    # Initialize population with random permutations
    population: List[List[int]] = []
    for _ in range(population_size):
        ind = list(stops)
        random.shuffle(ind)
        population.append(ind)

    def evaluate_chromosome(chromosome: List[int]) -> float:
        full_tour = [start] + chromosome + [start]
        return compute_tour_distance(full_tour, dist_matrix)

    best_individual = min(population, key=evaluate_chromosome)
    best_distance = evaluate_chromosome(best_individual)

    for _ in range(generations):
        # Evaluate fitness for current population (lower distance = higher fitness)
        scores = [evaluate_chromosome(ind) for ind in population]

        # Update global best
        for ind, score in zip(population, scores):
            if score < best_distance:
                best_distance = score
                best_individual = list(ind)

        # Generate next generation with Elitism
        new_population: List[List[int]] = [list(best_individual)]

        # Tournament selection helper
        def tournament_select() -> List[int]:
            candidates = random.sample(population, tournament_size)
            return min(candidates, key=evaluate_chromosome)

        while len(new_population) < population_size:
            parent1 = tournament_select()
            parent2 = tournament_select()
            child = ordered_crossover(parent1, parent2)
            child = swap_mutation(child, mutation_rate)
            new_population.append(child)

        population = new_population

    final_tour = [start] + best_individual + [start]
    return final_tour, best_distance
