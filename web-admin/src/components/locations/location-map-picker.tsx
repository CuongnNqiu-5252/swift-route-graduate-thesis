'use client';

import dynamic from 'next/dynamic';
import { useLocale } from '@/i18n/provider';
import type { LocationMapPickerProps } from './location-map-picker-inner';

const Inner = dynamic(() => import('./location-map-picker-inner'), {
  ssr: false,
  loading: function MapLoading() {
    const { t } = useLocale();
    return <div className="flex h-72 items-center justify-center bg-slate-100 text-sm text-slate-500">{t("Đang tải bản đồ…")}</div>;
  },
});

export default function LocationMapPicker(props: LocationMapPickerProps) {
  return <Inner {...props} />;
}
