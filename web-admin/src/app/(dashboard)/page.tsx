'use client';

import Link from 'next/link';
import { useState } from 'react';
import { toast } from 'sonner';
import { useOperationsQueue } from '@/application/hooks/use-operations-queue';
import { useDrivers } from '@/application/hooks/use-drivers';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Modal } from '@/components/ui/modal';
import { OrderStatusBadge } from '@/components/orders/order-status-badge';
import { formatVNDate } from '@/lib/utils';
import {
  Package,
  Route,
  Truck,
  CheckCircle2,
  Clock,
  ArrowRight,
  TrendingUp,
  AlertTriangle,
} from 'lucide-react';


export default function DashboardOverviewPage() {
  const { queue, isLoading: isLoadingOrders, error, reassignOrder } = useOperationsQueue();
  const { drivers } = useDrivers();
  const [selectedOrderId, setSelectedOrderId] = useState<string | null>(null);
  const [isReassigning, setIsReassigning] = useState(false);

  const paidOrders = queue.ready_for_dispatch;
  const inTransitOrders = queue.active.filter((order) => order.status === 'IN_TRANSIT');
  const interventionOrders = queue.intervention;
  const reassignableOrders = queue.active.filter((order) => order.status === 'PLANNED');
  const availableDrivers = drivers.filter((d) => d.status === 'AVAILABLE' || !d.status);

  const activeOrders = queue.active;
  const recentOrders = [...paidOrders, ...activeOrders, ...interventionOrders].slice(0, 6);
  const operationalOrderCount = paidOrders.length + activeOrders.length + interventionOrders.length;

  const handleReassign = async (driverId: string) => {
    if (!selectedOrderId) return;
    setIsReassigning(true);
    try {
      await reassignOrder(selectedOrderId, driverId);
      toast.success('Đã điều phối lại tài xế.');
      setSelectedOrderId(null);
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Không thể điều phối lại tài xế.');
    } finally {
      setIsReassigning(false);
    }
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-gray-900">
            Tổng quan Vận hành SwiftRoute
          </h1>
          <p className="text-sm text-gray-500">
            Hệ thống giám sát thời gian thực & trung tâm điều phối chặng cuối (Last-mile Delivery)
          </p>
        </div>
        <div className="flex items-center gap-3">
          <Link href="/dispatch">
            <Button className="bg-blue-600 hover:bg-blue-700 font-semibold shadow-sm">
              <Route size={16} /> Bắt đầu Điều phối Ngay
            </Button>
          </Link>
        </div>
      </div>

      {/* KPI Cards */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <Card className="shadow-sm">
          <CardContent className="flex items-center justify-between p-5">
            <div>
              <p className="text-xs font-medium uppercase tracking-wider text-gray-500">
                Đơn trong luồng vận hành
              </p>
              <p className="mt-1 text-2xl font-bold text-gray-900">{operationalOrderCount}</p>
              <p className="mt-1 flex items-center text-xs text-gray-500">
                <TrendingUp size={12} className="mr-1" /> Theo tenant hiện tại
              </p>
            </div>
            <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-blue-50 text-blue-600">
              <Package size={24} />
            </div>
          </CardContent>
        </Card>

        <Card className="shadow-sm">
          <CardContent className="flex items-center justify-between p-5">
            <div>
              <p className="text-xs font-medium uppercase tracking-wider text-gray-500">
                Chờ gom & điều phối
              </p>
              <p className="mt-1 text-2xl font-bold text-amber-600">{paidOrders.length}</p>
              <p className="mt-1 text-xs text-gray-500">Sẵn sàng phân công tài xế</p>
            </div>
            <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-amber-50 text-amber-600">
              <Clock size={24} />
            </div>
          </CardContent>
        </Card>

        <Card className="shadow-sm">
          <CardContent className="flex items-center justify-between p-5">
            <div>
              <p className="text-xs font-medium uppercase tracking-wider text-gray-500">
                Đang trên đường giao
              </p>
              <p className="mt-1 text-2xl font-bold text-indigo-600">{inTransitOrders.length}</p>
              <p className="mt-1 text-xs text-gray-500">Theo dõi định vị GPS</p>
            </div>
            <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-indigo-50 text-indigo-600">
              <Truck size={24} />
            </div>
          </CardContent>
        </Card>

        <Card className="shadow-sm">
          <CardContent className="flex items-center justify-between p-5">
            <div>
              <p className="text-xs font-medium uppercase tracking-wider text-gray-500">
                Tài xế sẵn sàng
              </p>
              <p className="mt-1 text-2xl font-bold text-emerald-600">{availableDrivers.length}</p>
              <p className="mt-1 text-xs text-gray-500">Sẵn sàng nhận tuyến</p>
            </div>
            <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-emerald-50 text-emerald-600">
              <CheckCircle2 size={24} />
            </div>
          </CardContent>
        </Card>
      </div>

      {error && <p role="alert" className="text-sm text-rose-700">{error}</p>}

      <Card className="shadow-sm">
        <CardHeader><CardTitle className="flex items-center gap-2 text-base"><AlertTriangle className="text-amber-600" size={18} /> Cần can thiệp</CardTitle><p className="text-xs text-gray-500">Đơn thất bại hoặc bị huỷ cần dispatcher kiểm tra và quyết định xử lý tiếp.</p></CardHeader>
        <CardContent>{interventionOrders.length === 0 ? <p className="text-sm text-gray-500">Không có đơn cần can thiệp.</p> : <div className="space-y-2">{interventionOrders.map((order) => <div key={order.id} className="flex items-center justify-between rounded-lg border border-amber-200 p-3 text-sm"><span className="font-medium">{order.order_code}</span><OrderStatusBadge status={order.status} /></div>)}</div>}</CardContent>
      </Card>

      <Card className="shadow-sm">
        <CardHeader><CardTitle className="text-base">Điều phối lại trước pickup</CardTitle><p className="text-xs text-gray-500">Chỉ các đơn đã lập kế hoạch mới có thể đổi tài xế trước khi hàng được lấy.</p></CardHeader>
        <CardContent>{reassignableOrders.length === 0 ? <p className="text-sm text-gray-500">Không có đơn nào có thể điều phối lại.</p> : <div className="space-y-2">{reassignableOrders.map((order) => <div key={order.id} className="flex items-center justify-between rounded-lg border border-gray-200 p-3 text-sm"><span className="font-medium">{order.order_code}</span><Button size="sm" variant="outline" onClick={() => setSelectedOrderId(order.id)}>Chọn tài xế</Button></div>)}</div>}</CardContent>
      </Card>
      <Modal open={selectedOrderId !== null} onOpenChange={(open) => !open && setSelectedOrderId(null)} title="Điều phối lại tài xế" description="Thao tác này chỉ khả dụng trước khi tài xế xác nhận pickup.">
        <div className="space-y-2">{availableDrivers.map((driver) => <Button key={driver.id} variant="outline" className="w-full justify-start" isLoading={isReassigning} onClick={() => handleReassign(driver.user_id)}>{driver.name}{driver.vehicle_plate ? ` · ${driver.vehicle_plate}` : ''}</Button>)}{availableDrivers.length === 0 && <p className="text-sm text-gray-500">Không có tài xế sẵn sàng.</p>}</div>
      </Modal>

      {/* Recent Orders Section */}
      <Card className="shadow-sm">
        <CardHeader className="flex flex-row items-center justify-between pb-3">
          <div>
            <CardTitle className="text-base font-bold text-gray-900">
              Đơn hàng Gần đây Nhất
            </CardTitle>
            <p className="text-xs text-gray-500">
              Các đơn đang chờ xử lý, đang giao hoặc cần can thiệp
            </p>
          </div>
          <Link
            href="/orders"
            className="flex items-center gap-1 text-xs font-semibold text-blue-600 hover:text-blue-700"
          >
            Xem tất cả đơn <ArrowRight size={14} />
          </Link>
        </CardHeader>
        <CardContent className="p-0">
          {isLoadingOrders ? (
            <div className="flex h-32 items-center justify-center">
              <div className="h-6 w-6 animate-spin rounded-full border-2 border-blue-600 border-t-transparent" />
            </div>
          ) : recentOrders.length === 0 ? (
            <div className="py-8 text-center text-sm text-gray-400">Chưa có đơn hàng nào</div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead className="bg-gray-50 text-xs uppercase tracking-wider text-gray-500 border-y">
                  <tr>
                    <th className="px-4 py-3">Mã đơn</th>
                    <th className="px-4 py-3">Cửa hàng</th>
                    <th className="px-4 py-3">Địa chỉ giao</th>
                    <th className="px-4 py-3">Trọng lượng</th>
                    <th className="px-4 py-3">Trạng thái</th>
                    <th className="px-4 py-3">Thời gian tạo</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {recentOrders.map((order) => (
                    <tr key={order.id} className="hover:bg-gray-50 transition-colors">
                      <td className="px-4 py-3 font-mono font-medium text-blue-700">
                        {order.order_code}
                      </td>
                      <td className="px-4 py-3 text-gray-700">{order.tenant_name || '—'}</td>
                      <td className="px-4 py-3 text-gray-600 max-w-xs truncate">
                        {order.delivery_address}
                      </td>
                      <td className="px-4 py-3">{order.weight_kg} kg</td>
                      <td className="px-4 py-3">
                        <OrderStatusBadge status={order.status} />
                      </td>
                      <td className="px-4 py-3 text-xs text-gray-500">
                        {formatVNDate(order.created_at)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
