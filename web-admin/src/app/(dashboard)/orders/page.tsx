'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useOrders } from '@/application/hooks/use-orders';
import { OrderStatus } from '@/domain/entities/order.entity';
import { OrderTable } from '@/components/orders/order-table';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { RefreshCw, Route, Plus } from 'lucide-react';
import { toast } from 'sonner';
import { OrderCreatePanel } from '@/components/orders/order-create-panel';
import OrderTrackingMap from '@/components/orders/order-tracking-map';
import { useOrderLiveTracking } from '@/application/hooks/use-order-live-tracking';

const STATUS_TABS: { label: string; value?: OrderStatus }[] = [
  { label: 'Tất cả' },
  { label: 'Chờ điều phối', value: 'PAID' },
  { label: 'Đang giao', value: 'IN_TRANSIT' },
  { label: 'Đã hoàn thành', value: 'DELIVERED' },
  { label: 'Sự cố / Thất bại', value: 'FAILED' },
  { label: 'Đã hủy', value: 'CANCELLED' },
];

export default function OrdersPage() {
  const [activeTab, setActiveTab] = useState<OrderStatus | undefined>(undefined);
  const { orders, isLoading, refetch, updateStatus, cancelOrder } = useOrders(activeTab);
  const [showCreate,setShowCreate]=useState(false); const [selectedId,setSelectedId]=useState('');
  const selected=orders.find(order=>order.id===selectedId) || orders[0];
  const tracking = useOrderLiveTracking(selected?.id, selected?.assigned_driver_id);

  const handleUpdateStatus = async (orderId: string, newStatus: OrderStatus) => {
    try {
      await updateStatus(orderId, newStatus);
      toast.success('Cập nhật trạng thái đơn thành công');
    } catch {
      toast.error('Lỗi khi cập nhật trạng thái');
    }
  };

  const handleCancelOrder = async (orderId: string, reason?: string) => {
    try {
      await cancelOrder(orderId, reason);
      toast.success('Đã huỷ đơn hàng thành công');
    } catch {
      toast.error('Lỗi khi huỷ đơn hàng');
    }
  };


  return (
    <div className="space-y-6">
      {/* Page Header */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-gray-900">Quản lý Đơn hàng</h1>
          <p className="text-sm text-gray-500">
            Theo dõi, cập nhật trạng thái và điều phối các đơn hàng vận chuyển trong hệ thống
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <Button size="sm" onClick={()=>setShowCreate(value=>!value)}><Plus size={15}/>Tạo đơn</Button>
          <Button variant="outline" size="sm" onClick={() => refetch()} isLoading={isLoading}>
            <RefreshCw size={14} /> Làm mới
          </Button>
          <Link href="/dispatch">
            <Button size="sm" variant="outline">
              <Route size={15} /> Điều phối
            </Button>
          </Link>
        </div>
      </div>
      {showCreate && (
        <OrderCreatePanel onCreated={() => { setShowCreate(false); void refetch(); }} onClose={() => setShowCreate(false)} />
      )}

      {selected && <section className="grid overflow-hidden border-y border-slate-200 lg:grid-cols-[0.65fr_1.35fr]">
        <div className="space-y-4 bg-slate-50 p-5"><div><h2 className="font-bold text-slate-950">Theo dõi vận hành</h2><p className="text-sm text-slate-600">Vị trí cập nhật khi tài xế đang chia sẻ GPS.</p></div><select aria-label="Đơn hàng cần theo dõi" value={selected.id} onChange={e=>setSelectedId(e.target.value)} className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm">{orders.map(o=><option key={o.id} value={o.id}>{o.order_code} · {o.status}</option>)}</select><div className="text-sm"><p className="font-semibold text-slate-900">{selected.customer_name || 'Người nhận'}</p><p className="mt-1 text-slate-600">{selected.pickup_address}</p><p className="my-1 text-slate-400">↓</p><p className="text-slate-600">{selected.delivery_address}</p></div><TrackingSignal signal={tracking.signal} lastUpdate={tracking.lastUpdate} accuracy={tracking.accuracy} assigned={Boolean(selected.assigned_driver_id)} /></div>
        <OrderTrackingMap pickup={[selected.pickup_lat,selected.pickup_lng]} delivery={[selected.delivery_lat,selected.delivery_lng]} driver={tracking.position}/>
      </section>}

      <p role="status" className="text-sm text-slate-600">
        {isLoading ? 'Đang tải đơn hàng…' : `Hiển thị ${orders.length} đơn trong bộ lọc hiện tại`}
      </p>
      {/* Filter Tabs */}
      <div className="flex items-center gap-2 border-b border-gray-200 pb-2 overflow-x-auto">
        {STATUS_TABS.map((tab) => {
          const isActive = activeTab === tab.value;
          return (
            <button
              key={tab.label}
              onClick={() => setActiveTab(tab.value)}
              className={`min-h-11 shrink-0 border-b-2 px-3 py-2 text-sm font-medium transition-colors ${
                isActive
                  ? 'border-slate-900 text-slate-950'
                  : 'border-transparent text-gray-600 hover:bg-gray-100 hover:text-gray-900'
              }`}
            >
              {tab.label}
            </button>
          );
        })}
      </div>

      {/* Orders Table */}
      <Card className="shadow-sm">
        <CardContent className="p-0">
          <OrderTable
            orders={orders}
            isLoading={isLoading}
            onUpdateStatus={handleUpdateStatus}
            onCancelOrder={handleCancelOrder}
          />
        </CardContent>
      </Card>
    </div>
  );
}

function TrackingSignal({ signal, lastUpdate, accuracy, assigned }: { signal: 'waiting' | 'live' | 'stale' | 'reconnecting'; lastUpdate?: Date; accuracy?: number; assigned: boolean }) {
  if (!assigned) return <p className="text-xs text-slate-500">Đơn chưa được phân công tài xế.</p>;
  const presentation = signal === 'live' ? ['bg-emerald-500', 'Đang nhận GPS trực tiếp'] : signal === 'stale' ? ['bg-amber-500', 'Tín hiệu GPS đã cũ'] : signal === 'reconnecting' ? ['bg-rose-500', 'Đang kết nối lại luồng GPS'] : ['bg-slate-400', 'Đang chờ vị trí mới'];
  return <div className="rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs"><p className="flex items-center gap-2 font-medium text-slate-800"><span className={`h-2 w-2 rounded-full ${presentation[0]}`} />{presentation[1]}</p>{lastUpdate && <p className="mt-1 text-slate-500">Cập nhật {lastUpdate.toLocaleTimeString('vi-VN')}{accuracy != null ? ` · sai số ±${Math.round(accuracy)} m` : ''}</p>}</div>;
}
