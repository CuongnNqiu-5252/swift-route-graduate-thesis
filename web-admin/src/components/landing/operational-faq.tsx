'use client';

import { useState } from 'react';
import type { LandingCopy } from './landing-copy';
import styles from './landing.module.css';

/** Questions on one side, the selected answer set large on the other. */
export function OperationalFaq({ copy }: { copy: LandingCopy['faq'] }) {
  const [selected, setSelected] = useState(0);

  return (
    <div className={styles.faqGrid}>
      <div>
        <h2 id="faq-title" className={styles.sectionTitle}>{copy.title}</h2>
        <ul className={styles.faqList} aria-label={copy.listLabel}>
          {copy.items.map((item, index) => (
            <li key={item.q}>
              <button
                type="button"
                className={styles.faqQuestion}
                data-active={selected === index || undefined}
                aria-expanded={selected === index}
                onClick={() => setSelected(index)}
                onMouseEnter={() => setSelected(index)}
                onFocus={() => setSelected(index)}
              >
                <span className={styles.faqDot} aria-hidden="true" />
                {item.q}
              </button>
              {selected === index && <p className={styles.faqInline}>{item.a}</p>}
            </li>
          ))}
        </ul>
      </div>
      <p key={selected} className={styles.faqPane} aria-live="polite">{copy.items[selected].a}</p>
    </div>
  );
}
