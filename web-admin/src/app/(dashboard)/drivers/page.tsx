'use client';

import { useDrivers } from '@/application/hooks/use-drivers';
import { DRIVER_STATUS_COLOR, DRIVER_STATUS_LABEL } from '@/domain/entities/driver.entity';
import { Badge } from '@/components/ui/badge';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import { Users, Truck, Phone, CheckCircle2, AlertTriangle, ShieldCheck } from 'lucide-react';
import { DriverProfileForm } from '@/components/drivers/driver-profile-form';

export default function DriversPage() {
  const { drivers, isLoading, error, createDriver } = useDrivers();

  const availableCount = drivers.filter((d) => d.status === 'AVAILABLE' || !d.status).length;
  const busyCount = drivers.filter((d) => d.status === 'BUSY').length;

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-gray-900">Đội ngũ Tài xế</h1>
        <p className="text-sm text-gray-500">
          Quản lý danh sách, phương tiện và tình trạng sẵn sàng giao hàng của các tài xế
        </p>
      </div>

      {/* KPI Cards */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <Card className="shadow-sm">
          <CardContent className="flex items-center gap-3 p-4">
            <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-blue-100 text-blue-700">
              <Users size={20} />
            </div>
            <div>
              <p className="text-xs text-gray-500">Tổng số tài xế</p>
              <p className="text-xl font-bold text-gray-900">{drivers.length}</p>
            </div>
          </CardContent>
        </Card>

        <Card className="shadow-sm">
          <CardContent className="flex items-center gap-3 p-4">
            <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-emerald-100 text-emerald-700">
              <CheckCircle2 size={20} />
            </div>
            <div>
              <p className="text-xs text-gray-500">Sẵn sàng nhận đơn</p>
              <p className="text-xl font-bold text-emerald-600">{availableCount}</p>
            </div>
          </CardContent>
        </Card>

        <Card className="shadow-sm">
          <CardContent className="flex items-center gap-3 p-4">
            <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-amber-100 text-amber-700">
              <AlertTriangle size={20} />
            </div>
            <div>
              <p className="text-xs text-gray-500">Đang thực hiện chuyến</p>
              <p className="text-xl font-bold text-amber-600">{busyCount}</p>
            </div>
          </CardContent>
        </Card>
      </div>

      <DriverProfileForm drivers={drivers} onCreate={createDriver} />

      {/* Drivers Grid */}
      {isLoading ? (
        <div className="flex h-48 items-center justify-center">
          <div className="h-8 w-8 animate-spin rounded-full border-4 border-blue-600 border-t-transparent" />
        </div>
      ) : error ? (
        <div className="rounded-lg bg-red-50 p-4 text-sm text-red-700">{error}</div>
      ) : (
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
          {drivers.map((driver) => {
            const status = driver.status || 'AVAILABLE';
            return (
              <Card key={driver.id} className="shadow-sm hover:shadow-md transition-shadow">
                <CardHeader className="pb-3">
                  <div className="flex items-start justify-between">
                    <div className="flex items-center gap-3">
                      <div className="flex h-10 w-10 items-center justify-center rounded-full bg-blue-100 text-blue-700 font-bold">
                        {driver.name.charAt(0).toUpperCase()}
                      </div>
                      <div>
                        <CardTitle className="text-base font-bold text-gray-900">
                          {driver.name}
                        </CardTitle>
                        <p className="flex items-center gap-1 text-xs text-gray-500">
                          <Phone size={11} /> {driver.phone}
                        </p>
                      </div>
                    </div>
                    <Badge className={DRIVER_STATUS_COLOR[status]}>
                      {DRIVER_STATUS_LABEL[status]}
                    </Badge>
                  </div>
                </CardHeader>
                <CardContent className="pt-0 space-y-2 text-xs text-gray-600">
                  <div className="flex items-center justify-between border-t border-gray-100 pt-2">
                    <span className="flex items-center gap-1.5 text-gray-500">
                      <Truck size={13} /> Phương tiện:
                    </span>
                    <span className="font-medium text-gray-900">
                      {driver.vehicle_type} ({driver.vehicle_plate})
                    </span>
                  </div>
                  <div className="flex items-center justify-between">
                    <span className="flex items-center gap-1.5 text-gray-500">
                      <ShieldCheck size={13} /> Xác thực hồ sơ:
                    </span>
                    <span className="text-emerald-600 font-medium">Đã kiểm duyệt</span>
                  </div>
                </CardContent>
              </Card>
            );
          })}
        </div>
      )}
    </div>
  );
}
