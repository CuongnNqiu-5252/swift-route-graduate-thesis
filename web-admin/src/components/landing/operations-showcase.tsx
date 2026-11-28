'use client';

import { useRef, useState } from 'react';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import type { LandingCopy } from './landing-copy';
import { FragmentRows } from './fragment-rows';
import styles from './landing.module.css';

function ZoneMap() {
  return (
    <svg className={styles.zoneMap} viewBox="0 0 320 150" aria-hidden="true" focusable="false">
      <rect width="320" height="150" fill="#EEF3F0" />
      <polygon points="40,30 210,18 280,70 230,128 70,120" fill="#17766C" fillOpacity="0.14" stroke="#17766C" strokeWidth="2.5" />
      {[[90, 60], [150, 50], [200, 80], [120, 95], [175, 105]].map(([x, y]) => <circle key={`${x}-${y}`} cx={x} cy={y} r="5" fill="#2563EB" />)}
      {[[285, 112], [20, 135], [300, 28]].map(([x, y]) => <circle key={`${x}-${y}`} cx={x} cy={y} r="5" fill="#B91C1C" />)}
    </svg>
  );
}

/** Sideways row of product fragments. The panel at the left edge is lit; the rest wait. */
export function OperationsShowcase({ copy }: { copy: LandingCopy['showcase'] }) {
  const rowRef = useRef<HTMLDivElement>(null);
  const [active, setActive] = useState(0);

  function updateActive() {
    const row = rowRef.current;
    if (!row) return;
    if (row.scrollLeft + row.clientWidth >= row.scrollWidth - 4) {
      setActive(row.children.length - 1);
      return;
    }
    let best = 0;
    let bestDistance = Number.POSITIVE_INFINITY;
    Array.from(row.children).forEach((child, index) => {
      const distance = Math.abs((child as HTMLElement).offsetLeft - row.scrollLeft);
      if (distance < bestDistance) {
        bestDistance = distance;
        best = index;
      }
    });
    setActive(best);
  }

  function go(index: number) {
    const row = rowRef.current;
    const target = row?.children[Math.max(0, Math.min(index, copy.panels.length - 1))] as HTMLElement | undefined;
    if (!row || !target) return;
    const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    row.scrollTo({ left: target.offsetLeft, behavior: reduced ? 'auto' : 'smooth' });
  }

  const current = copy.panels[active];
  return (
    <div className={styles.showcaseGrid}>
      <div className={styles.showcaseText}>
        <h2 id="showcase-title" className={styles.sectionTitle}>{copy.title}</h2>
        <div className={styles.showcaseActive} aria-live="polite">
          <h3>{current.name}</h3>
          <p>{current.summary}</p>
        </div>
        <div className={styles.showcaseControls}>
          <button type="button" aria-label={copy.previous} disabled={active === 0} onClick={() => go(active - 1)}><ChevronLeft aria-hidden="true" size={22} /></button>
          <button type="button" aria-label={copy.next} disabled={active === copy.panels.length - 1} onClick={() => go(active + 1)}><ChevronRight aria-hidden="true" size={22} /></button>
        </div>
      </div>
      <div
        ref={rowRef}
        className={styles.showcaseRow}
        role="region"
        aria-label={copy.region}
        tabIndex={0}
        onScroll={updateActive}
        onKeyDown={(event) => {
          if (event.key === 'ArrowRight') { event.preventDefault(); go(active + 1); }
          if (event.key === 'ArrowLeft') { event.preventDefault(); go(active - 1); }
        }}
      >
        {copy.panels.map((panel, index) => (
          <article key={panel.name} className={styles.panel} data-ui data-active={active === index || undefined} aria-label={panel.name}>
            <div className={styles.fragmentHead}><span className={styles.fragmentTitle}>{panel.name}</span></div>
            <div className={styles.fragmentBody}>
              {index === 0 && <ZoneMap />}
              <FragmentRows rows={panel.rows} />
              <p className={styles.fragSummary}>{panel.note}</p>
            </div>
          </article>
        ))}
      </div>
    </div>
  );
}
