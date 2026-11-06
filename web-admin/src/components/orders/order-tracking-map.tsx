'use client';
import dynamic from 'next/dynamic';
export default dynamic(() => import('./order-tracking-map-inner'), { ssr: false, loading: () => <div className="h-80 bg-slate-100" /> });
