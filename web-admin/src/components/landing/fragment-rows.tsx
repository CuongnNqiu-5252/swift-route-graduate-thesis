import { AlertTriangle, Check } from 'lucide-react';
import type { FragmentRow } from './landing-copy';
import styles from './landing.module.css';

/** Label and value rows used inside product fragments. State is shown by icon as well as colour. */
export function FragmentRows({ rows }: { rows: FragmentRow[] }) {
  return (
    <dl className={styles.rows}>
      {rows.map((row) => (
        <div key={row.label} className={styles.row} data-tone={row.tone}>
          <dt>{row.label}</dt>
          <dd>
            {row.tone === 'ok' && <Check aria-hidden="true" size={14} />}
            {row.tone === 'warn' && <AlertTriangle aria-hidden="true" size={14} />}
            {row.value}
          </dd>
        </div>
      ))}
    </dl>
  );
}
