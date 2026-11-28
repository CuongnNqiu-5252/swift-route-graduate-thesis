import Link from 'next/link';
import { Check } from 'lucide-react';
import type { LandingCopy } from './landing-copy';
import styles from './landing.module.css';

export function WorkspaceCta({ copy, loginLabel }: { copy: LandingCopy['start']; loginLabel: string }) {
  return (
    <section className={styles.start} id="start" aria-labelledby="start-title">
      <div className={styles.startGrid}>
        <div className={styles.startText}>
          <h2 id="start-title" className={styles.startLine}>{copy.title}</h2>
          <p>{copy.lead}</p>
        </div>
        <div className={styles.dialog} data-ui role="group" aria-labelledby="start-dialog-title">
          <h3 id="start-dialog-title">{copy.dialogTitle}</h3>
          <ol className={styles.dialogSteps}>
            {copy.steps.map((step) => (
              <li key={step.label}>
                <Check className={styles.dialogCheck} aria-hidden="true" size={18} />
                <span>
                  <strong>{step.label}</strong>
                  <small>{step.detail}</small>
                </span>
              </li>
            ))}
          </ol>
          <Link className={styles.button} href="/register">{copy.action}</Link>
          <p className={styles.dialogLogin}>
            {copy.hasAccount} <Link href="/login">{loginLabel}</Link>
          </p>
        </div>
      </div>
    </section>
  );
}
