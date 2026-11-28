'use client';

import { useMemo, useState } from 'react';
import { AlertTriangle, Zap } from 'lucide-react';
import type { LandingCopy } from './landing-copy';
import {
  DEPOT, MAP_HEIGHT, MAP_WIDTH, ROUTE_COLORS, STOPS, URGENT_STOP_ID,
  buildPlan, formatClock, type FleetSize, type PlanMode,
} from './landing-scenario';
import styles from './landing.module.css';

const FLEETS: FleetSize[] = [2, 3];

/** Fills {name} placeholders. Copy stays plain strings so it can cross the server/client boundary. */
function fill(template: string, values: Record<string, string | number>): string {
  return template.replace(/\{(\w+)\}/g, (_, key: string) => String(values[key] ?? ''));
}
const ROADS = [
  'M0 120 L640 150',
  'M0 280 L640 250',
  'M120 0 L170 400',
  'M330 0 L300 400',
  'M520 0 L560 400',
];

/** The hero's working fragment: a sample dispatch the visitor can re-plan. */
export function HeroDispatchStage({ copy, intlLocale }: { copy: LandingCopy['sim']; intlLocale: string }) {
  const [mode, setMode] = useState<PlanMode>('optimized');
  const [fleet, setFleet] = useState<FleetSize>(2);
  const [urgent, setUrgent] = useState(false);
  const plan = useMemo(() => buildPlan(mode, fleet, urgent), [mode, fleet, urgent]);
  const modeLabel = mode === 'manual' ? copy.manual : copy.optimized;
  const km = plan.totalKm.toLocaleString(intlLocale, { minimumFractionDigits: 1, maximumFractionDigits: 1 });
  const summary = fill(copy.summary, { km, clock: formatClock(plan.latestReturnClock), late: plan.lateCount });
  const urgentSentence = plan.urgent
    ? `${fill(copy.urgentAdded, { vehicle: plan.urgent.vehicleIndex + 1, minutes: plan.urgent.addedMinutes })} ${plan.urgent.late ? copy.urgentLate : copy.urgentOnTime}`
    : '';

  return (
    <div className={styles.sim} data-ui>
      <div className={styles.simBar}>
        <div className={styles.segmented} role="group" aria-label={copy.groupMode}>
          {(['manual', 'optimized'] as PlanMode[]).map((value) => (
            <button key={value} type="button" aria-pressed={mode === value} onClick={() => setMode(value)}>
              {value === 'manual' ? copy.manual : copy.optimized}
            </button>
          ))}
        </div>
        <div className={styles.segmented} role="group" aria-label={copy.groupFleet}>
          {FLEETS.map((value) => (
            <button key={value} type="button" aria-pressed={fleet === value} onClick={() => setFleet(value)}>
              {fill(copy.fleet, { n: value })}
            </button>
          ))}
        </div>
        <button type="button" className={styles.urgentButton} aria-pressed={urgent} onClick={() => setUrgent((value) => !value)}>
          <Zap aria-hidden="true" size={15} />
          {urgent ? copy.urgentOn : copy.urgent}
        </button>
      </div>

      <svg className={styles.simMap} viewBox={`0 0 ${MAP_WIDTH} ${MAP_HEIGHT}`} role="img" aria-label={fill(copy.mapLabel, { mode: modeLabel, fleet })}>
        <rect width={MAP_WIDTH} height={MAP_HEIGHT} fill="#EEF3F0" />
        <path d="M0 340 C 160 300, 300 380, 640 330" fill="none" stroke="#D3E4EA" strokeWidth="22" />
        {ROADS.map((d) => <path key={d} d={d} fill="none" stroke="#FFFFFF" strokeWidth="7" />)}
        {plan.routes.map((route, index) => (
          <polyline
            key={index}
            fill="none"
            stroke={ROUTE_COLORS[index]}
            strokeWidth="3.5"
            strokeLinejoin="round"
            strokeLinecap="round"
            points={[DEPOT, ...route.stops.map((id) => STOPS[id]), DEPOT].map((point) => point.join(',')).join(' ')}
          />
        ))}
        {plan.routes.map((route, index) => route.stops.map((id, position) => {
          const [x, y] = STOPS[id];
          const isUrgent = id === URGENT_STOP_ID;
          const isLate = route.late.includes(id);
          return (
            <g key={`${index}-${id}`} transform={`translate(${x} ${y})`}>
              {isLate && <circle r="20" fill="none" stroke="#B91C1C" strokeWidth="3" />}
              {isUrgent
                ? <rect x="-12" y="-12" width="24" height="24" rx="2" transform="rotate(45)" fill="#111827" />
                : <circle r="13" fill={ROUTE_COLORS[index]} />}
              <text textAnchor="middle" dy="4.5" fontSize="13" fontWeight="700" fill="#FFFFFF">{position + 1}</text>
            </g>
          );
        }))}
        <g transform={`translate(${DEPOT[0]} ${DEPOT[1]})`}>
          <rect x="-14" y="-14" width="28" height="28" rx="3" fill="#0F172A" />
          <text textAnchor="middle" dy="4.5" fontSize="12" fontWeight="700" fill="#FFFFFF">{copy.depot}</text>
        </g>
      </svg>

      <ul className={styles.legend}>
        <li><span className={styles.legendRing} aria-hidden="true" />{copy.legendLate}</li>
        <li><span className={styles.legendDiamond} aria-hidden="true" />{copy.legendUrgent}</li>
      </ul>

      <dl className={styles.metrics}>
        <div><dt>{copy.distance}</dt><dd>{km} {copy.kmUnit}</dd></div>
        <div><dt>{copy.latest}</dt><dd>{formatClock(plan.latestReturnClock)}</dd></div>
        <div data-alert={plan.lateCount > 0 || undefined}>
          <dt>{copy.late}</dt>
          <dd>{plan.lateCount > 0 && <AlertTriangle aria-hidden="true" size={16} />}{plan.lateCount}</dd>
        </div>
      </dl>

      <table className={styles.simTable}>
        <thead>
          <tr>
            <th scope="col">{copy.vehicle}</th>
            <th scope="col">{copy.stops}</th>
            <th scope="col">{copy.kmUnit}</th>
            <th scope="col">{copy.back}</th>
            <th scope="col">{copy.lateShort}</th>
          </tr>
        </thead>
        <tbody>
          {plan.routes.map((route, index) => (
            <tr key={index}>
              <th scope="row"><span className={styles.swatch} style={{ background: ROUTE_COLORS[index] }} aria-hidden="true" />{copy.vehicle} {index + 1}</th>
              <td>{route.stops.length}</td>
              <td>{route.km.toLocaleString(intlLocale, { minimumFractionDigits: 1, maximumFractionDigits: 1 })}</td>
              <td>{formatClock(route.returnClock)}</td>
              <td>{route.late.length}</td>
            </tr>
          ))}
        </tbody>
      </table>

      <p className={styles.simNote}>{urgentSentence || copy.note}</p>
      <p className={styles.srOnly} role="status">{`${summary} ${urgentSentence}`}</p>
    </div>
  );
}
