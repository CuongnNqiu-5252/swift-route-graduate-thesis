'use client';

import { useState, type FormEvent } from 'react';
import { useRouter } from 'next/navigation';
import type { LandingCopy } from './landing-copy';
import styles from './landing.module.css';

/** Real lookup into /track/[token], plus a sample card the visitor can step through. */
export function TrackingBand({ copy }: { copy: LandingCopy['tracking'] }) {
  const router = useRouter();
  const [code, setCode] = useState('');
  const [step, setStep] = useState(2);

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const token = code.trim();
    if (token) router.push(`/track/${encodeURIComponent(token)}`);
  }

  return (
    <div className={styles.trackGrid}>
      <div className={styles.trackText}>
        <h2 id="tracking-title" className={styles.sectionTitle}>{copy.title}</h2>
        <p>{copy.lead}</p>
        <form className={styles.trackForm} onSubmit={submit}>
          <label>
            <span className={styles.srOnly}>{copy.inputLabel}</span>
            <input type="text" value={code} onChange={(event) => setCode(event.target.value)} placeholder={copy.placeholder} autoComplete="off" />
          </label>
          <button type="submit" className={styles.button}>{copy.action}</button>
        </form>
      </div>

      <div className={styles.trackSample} data-ui>
        <div className={styles.fragmentHead}>
          <span className={styles.fragmentTitle}>{copy.sampleTitle}</span>
          <span className={styles.fragmentMeta}>{copy.sample.parcel}</span>
        </div>
        <div className={styles.fragmentBody}>
          <ol className={styles.trackSteps} aria-label={copy.stepsLabel}>
            {copy.steps.map((label, index) => (
              <li key={label}>
                <button type="button" aria-pressed={step === index} data-done={index <= step || undefined} onClick={() => setStep(index)}>
                  <span className={styles.trackDot} aria-hidden="true" />
                  {label}
                </button>
              </li>
            ))}
          </ol>
          <p className={styles.trackDetail} role="status">{copy.sample.details[step]}</p>
          <div className={styles.fragSeparate}>
            <span>{copy.sample.cod}</span>
            <strong>{copy.sample.codValue}</strong>
          </div>
        </div>
      </div>
    </div>
  );
}
