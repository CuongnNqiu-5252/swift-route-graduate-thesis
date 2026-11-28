'use client';

import { useEffect, useRef, useState } from 'react';
import { AlertTriangle, Check } from 'lucide-react';
import type { LandingCopy } from './landing-copy';
import { FragmentRows } from './fragment-rows';
import styles from './landing.module.css';

function ImportReview({ copy }: { copy: LandingCopy['workflow']['import'] }) {
  return (
    <div className={styles.fragment} data-ui>
      <div className={styles.fragmentHead}>
        <span className={styles.fragmentTitle}>{copy.title}</span>
        <span className={styles.fragmentMeta}>{copy.file}</span>
      </div>
      <div className={styles.fragmentBody}>
        <table className={styles.fragTable}>
          <thead><tr>{copy.columns.map((column) => <th key={column} scope="col">{column}</th>)}</tr></thead>
          <tbody>
            {copy.rows.map((row) => {
              const valid = row[4] === copy.ok;
              return (
                <tr key={row[0]} data-tone={valid ? 'ok' : 'warn'}>
                  {row.slice(0, 4).map((cell, index) => <td key={index}>{cell}</td>)}
                  <td>
                    {valid ? <Check aria-hidden="true" size={14} /> : <AlertTriangle aria-hidden="true" size={14} />}
                    {row[4]}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
        <p className={styles.fragSummary}>{copy.summary}</p>
        <span className={styles.fragButton}>{copy.action}</span>
      </div>
    </div>
  );
}

function PlanFragment({ copy }: { copy: LandingCopy['workflow']['plan'] }) {
  return (
    <div className={styles.fragment} data-ui>
      <div className={styles.fragmentHead}>
        <span className={styles.fragmentTitle}>{copy.title}</span>
        <span className={styles.fragmentMeta}>{copy.driverLabel}: {copy.driver}</span>
      </div>
      <div className={styles.fragmentBody}>
        <table className={styles.fragTable}>
          <thead><tr>{copy.columns.map((column) => <th key={column} scope="col">{column}</th>)}</tr></thead>
          <tbody>
            {copy.rows.map((row) => (
              <tr key={row[0]}>{row.map((cell, index) => <td key={index}>{cell}</td>)}</tr>
            ))}
          </tbody>
        </table>
        <p className={styles.fragSummary}>{copy.total}</p>
        <span className={styles.fragButton}>{copy.action}</span>
      </div>
    </div>
  );
}

function LiveFragment({ copy }: { copy: LandingCopy['workflow']['live'] }) {
  return (
    <div className={styles.fragment} data-ui>
      <div className={styles.fragmentHead}>
        <span className={styles.fragmentTitle}>{copy.title}</span>
        <span className={styles.fragmentMeta}>{copy.progress}</span>
      </div>
      <div className={styles.fragmentBody}>
        <FragmentRows rows={copy.rows} />
        <p className={styles.fragSummary}>{copy.position}</p>
      </div>
    </div>
  );
}

function CodFragment({ copy }: { copy: LandingCopy['workflow']['cod'] }) {
  return (
    <div className={styles.fragment} data-ui>
      <div className={styles.fragmentHead}><span className={styles.fragmentTitle}>{copy.title}</span></div>
      <div className={styles.fragmentBody}>
        <FragmentRows rows={copy.rows} />
        <div className={styles.fragSeparate}>
          <span>{copy.earnings}</span>
          <strong>{copy.earningsValue}</strong>
        </div>
        <span className={styles.fragButton}>{copy.action}</span>
      </div>
    </div>
  );
}

/** Four statements scroll past while one pinned fragment changes to the matching state. */
export function WorkflowPinnedStage({ copy }: { copy: LandingCopy['workflow'] }) {
  const [active, setActive] = useState(0);
  const refs = useRef<(HTMLLIElement | null)[]>([]);

  useEffect(() => {
    const observer = new IntersectionObserver((entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) setActive(Number((entry.target as HTMLElement).dataset.index));
      });
    }, { rootMargin: '-45% 0px -45% 0px' });
    refs.current.forEach((element) => element && observer.observe(element));
    return () => observer.disconnect();
  }, []);

  const panels = [
    <ImportReview key="orders" copy={copy.import} />,
    <PlanFragment key="dispatch" copy={copy.plan} />,
    <LiveFragment key="tracking" copy={copy.live} />,
    <CodFragment key="cod" copy={copy.cod} />,
  ];

  return (
    <div className={styles.workflowGrid}>
      <div className={styles.workflowText}>
        <h2 id="workflow-title" className={styles.sectionTitle}>{copy.title}</h2>
        <ol className={styles.statements}>
          {copy.steps.map((step, index) => (
            <li
              key={step.id}
              ref={(element) => { refs.current[index] = element; }}
              data-index={index}
              data-active={active === index || undefined}
              className={styles.statement}
            >
              <h3>{step.title}</h3>
              <p>{step.detail}</p>
              <div className={styles.inlinePanel}>{panels[index]}</div>
            </li>
          ))}
        </ol>
      </div>
      <div className={styles.stagePin}>
        <div className={styles.stageStack}>
          {panels.map((panel, index) => (
            <div key={copy.steps[index].id} className={styles.stagePanel} data-active={active === index || undefined} aria-hidden={active !== index}>
              {panel}
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
