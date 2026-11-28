import Link from 'next/link';
import { Route } from 'lucide-react';
import type { Locale } from '@/i18n/locale';
import type { LandingCopy } from './landing-copy';
import { LanguageToggle } from './language-toggle';
import styles from './landing.module.css';

export function LandingHeader({ copy, locale }: { copy: LandingCopy['nav']; locale: Locale }) {
  return (
    <header className={styles.header}>
      <div className={`${styles.frame} ${styles.headerInner}`}>
        <Link className={styles.brand} href="/">
          <Route aria-hidden="true" size={24} />
          SwiftRoute
        </Link>
        <nav aria-label={copy.label} className={styles.nav}>
          <a href="#workflow">{copy.workflow}</a>
          <a href="#capabilities">{copy.capabilities}</a>
          <a href="#tracking">{copy.tracking}</a>
          <a href="#faq">{copy.faq}</a>
        </nav>
        <div className={styles.headerActions}>
          <LanguageToggle locale={locale} label={copy.language} />
          <Link className={styles.plainLink} href="/login">{copy.login}</Link>
          <Link className={styles.button} href="/register">{copy.register}</Link>
        </div>
      </div>
    </header>
  );
}
