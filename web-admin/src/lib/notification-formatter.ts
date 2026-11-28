/**
 * Notification formatting & humanization utility.
 * Ensures no technical event codes, schema identifiers, or raw JSON enums
 * leak onto the user interface.
 */

export interface FormattableNotification {
  id?: string;
  template_code?: string;
  category?: 'INCIDENT' | 'DELIVERY' | 'DISPATCH' | 'COD' | 'EARNINGS' | 'WORKSPACE' | 'SYSTEM' | string;
  severity?: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL' | string;
  title?: string;
  body?: string;
  action_url?: string | null;
  incident_id?: string | null;
  order_id?: string | null;
  dispatch_plan_id?: string | null;
  created_at?: string;
}

export interface FormattedNotificationPresentation {
  title: string;
  body: string;
  category: FormattableNotification['category'];
  severity: FormattableNotification['severity'];
  actionUrl: string;
  badgeLabel?: string;
  badgeClass?: string;
  ctaLabel?: string;
}

const TECHNICAL_INCIDENT_MAP: Record<string, string> = {
  CUSTOMER_ABSENT: 'Khách vắng mặt',
  RECIPIENT_UNAVAILABLE: 'Khách vắng mặt',
  CUSTOMER_UNREACHABLE: 'Không liên lạc được người nhận',
  CUSTOMER_REFUSED: 'Khách từ chối nhận hàng',
  REFUSED: 'Khách từ chối nhận hàng',
  REFUSED_BY_RECIPIENT: 'Khách từ chối nhận hàng',
  WRONG_ADDRESS: 'Sai địa chỉ giao hàng',
  DAMAGED: 'Kiện hàng hư hỏng',
  PACKAGE_DAMAGED: 'Kiện hàng hư hỏng',
  GOODS_DAMAGED: 'Kiện hàng hư hỏng',
  VEHICLE_BREAKDOWN: 'Hỏng xe trên tuyến',
  WEATHER_DELAY: 'Thời tiết xấu / ngập đường',
  TRAFFIC_CONGESTION: 'Tắc đường / kẹt xe nghiêm trọng',
  ACCIDENT: 'Tai nạn giao thông',
  MANUAL_REVIEW: 'Chờ điều phối viên xử lý',
  REDELIVERY: 'Lên lịch giao lại',
  RETURN_TO_ORIGIN: 'Hoàn hàng về kho',
  SAI_LECH: 'sai lệch hàng hóa',
  MISSING_PACKAGE: 'thiếu kiện hàng',
  DAMAGED_PACKAGE: 'hàng bị hư hại',
  WRONG_ITEM: 'sai lệch sản phẩm',
};

export function cleanNotificationText(raw: string | undefined | null): string {
  if (!raw) return '';
  let cleaned = raw;

  // Replace raw tenant status strings anywhere in body
  cleaned = cleaned.replace(
    /(?:Trạng thái:\s*)?tenant\.verification_submitted(\.v\d+)?\b/gi,
    'Hồ sơ xác minh doanh nghiệp đã được tiếp nhận và đang chờ ban quản trị phê duyệt. Hệ thống sẽ thông báo ngay khi có kết quả.'
  );
  cleaned = cleaned.replace(
    /(?:Trạng thái:\s*)?tenant\.verification_under_review(\.v\d+)?\b/gi,
    'Đội ngũ quản trị đang thẩm định thông tin và hồ sơ đăng ký của doanh nghiệp.'
  );
  cleaned = cleaned.replace(
    /(?:Trạng thái:\s*)?tenant\.created(\.v\d+)?\b/gi,
    'Không gian làm việc cho doanh nghiệp đã sẵn sàng. Hãy hoàn thiện hồ sơ xác minh để bắt đầu vận hành.'
  );
  cleaned = cleaned.replace(
    /(?:Trạng thái:\s*)?tenant\.approved(\.v\d+)?\b/gi,
    'Hồ sơ doanh nghiệp đã được phê duyệt thành công. Bạn hiện có thể tạo đơn hàng và bắt đầu điều phối giao vận.'
  );
  cleaned = cleaned.replace(
    /(?:Trạng thái:\s*)?tenant\.verification_changes_requested(\.v\d+)?(\s*-\s*)?(?:Lý do:\s*)?/gi,
    'Yêu cầu bổ sung thông tin hồ sơ: '
  );
  cleaned = cleaned.replace(
    /(?:Trạng thái:\s*)?tenant\.rejected(\.v\d+)?(\s*-\s*)?(?:Lý do:\s*)?/gi,
    'Hồ sơ doanh nghiệp không được phê duyệt: '
  );
  cleaned = cleaned.replace(
    /(?:Trạng thái:\s*)?tenant\.suspended(\.v\d+)?(\s*-\s*)?(?:Lý do:\s*)?/gi,
    'Tài khoản doanh nghiệp tạm ngưng hoạt động: '
  );
  cleaned = cleaned.replace(
    /(?:Trạng thái:\s*)?tenant\.[a-z0-9_.]+(\.v\d+)?(\s*-\s*)?/gi,
    'Cập nhật thông tin doanh nghiệp: '
  );

  // Replace raw operation event string
  cleaned = cleaned.replace(
    /Sự kiện vận hành\s*\[[a-z0-9_.]+\]\s*vừa được ghi nhận trên hệ thống\./gi,
    'Có cập nhật mới về tiến độ vận hành trên hệ thống.'
  );

  // Replace English incident/action codes
  for (const [key, replacement] of Object.entries(TECHNICAL_INCIDENT_MAP)) {
    // Replace bracketed code [CODE] or standalone code CODE
    const bracketRegex = new RegExp(`\\[${key}\\]`, 'g');
    cleaned = cleaned.replace(bracketRegex, replacement);
    const wordRegex = new RegExp(`\\b${key}\\b`, 'g');
    cleaned = cleaned.replace(wordRegex, replacement);
  }

  return cleaned.trim();
}

export function cleanNotificationTitle(title: string | undefined | null, templateCode?: string): string {
  if (!title) return 'Thông báo vận hành';
  let cleaned = title.trim();

  if (cleaned.startsWith('Thông báo vận hành: ')) {
    cleaned = 'Thông báo vận hành';
  }
  if (
    cleaned === 'Cập nhật workspace SwiftRoute' ||
    cleaned.includes('workspace SwiftRoute') ||
    cleaned.toLowerCase().includes('cập nhật workspace')
  ) {
    if (templateCode?.includes('verification_submitted')) {
      return 'Hồ sơ xác minh doanh nghiệp đã được gửi';
    }
    if (templateCode?.includes('verification_under_review')) {
      return 'Hồ sơ xác minh đang được thẩm định';
    }
    if (templateCode?.includes('verification_changes_requested')) {
      return 'Yêu cầu bổ sung hồ sơ xác minh doanh nghiệp';
    }
    if (templateCode?.includes('approved')) {
      return 'Hồ sơ doanh nghiệp đã được phê duyệt';
    }
    if (templateCode?.includes('rejected')) {
      return 'Hồ sơ doanh nghiệp không được phê duyệt';
    }
    if (templateCode?.includes('suspended')) {
      return 'Doanh nghiệp đã bị tạm ngưng hoạt động';
    }
    if (templateCode?.includes('created')) {
      return 'Khởi tạo doanh nghiệp thành công';
    }
    return 'Thông báo cập nhật doanh nghiệp';
  }

  return cleaned;
}

export function formatNotification(item: FormattableNotification): FormattedNotificationPresentation {
  const templateCode = item.template_code || '';
  let title = cleanNotificationTitle(item.title, templateCode);
  let body = cleanNotificationText(item.body);
  let category = item.category || 'DELIVERY';
  let severity = item.severity || 'LOW';
  let actionUrl = item.action_url || '/orders';
  let badgeLabel: string | undefined;
  let badgeClass: string | undefined;
  let ctaLabel: string | undefined;

  switch (templateCode) {
    case 'tenant.verification_submitted.v1':
      title = 'Hồ sơ xác minh doanh nghiệp đã được gửi';
      if (!body || body.includes('tenant.verification_submitted.v1')) {
        body =
          'Hồ sơ xác minh doanh nghiệp đã được tiếp nhận và đang chờ ban quản trị phê duyệt. Hệ thống sẽ thông báo ngay khi có kết quả.';
      }
      category = 'WORKSPACE';
      severity = 'LOW';
      actionUrl = '/onboarding';
      badgeLabel = 'Đang xét duyệt';
      badgeClass = 'bg-blue-50 text-blue-800 border-blue-200';
      ctaLabel = 'Xem tiến độ thiết lập';
      break;

    case 'tenant.verification_under_review.v1':
      title = 'Hồ sơ xác minh đang được thẩm định';
      if (!body) {
        body = 'Đội ngũ quản trị đang thẩm định thông tin và hồ sơ đăng ký của doanh nghiệp.';
      }
      category = 'WORKSPACE';
      severity = 'LOW';
      actionUrl = '/onboarding';
      badgeLabel = 'Đang thẩm định';
      badgeClass = 'bg-blue-50 text-blue-800 border-blue-200';
      ctaLabel = 'Xem tiến độ';
      break;

    case 'tenant.verification_changes_requested.v1':
      title = 'Yêu cầu bổ sung hồ sơ xác minh doanh nghiệp';
      category = 'WORKSPACE';
      severity = 'HIGH';
      actionUrl = '/onboarding';
      badgeLabel = 'Cần bổ sung hồ sơ';
      badgeClass = 'bg-orange-50 text-orange-800 border-orange-200';
      ctaLabel = 'Cập nhật hồ sơ';
      break;

    case 'tenant.approved':
    case 'tenant.approved.v1':
      title = 'Hồ sơ doanh nghiệp đã được phê duyệt';
      if (!body || body.includes('tenant.approved')) {
        body =
          'Hồ sơ doanh nghiệp đã được phê duyệt thành công. Bạn hiện có thể tạo đơn hàng và bắt đầu điều phối giao vận.';
      }
      category = 'WORKSPACE';
      severity = 'LOW';
      actionUrl = '/orders';
      badgeLabel = 'Đã phê duyệt';
      badgeClass = 'bg-emerald-50 text-emerald-800 border-emerald-200';
      ctaLabel = 'Bắt đầu vận hành';
      break;

    case 'tenant.rejected.v1':
      title = 'Hồ sơ doanh nghiệp không được phê duyệt';
      category = 'WORKSPACE';
      severity = 'HIGH';
      actionUrl = '/onboarding';
      badgeLabel = 'Từ chối duyệt';
      badgeClass = 'bg-red-50 text-red-800 border-red-200';
      ctaLabel = 'Xem lý do';
      break;

    case 'tenant.suspended.v1':
      title = 'Doanh nghiệp đã bị tạm ngưng hoạt động';
      category = 'WORKSPACE';
      severity = 'CRITICAL';
      actionUrl = '/onboarding';
      badgeLabel = 'Tạm ngưng';
      badgeClass = 'bg-red-100 text-red-800 border-red-200';
      ctaLabel = 'Xem chi tiết';
      break;

    case 'tenant.created':
    case 'tenant.created.v1':
      title = 'Khởi tạo doanh nghiệp thành công';
      if (!body || body.includes('tenant.created')) {
        body =
          'Không gian làm việc cho doanh nghiệp đã sẵn sàng. Hãy hoàn thiện hồ sơ xác minh để bắt đầu vận hành.';
      }
      category = 'WORKSPACE';
      severity = 'LOW';
      actionUrl = '/onboarding';
      badgeLabel = 'Doanh nghiệp';
      badgeClass = 'bg-slate-100 text-slate-800 border-slate-200';
      ctaLabel = 'Hoàn tất hồ sơ';
      break;

    case 'plan.completed':
      title = 'Lộ trình giao hàng đã hoàn tất';
      if (!body || body.includes('plan.completed')) {
        body = 'Tài xế đã hoàn thành tất cả các điểm dừng trong lộ trình giao hàng.';
      }
      category = 'DISPATCH';
      severity = 'LOW';
      actionUrl = '/dispatch';
      badgeLabel = 'Hoàn tất lộ trình';
      badgeClass = 'bg-emerald-50 text-emerald-800 border-emerald-200';
      ctaLabel = 'Xem điều phối';
      break;

    case 'assignment.timeout':
      title = 'Cảnh báo: Tài xế chưa nhận chuyến đúng hạn';
      if (!body) {
        body = 'Tài xế không phản hồi nhận lộ trình trước thời hạn. Hệ thống đang tự động tìm tài xế thay thế.';
      }
      category = 'DISPATCH';
      severity = 'MEDIUM';
      actionUrl = '/dispatch';
      badgeLabel = 'Hết hạn nhận';
      badgeClass = 'bg-amber-50 text-amber-800 border-amber-200';
      ctaLabel = 'Xem điều phối';
      break;

    case 'assignment.escalated':
      title = 'Cần can thiệp điều phối thủ công';
      if (!body) {
        body = 'Không tìm được tài xế thay thế sau khi hết hạn nhận lộ trình. Vui lòng điều phối thủ công.';
      }
      category = 'DISPATCH';
      severity = 'HIGH';
      actionUrl = '/dispatch';
      badgeLabel = 'Cần điều phối';
      badgeClass = 'bg-orange-50 text-orange-800 border-orange-200';
      ctaLabel = 'Điều phối ngay';
      break;

    case 'delivery.delay_detected':
      category = 'DISPATCH';
      badgeLabel = 'Nguy cơ trễ';
      badgeClass = 'bg-amber-50 text-amber-800 border-amber-200';
      actionUrl = '/dispatch';
      ctaLabel = 'Xem lộ trình';
      break;

    default:
      break;
  }

  return {
    title,
    body,
    category,
    severity,
    actionUrl,
    badgeLabel,
    badgeClass,
    ctaLabel,
  };
}
