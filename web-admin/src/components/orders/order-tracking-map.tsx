'use client';
import dynamic from 'next/dynamic';

export interface OrderTrackingMapProps {
  pickup: [number, number];
  delivery: [number, number];
  driver?: [number, number];
  driverName?: string;
  orderCode?: string;
}

export default dynamic<OrderTrackingMapProps>(() => import('./order-tracking-map-inner'), { ssr: false, loading: () => <div className="h-80 bg-slate-100" /> });
