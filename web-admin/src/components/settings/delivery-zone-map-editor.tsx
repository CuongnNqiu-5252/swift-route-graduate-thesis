'use client';

import dynamic from 'next/dynamic';
import { useLocale } from '@/i18n/provider';
import type { DeliveryZoneMapEditorProps, MapInteractionMode } from './delivery-zone-map-editor-inner';

export type { DeliveryZoneMapEditorProps, MapInteractionMode };

const DeliveryZoneMapEditorInner = dynamic(() => import('./delivery-zone-map-editor-inner'), {
  ssr: false,
  loading: function MapLoading() {
    const { t } = useLocale();
    return (
      <div className="flex h-[430px] items-center justify-center rounded-lg border border-slate-200 bg-slate-50 text-sm text-slate-500">
        {t("Đang tải bản đồ vùng giao hàng…")}
      </div>
    );
  },
});

export default function DeliveryZoneMapEditor(props: DeliveryZoneMapEditorProps) {
  return <DeliveryZoneMapEditorInner {...props} />;
}
