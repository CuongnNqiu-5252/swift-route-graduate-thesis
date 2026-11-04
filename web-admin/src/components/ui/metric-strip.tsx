import Link from 'next/link';

interface Metric { label: string; value: number; href: string }

export function MetricStrip({ metrics, loading }: { metrics: Metric[]; loading?: boolean }) {
  return <section aria-label="Tình hình vận hành" className="grid grid-cols-2 gap-px overflow-hidden rounded-xl border border-slate-200 bg-slate-200 lg:grid-cols-4">
    {metrics.map(metric => <Link key={metric.label} href={metric.href} className="bg-white p-5 transition-colors hover:bg-slate-50">
      <p className="text-sm text-slate-600">{metric.label}</p>
      <p className="mt-3 text-3xl font-semibold tabular-nums text-slate-950">{loading ? '—' : metric.value}</p>
    </Link>)}
  </section>;
}
