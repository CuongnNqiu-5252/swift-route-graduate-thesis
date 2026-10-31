import math
import random
from dataclasses import dataclass, field
from typing import List, Optional, Tuple

from core.distance import build_distance_matrix, build_time_matrix


@dataclass
class CustomerStop:
    """Represents a delivery stop / customer order in a routing problem."""
    id: int
    lat: float
    lon: float
    demand: float  # Package weight/load (e.g., in kg)
    time_window_start: float  # Minutes from midnight (e.g., 480.0 = 08:00 AM)
    time_window_end: float  # Minutes from midnight (e.g., 720.0 = 12:00 PM)
    service_time: float  # Service/unloading duration in minutes (e.g., 5.0 - 10.0 min)
    zone: str = "default"


@dataclass
class DeliveryScenario:
    """
    A complete simulation scenario representing a depot, a set of customer orders,
    and fleet operating constraints.
    """
    scenario_id: str
    depot_lat: float
    depot_lon: float
    stops: List[CustomerStop]
    vehicle_capacity: float = 60.0  # Max load per vehicle in kg
    depot_open_time: float = 480.0  # 08:00 AM
    depot_close_time: float = 1080.0  # 18:00 PM (6:00 PM)

    def get_locations(self) -> List[Tuple[float, float]]:
        """
        Returns all coordinates with Depot at index 0, followed by stops 1..N-1.
        Format: [(depot_lat, depot_lon), (stop1_lat, stop1_lon), ...]
        """
        locs = [(self.depot_lat, self.depot_lon)]
        for s in self.stops:
            locs.append((s.lat, s.lon))
        return locs

    def build_matrices(self, avg_speed_kmh: float = 28.0) -> Tuple[List[List[float]], List[List[float]]]:
        """Build both distance matrix (km) and travel time matrix (minutes)."""
        locs = self.get_locations()
        dist_mat = build_distance_matrix(locs)
        time_mat = build_time_matrix(dist_mat, avg_speed_kmh)
        return dist_mat, time_mat


# Realistic urban cluster centers (Can Tho City area)
DEFAULT_CANTHO_ZONES = [
    {"name": "Ninh_Kieu", "center": (10.0371, 105.7828), "radius_km": 2.0},
    {"name": "Cai_Rang", "center": (10.0020, 105.7510), "radius_km": 2.5},
    {"name": "Binh_Thuy", "center": (10.0650, 105.7350), "radius_km": 2.5},
    {"name": "O_Mon", "center": (10.1180, 105.6290), "radius_km": 3.0},
    {"name": "Phong_Dien", "center": (9.9950, 105.6980), "radius_km": 3.0},
]
DEFAULT_HANOI_ZONES = DEFAULT_CANTHO_ZONES


def generate_scenario(
    num_orders: int,
    scenario_id: str = "sim_001",
    depot_coord: Tuple[float, float] = (10.0371, 105.7828),  # Ninh Kieu, Can Tho Central Depot
    zones: Optional[List[dict]] = None,
    clustering_factor: float = 0.8,
    seed: Optional[int] = None,
) -> DeliveryScenario:
    """
    Generate a realistic urban delivery scenario with clustered spatial distribution,
    customer demands, and time windows.

    Args:
        num_orders: Number of customer delivery stops (excluding depot).
        scenario_id: Unique identifier for the scenario.
        depot_coord: (lat, lon) of the central fulfillment warehouse.
        zones: List of urban cluster zones. Defaults to Hanoi major urban zones.
        clustering_factor: Probability (0.0 - 1.0) of a stop belonging to a tight cluster
                           vs uniform random background distribution.
        seed: Random seed for deterministic reproducibility.

    Returns:
        DeliveryScenario object containing depot and all customer stops.
    """
    if seed is not None:
        random.seed(seed)

    if zones is None:
        zones = DEFAULT_HANOI_ZONES

    depot_lat, depot_lon = depot_coord
    stops: List[CustomerStop] = []

    # Earth degrees approx: 1 deg lat ~ 111 km, 1 deg lon ~ 103 km at lat 21 deg
    km_to_lat = 1.0 / 111.0
    km_to_lon = 1.0 / 103.0

    # Common time window buckets in minutes from midnight (08:00 = 480)
    # 1. Morning tight: 08:30 - 10:30 (510 - 630)
    # 2. Morning broad: 08:00 - 12:00 (480 - 720)
    # 3. Afternoon tight: 13:30 - 15:30 (810 - 930)
    # 4. Afternoon broad: 13:00 - 17:00 (780 - 1020)
    # 5. Full day flexible: 08:00 - 17:30 (480 - 1050)
    time_window_presets = [
        (510.0, 630.0),
        (480.0, 720.0),
        (810.0, 930.0),
        (780.0, 1020.0),
        (480.0, 1050.0),
    ]

    for i in range(1, num_orders + 1):
        # 1. Spatial generation (clustered or scattered)
        if random.random() < clustering_factor:
            zone = random.choice(zones)
            zone_name = zone["name"]
            c_lat, c_lon = zone["center"]
            r_km = zone["radius_km"]

            # Generate random point inside cluster circle using polar coordinates
            angle = random.random() * 2.0 * math.pi
            r = math.sqrt(random.random()) * r_km
            lat = c_lat + (r * math.cos(angle)) * km_to_lat
            lon = c_lon + (r * math.sin(angle)) * km_to_lon
        else:
            # Uniform scatter within metropolitan bounding box
            zone_name = "scattered"
            lat = depot_lat + (random.random() - 0.5) * 0.10  # ~11 km span
            lon = depot_lon + (random.random() - 0.5) * 0.12  # ~12 km span

        # 2. Demand: standard parcel weight (1.0 to 12.0 kg, average ~3.5 kg)
        demand = round(random.uniform(1.0, 12.0), 1)

        # 3. Time Window
        tw_start, tw_end = random.choice(time_window_presets)

        # 4. Service time: 3 to 10 minutes per delivery
        service_time = round(random.uniform(4.0, 8.0), 1)

        stops.append(
            CustomerStop(
                id=i,
                lat=lat,
                lon=lon,
                demand=demand,
                time_window_start=tw_start,
                time_window_end=tw_end,
                service_time=service_time,
                zone=zone_name,
            )
        )

    return DeliveryScenario(
        scenario_id=scenario_id,
        depot_lat=depot_lat,
        depot_lon=depot_lon,
        stops=stops,
    )
