import { toast } from 'sonner';
import type { Translator } from '@/i18n/translate';

export function reportDispatchPlanning(result: { outcome: string; plan_ids: string[] }, t: Translator): string | undefined {
  if (result.outcome === 'PLANNED' && result.plan_ids.length > 0) {
    toast.success(`${t('Đã tạo tuyến nháp chưa gán tài xế')}: ${result.plan_ids.length}`);
    return result.plan_ids[0];
  }
  if (result.outcome === 'NO_ORDERS') {
    toast.info(t('Chưa có đơn đủ điều kiện lập kế hoạch.'));
  } else if (result.outcome === 'IN_PROGRESS') {
    toast.info(t('Một lượt lập kế hoạch khác đang chạy.'));
  } else {
    toast.warning(t('Chưa tạo được tuyến từ các đơn đang chờ.'));
  }
}
