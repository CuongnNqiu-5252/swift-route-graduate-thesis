'use client';

import { useMemo, useState } from 'react';
import type { LandingCopy } from './landing-copy';
import styles from './landing.module.css';

function normalize(text: string): string {
  return text.normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/đ/g, 'd').toLowerCase();
}

/** A filterable list of what the product does. Typing narrows it, arrow keys move the selection. */
export function CapabilityExplorer({ copy }: { copy: LandingCopy['capabilities'] }) {
  const [query, setQuery] = useState('');
  const [selected, setSelected] = useState(0);

  const filtered = useMemo(() => {
    const needle = normalize(query.trim());
    return copy.items.filter((item) => normalize(`${item.name} ${item.detail} ${item.keywords}`).includes(needle));
  }, [copy.items, query]);
  const active = Math.min(selected, filtered.length - 1);

  return (
    <div className={styles.capGrid}>
      <h2 id="capabilities-title" className={styles.sectionTitle}>{copy.title}</h2>
      <label className={styles.capSearch}>
        <span className={styles.srOnly}>{copy.inputLabel}</span>
        <input
          type="search"
          value={query}
          placeholder={copy.placeholder}
          aria-controls="capability-list"
          onChange={(event) => { setQuery(event.target.value); setSelected(0); }}
          onKeyDown={(event) => {
            if (event.key === 'ArrowDown') { event.preventDefault(); setSelected(Math.min(active + 1, filtered.length - 1)); }
            if (event.key === 'ArrowUp') { event.preventDefault(); setSelected(Math.max(active - 1, 0)); }
          }}
        />
      </label>
      {filtered.length === 0 && <p className={styles.capEmpty} role="status">{copy.empty}</p>}
      <ul id="capability-list" className={styles.capList} aria-label={copy.listLabel}>
        {filtered.map((item, index) => (
          <li key={item.name}>
            <button
              type="button"
              className={styles.capItem}
              data-active={active === index || undefined}
              aria-current={active === index ? 'true' : undefined}
              onClick={() => setSelected(index)}
              onMouseEnter={() => setSelected(index)}
            >
              <span className={styles.capName}>{item.name}</span>
              {active === index && <span className={styles.capDetail}>{item.detail}</span>}
            </button>
          </li>
        ))}
      </ul>
    </div>
  );
}
