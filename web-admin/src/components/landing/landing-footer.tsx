import Link from 'next/link';
import type { LandingCopy } from './landing-copy';
import styles from './landing.module.css';

export function LandingFooter({ copy, nav }: { copy: LandingCopy['footer']; nav: LandingCopy['nav'] }) {
  return (
    <footer className={styles.footer}>
      <div className={styles.tiles}>
        <div className={`${styles.tile} ${styles.tileStatement}`}>
          <p>{copy.statement}</p>
          <small>{copy.legal}</small>
        </div>
        <nav aria-label={copy.label} className={`${styles.tile} ${styles.tileNav}`}>
          <a href="#workflow">{nav.workflow}</a>
          <a href="#capabilities">{nav.capabilities}</a>
          <a href="#tracking">{nav.tracking}</a>
          <a href="#faq">{nav.faq}</a>
        </nav>
        <div className={`${styles.tile} ${styles.tileAccess}`}>
          <span>{copy.access}</span>
          <Link href="/login">{nav.login}</Link>
          <Link href="/register">{nav.register}</Link>
        </div>
        <div className={`${styles.tile} ${styles.tileWordmark}`} aria-hidden="true">SwiftRoute</div>
        <Link className={`${styles.tile} ${styles.tileInvite}`} href="/register">
          <span>{copy.invite}</span>
          <svg viewBox="0 0 48 48" width="44" height="44" aria-hidden="true" focusable="false">
            <path d="M8 40 40 8M18 8h22v22" fill="none" stroke="currentColor" strokeWidth="4" strokeLinecap="square" />
          </svg>
        </Link>
      </div>
    </footer>
  );
}
