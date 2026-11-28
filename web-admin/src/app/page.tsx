import type { Metadata } from 'next';
import { cookies } from 'next/headers';
import Link from 'next/link';
import { ArrowRight } from 'lucide-react';
import { formatLocale, localeCookie, resolveLocale } from '@/i18n/locale';
import { CapabilityExplorer } from '@/components/landing/capability-explorer';
import { HeroDispatchStage } from '@/components/landing/hero-dispatch-stage';
import { landingCopy } from '@/components/landing/landing-copy';
import { LandingFooter } from '@/components/landing/landing-footer';
import { LandingHeader } from '@/components/landing/landing-header';
import { OperationalFaq } from '@/components/landing/operational-faq';
import { OperationsShowcase } from '@/components/landing/operations-showcase';
import { Reveal } from '@/components/landing/reveal';
import { TrackingBand } from '@/components/landing/tracking-band';
import { WorkflowPinnedStage } from '@/components/landing/workflow-pinned-stage';
import { WorkspaceCta } from '@/components/landing/workspace-cta';
import styles from '@/components/landing/landing.module.css';

async function currentLocale() {
  return resolveLocale((await cookies()).get(localeCookie)?.value);
}

export async function generateMetadata(): Promise<Metadata> {
  const copy = landingCopy[await currentLocale()];
  return { title: copy.metaTitle, description: copy.metaDescription };
}

export default async function LandingPage() {
  const locale = await currentLocale();
  const copy = landingCopy[locale];

  return (
    <div className={styles.page} id="home">
      <LandingHeader copy={copy.nav} locale={locale} />

      <main>
        <section className={styles.hero} aria-labelledby="landing-title">
          <div className={styles.heroCopy}>
            <h1 id="landing-title">{copy.hero.title}</h1>
            <p>{copy.hero.lead}</p>
            <div className={styles.heroActions}>
              <Link className={styles.button} href="/register">
                {copy.hero.primary} <ArrowRight aria-hidden="true" size={18} />
              </Link>
              <a className={styles.textLink} href="#workflow">{copy.hero.secondary}</a>
            </div>
          </div>
          <div className={styles.stageWrap}>
            <div className={styles.stage}>
              <HeroDispatchStage copy={copy.sim} intlLocale={formatLocale(locale)} />
            </div>
          </div>
        </section>

        <section className={styles.workflow} id="workflow" aria-labelledby="workflow-title">
          <WorkflowPinnedStage copy={copy.workflow} />
        </section>

        <section className={styles.showcase} id="situations" aria-labelledby="showcase-title">
          <OperationsShowcase copy={copy.showcase} />
        </section>

        <section className={styles.capabilities} id="capabilities" aria-labelledby="capabilities-title">
          <Reveal>
            <CapabilityExplorer copy={copy.capabilities} />
          </Reveal>
        </section>

        <section className={styles.tracking} id="tracking" aria-labelledby="tracking-title">
          <Reveal>
            <TrackingBand copy={copy.tracking} />
          </Reveal>
        </section>

        <section className={styles.faq} id="faq" aria-labelledby="faq-title">
          <Reveal>
            <OperationalFaq copy={copy.faq} />
          </Reveal>
        </section>

        <WorkspaceCta copy={copy.start} loginLabel={copy.nav.login} />
      </main>

      <LandingFooter copy={copy.footer} nav={copy.nav} />
    </div>
  );
}
