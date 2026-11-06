'use client';

import dynamic from 'next/dynamic';
import type { PickupLocationsMapProps } from './pickup-locations-map-inner';

const Inner = dynamic(() => import('./pickup-locations-map-inner'), {
  ssr: false,
  loading: () => <div className="flex h-80 items-center justify-center bg-slate-100 text-sm text-slate-500">Đang tải bản đồ kho…</div>,
});

export default function PickupLocationsMap(props: PickupLocationsMapProps) {
  return <Inner {...props} />;
}
