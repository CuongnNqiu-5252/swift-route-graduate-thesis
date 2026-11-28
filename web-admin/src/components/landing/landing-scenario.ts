/**
 * Sample dispatch scenario for the landing hero.
 * One depot and twelve delivery stops on a 640 x 400 canvas. Distances, arrival times and
 * lateness are computed from the coordinates, so every figure on screen adds up.
 * The scenario is illustrative; the production engine solves the real problem with OR-Tools.
 */
export type Point = readonly [number, number];
export type PlanMode = 'manual' | 'optimized';
export type FleetSize = 2 | 3;

export const MAP_WIDTH = 640;
export const MAP_HEIGHT = 400;
export const DEPOT: Point = [300, 330];
export const URGENT_STOP_ID = 13;

export const STOPS: Record<number, Point> = {
  1: [70, 90], 2: [130, 150], 3: [90, 230], 4: [170, 60], 5: [200, 200], 6: [300, 110],
  7: [450, 80], 8: [540, 140], 9: [430, 170], 10: [560, 260], 11: [480, 320], 12: [380, 250],
  13: [250, 150],
};

/** Latest acceptable arrival, in minutes after departure. */
export const WINDOW_END: Record<number, number> = {
  1: 70, 2: 85, 3: 100, 4: 80, 5: 115, 6: 35, 7: 55, 8: 70, 9: 90, 10: 85, 11: 100, 12: 70, 13: 45,
};

/** Stop order per vehicle. Manual follows spreadsheet order, optimized minimises distance. */
export const PLANS: Record<PlanMode, Record<FleetSize, number[][]>> = {
  manual: {
    2: [[1, 3, 5, 7, 9, 11], [2, 4, 6, 8, 10, 12]],
    3: [[1, 4, 7, 10], [2, 5, 8, 11], [3, 6, 9, 12]],
  },
  optimized: {
    2: [[6, 4, 1, 2, 3, 5], [12, 9, 7, 8, 10, 11]],
    3: [[3, 2, 1, 4, 5], [6, 7, 8, 9], [11, 10, 12]],
  },
};

export const ROUTE_COLORS = ['#2563EB', '#059669', '#D97706'] as const;

const KM_PER_PIXEL = 0.03;
const SPEED_KMH = 18;
const SERVICE_MINUTES = 5;
const DEPARTURE_MINUTES = 8 * 60;

export interface RouteResult {
  stops: number[];
  km: number;
  /** Clock time, in minutes from midnight, when the vehicle is back at the depot. */
  returnClock: number;
  arrivals: Record<number, number>;
  late: number[];
  durationMinutes: number;
}

export interface PlanResult {
  routes: RouteResult[];
  totalKm: number;
  latestReturnClock: number;
  lateCount: number;
  urgent?: { vehicleIndex: number; addedMinutes: number; late: boolean };
}

function pointOf(id: number): Point {
  return id === 0 ? DEPOT : STOPS[id];
}

function legKm(a: number, b: number): number {
  const [ax, ay] = pointOf(a);
  const [bx, by] = pointOf(b);
  return Math.hypot(ax - bx, ay - by) * KM_PER_PIXEL;
}

export function evaluateRoute(stops: number[]): RouteResult {
  let minutes = 0;
  let km = 0;
  let previous = 0;
  const arrivals: Record<number, number> = {};
  for (const id of stops) {
    const leg = legKm(previous, id);
    km += leg;
    minutes += (leg / SPEED_KMH) * 60;
    arrivals[id] = Math.round(minutes);
    minutes += SERVICE_MINUTES;
    previous = id;
  }
  const back = legKm(previous, 0);
  km += back;
  minutes += (back / SPEED_KMH) * 60;
  return {
    stops,
    km,
    durationMinutes: minutes,
    returnClock: DEPARTURE_MINUTES + Math.round(minutes),
    arrivals,
    late: stops.filter((id) => arrivals[id] > WINDOW_END[id]),
  };
}

function summarize(routes: RouteResult[], urgent?: PlanResult['urgent']): PlanResult {
  return {
    routes,
    totalKm: routes.reduce((sum, route) => sum + route.km, 0),
    latestReturnClock: Math.max(...routes.map((route) => route.returnClock)),
    lateCount: routes.reduce((sum, route) => sum + route.late.length, 0),
    urgent,
  };
}

/**
 * Builds the plan, optionally with one urgent stop.
 * Optimized: cheapest insertion by distance. Manual: appended to the shortest route.
 */
export function buildPlan(mode: PlanMode, fleet: FleetSize, withUrgent: boolean): PlanResult {
  const base = PLANS[mode][fleet];
  if (!withUrgent) return summarize(base.map(evaluateRoute));

  let vehicleIndex = 0;
  let position = 0;
  if (mode === 'manual') {
    vehicleIndex = base.reduce((best, route, index) => (route.length < base[best].length ? index : best), 0);
    position = base[vehicleIndex].length;
  } else {
    let bestCost = Number.POSITIVE_INFINITY;
    base.forEach((route, v) => {
      const before = evaluateRoute(route).km;
      for (let i = 0; i <= route.length; i += 1) {
        const candidate = [...route.slice(0, i), URGENT_STOP_ID, ...route.slice(i)];
        const cost = evaluateRoute(candidate).km - before;
        if (cost < bestCost) {
          bestCost = cost;
          vehicleIndex = v;
          position = i;
        }
      }
    });
  }

  const plan = base.map((route, v) => (v === vehicleIndex
    ? [...route.slice(0, position), URGENT_STOP_ID, ...route.slice(position)]
    : route));
  const routes = plan.map(evaluateRoute);
  const addedMinutes = Math.round(routes[vehicleIndex].durationMinutes - evaluateRoute(base[vehicleIndex]).durationMinutes);
  return summarize(routes, {
    vehicleIndex,
    addedMinutes,
    late: routes[vehicleIndex].late.includes(URGENT_STOP_ID),
  });
}

export function formatClock(minutesFromMidnight: number): string {
  const hours = Math.floor(minutesFromMidnight / 60) % 24;
  const minutes = minutesFromMidnight % 60;
  return `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}`;
}

export function stopClock(minutesAfterDeparture: number): string {
  return formatClock(DEPARTURE_MINUTES + minutesAfterDeparture);
}
