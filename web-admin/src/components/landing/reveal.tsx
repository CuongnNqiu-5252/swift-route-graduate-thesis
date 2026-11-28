'use client';

import { useEffect, useRef, type CSSProperties, type ReactNode } from 'react';
import styles from './landing.module.css';

/**
 * Rises into place once as the block enters the viewport.
 * Content is fully visible until the script runs, and stays visible under reduced motion
 * or when the block is already on screen at load.
 */
export function Reveal({ children, className, delay = 0 }: { children: ReactNode; className?: string; delay?: number }) {
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const element = ref.current;
    if (!element || window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;
    if (element.getBoundingClientRect().top < window.innerHeight * 0.9) return;

    element.dataset.reveal = 'hidden';
    const observer = new IntersectionObserver(([entry]) => {
      if (!entry.isIntersecting) return;
      element.dataset.reveal = 'shown';
      observer.disconnect();
    }, { rootMargin: '0px 0px -8% 0px' });
    observer.observe(element);
    return () => observer.disconnect();
  }, []);

  const style: CSSProperties | undefined = delay ? { transitionDelay: `${delay}ms` } : undefined;
  return <div ref={ref} className={`${styles.reveal} ${className ?? ''}`} style={style}>{children}</div>;
}
