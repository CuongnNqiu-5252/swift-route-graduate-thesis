'use client';

import { useState, useEffect, useCallback } from 'react';
import {
  PickupLocation,
  PickupLocationStatus,
  PICKUP_LOCATION_STATUS_COLOR,
  PICKUP_LOCATION_STATUS_LABEL,
} from '@/domain/entities/pickup-location.entity';
import { pickupLocationRepository } from '@/infrastructure/repositories/pickup-location.repository';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import {
  MapPin,
  Plus,
  RefreshCw,
  Star,
  Edit2,
  Trash2,
  Phone,
  User,
  Building2,
  X,
} from 'lucide-react';
import { toast } from 'sonner';
import LocationMapPicker from '@/components/locations/location-map-picker';
import PickupLocationsMap from '@/components/locations/pickup-locations-map';

const messageFrom = (error: unknown, fallback: string) => error instanceof Error && error.message ? error.message : fallback;

export default function PickupLocationsPage() {
  const [locations, setLocations] = useState<PickupLocation[]>([]);
  const [statusFilter, setStatusFilter] = useState<PickupLocationStatus | undefined>(undefined);
  const [isLoading, setIsLoading] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editingLoc, setEditingLoc] = useState<PickupLocation | null>(null);

  // Form state
  const [name, setName] = useState('');
  const [address, setAddress] = useState('');
  const [lat, setLat] = useState<string>('10.0300');
  const [lng, setLng] = useState<string>('105.7800');
  const [contactName, setContactName] = useState('');
  const [contactPhone, setContactPhone] = useState('');
  const [isDefault, setIsDefault] = useState(false);
  const [showCoordinates, setShowCoordinates] = useState(false);
  const [locationConfirmed, setLocationConfirmed] = useState(false);
  const [resolvingAddress, setResolvingAddress] = useState(false);

  const fetchLocations = useCallback(async () => {
    setIsLoading(true);
    try {
      const data = await pickupLocationRepository.getPickupLocations(statusFilter);
      setLocations(data);
    } catch (error: unknown) {
      toast.error(messageFrom(error, 'Không thể tải danh sách điểm lấy hàng.'));
    } finally {
      setIsLoading(false);
    }
  }, [statusFilter]);

  useEffect(() => {
    const frame = window.requestAnimationFrame(() => { void fetchLocations(); });
    return () => window.cancelAnimationFrame(frame);
  }, [fetchLocations]);

  const openCreateModal = () => {
    setEditingLoc(null);
    setName('');
    setAddress('');
    setLat('10.0300');
    setLng('105.7800');
    setContactName('');
    setContactPhone('');
    setIsDefault(locations.length === 0);
    setLocationConfirmed(false);
    setModalOpen(true);
  };

  const openEditModal = (loc: PickupLocation) => {
    setEditingLoc(loc);
    setName(loc.name);
    setAddress(loc.address);
    setLat(loc.lat.toString());
    setLng(loc.lng.toString());
    setContactName(loc.contact_name || '');
    setContactPhone(loc.contact_phone || '');
    setIsDefault(loc.is_default);
    setLocationConfirmed(true);
    setModalOpen(true);
  };

  const closeModal = () => {
    setModalOpen(false);
    setEditingLoc(null);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const latNum = parseFloat(lat);
    const lngNum = parseFloat(lng);

    if (!locationConfirmed) {
      toast.error('Hãy tìm địa chỉ hoặc đặt ghim tại đúng điểm lấy hàng.');
      return;
    }

    if (isNaN(latNum) || latNum < -90 || latNum > 90) {
      toast.error('Vĩ độ (Latitude) phải nằm trong khoảng [-90, 90]');
      return;
    }
    if (isNaN(lngNum) || lngNum < -180 || lngNum > 180) {
      toast.error('Kinh độ (Longitude) phải nằm trong khoảng [-180, 180]');
      return;
    }

    setIsSubmitting(true);
    try {
      if (editingLoc) {
        await pickupLocationRepository.updatePickupLocation(editingLoc.id, {
          name,
          address,
          lat: latNum,
          lng: lngNum,
          contactName: contactName || undefined,
          contactPhone: contactPhone || undefined,
          isDefault,
        });
        toast.success('Cập nhật điểm lấy hàng thành công!');
      } else {
        await pickupLocationRepository.createPickupLocation({
          name,
          address,
          lat: latNum,
          lng: lngNum,
          contactName: contactName || undefined,
          contactPhone: contactPhone || undefined,
          isDefault,
        });
        toast.success('Đã thêm điểm lấy hàng thành công!');
      }
      closeModal();
      fetchLocations();
    } catch (error: unknown) {
      toast.error(messageFrom(error, 'Có lỗi xảy ra.'));
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleSetDefault = async (id: string) => {
    try {
      await pickupLocationRepository.setDefaultLocation(id);
      toast.success('Đã đặt làm điểm lấy hàng mặc định!');
      fetchLocations();
    } catch (error: unknown) {
      toast.error(messageFrom(error, 'Không thể đặt mặc định.'));
    }
  };

  const handleDelete = async (loc: PickupLocation) => {
    if (!confirm(`Bạn có chắc muốn ngừng hoạt động điểm lấy hàng "${loc.name}"?`)) {
      return;
    }
    try {
      await pickupLocationRepository.deletePickupLocation(loc.id);
      toast.success('Đã ngừng hoạt động điểm lấy hàng.');
      fetchLocations();
    } catch (error: unknown) {
      toast.error(messageFrom(error, 'Không thể ngừng hoạt động điểm lấy hàng.'));
    }
  };

  return (
    <div className="space-y-6 p-6">
      {/* Header */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <div className="flex items-center gap-2">
            <MapPin className="h-7 w-7 text-blue-600" />
            <h1 className="text-2xl font-bold tracking-tight text-gray-900">Điểm lấy hàng & Kho</h1>
          </div>
          <p className="mt-1 text-sm text-gray-500">
            Khai báo và quản lý các địa điểm xuất phát đơn hàng (cửa hàng, chi nhánh, kho bãi) của doanh nghiệp.
          </p>
        </div>
        <div className="flex items-center gap-3">
          <Button variant="outline" size="sm" onClick={() => fetchLocations()} className="gap-2">
            <RefreshCw className={`h-4 w-4 ${isLoading ? 'animate-spin' : ''}`} />
            Làm mới
          </Button>
          <Button onClick={openCreateModal} className="gap-2 bg-blue-600 hover:bg-blue-700">
            <Plus className="h-4 w-4" />
            Thêm điểm lấy hàng
          </Button>
        </div>
      </div>

      {/* Filter Tabs */}
      <div className="flex items-center justify-between border-b border-gray-200 pb-3">
        <div className="flex gap-2">
          <button
            onClick={() => setStatusFilter(undefined)}
            className={`rounded-lg px-3 py-1.5 text-sm font-medium transition-colors ${
              statusFilter === undefined ? 'bg-blue-100 text-blue-700' : 'text-gray-600 hover:bg-gray-100'
            }`}
          >
            Tất cả ({locations.length})
          </button>
          <button
            onClick={() => setStatusFilter('ACTIVE')}
            className={`rounded-lg px-3 py-1.5 text-sm font-medium transition-colors ${
              statusFilter === 'ACTIVE' ? 'bg-blue-100 text-blue-700' : 'text-gray-600 hover:bg-gray-100'
            }`}
          >
            Đang hoạt động ({locations.filter((l) => l.status === 'ACTIVE').length})
          </button>
          <button
            onClick={() => setStatusFilter('INACTIVE')}
            className={`rounded-lg px-3 py-1.5 text-sm font-medium transition-colors ${
              statusFilter === 'INACTIVE' ? 'bg-blue-100 text-blue-700' : 'text-gray-600 hover:bg-gray-100'
            }`}
          >
            Ngừng hoạt động ({locations.filter((l) => l.status === 'INACTIVE').length})
          </button>
        </div>
      </div>

      {locations.length > 0 && (
        <section className="overflow-hidden rounded-xl border border-slate-200 bg-white" aria-labelledby="locations-map-title">
          <div className="flex flex-wrap items-start justify-between gap-3 px-5 py-4">
            <div>
              <h2 id="locations-map-title" className="font-semibold text-slate-950">Bản đồ kho và điểm lấy hàng</h2>
              <p className="mt-1 text-sm text-slate-500">Theo dõi toàn bộ địa điểm xuất phát đơn hàng của workspace trên cùng một bản đồ.</p>
            </div>
            <div className="flex items-center gap-4 text-xs font-medium text-slate-600">
              <span>{locations.filter((location) => location.status === 'ACTIVE').length} đang hoạt động</span>
              <span>{locations.filter((location) => location.is_default).length > 0 ? 'Đã chọn điểm mặc định' : 'Chưa có điểm mặc định'}</span>
            </div>
          </div>
          <div className="border-y border-slate-200">
            <PickupLocationsMap locations={locations} />
          </div>
          <div className="flex flex-wrap items-center gap-x-5 gap-y-2 px-5 py-3 text-xs text-slate-500">
            <span className="flex items-center gap-2"><span className="h-2.5 w-2.5 rounded-full bg-blue-600" aria-hidden="true" />Đang hoạt động</span>
            <span className="flex items-center gap-2"><span className="h-2.5 w-2.5 rounded-full bg-slate-500" aria-hidden="true" />Ngừng hoạt động</span>
            <span>Ghim lớn hơn là điểm lấy hàng mặc định.</span>
          </div>
        </section>
      )}

      {/* Locations Table */}
      <Card>
        <CardContent className="p-0">
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm text-gray-600">
              <thead className="border-b border-gray-200 bg-gray-50 text-xs font-semibold uppercase text-gray-700">
                <tr>
                  <th className="px-6 py-4">Tên điểm lấy hàng</th>
                  <th className="px-6 py-4">Địa chỉ & Tọa độ</th>
                  <th className="px-6 py-4">Người liên hệ</th>
                  <th className="px-6 py-4">Trạng thái</th>
                  <th className="px-6 py-4 text-right">Thao tác</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {isLoading ? (
                  <tr>
                    <td colSpan={5} className="py-12 text-center text-gray-400">
                      Đang tải danh sách điểm lấy hàng...
                    </td>
                  </tr>
                ) : locations.length === 0 ? (
                  <tr>
                    <td colSpan={5} className="py-12 text-center text-gray-400">
                      Chưa có điểm lấy hàng nào. Bấm &quot;Thêm điểm lấy hàng&quot; để tạo điểm đầu tiên.
                    </td>
                  </tr>
                ) : (
                  locations.map((loc) => (
                    <tr key={loc.id} className="hover:bg-gray-50/70 transition-colors">
                      <td className="px-6 py-4">
                        <div className="flex items-center gap-2">
                          <Building2 className="h-5 w-5 text-gray-400 flex-shrink-0" />
                          <div>
                            <div className="font-semibold text-gray-900 flex items-center gap-2">
                              {loc.name}
                              {loc.is_default && (
                                <span className="inline-flex items-center gap-1 rounded-full bg-amber-50 px-2.5 py-0.5 text-xs font-medium text-amber-700 border border-amber-200">
                                  <Star className="h-3 w-3 fill-amber-400 text-amber-500" />
                                  Mặc định
                                </span>
                              )}
                            </div>
                            <span className="text-xs text-gray-400 font-mono">ID: {loc.id.substring(0, 8)}</span>
                          </div>
                        </div>
                      </td>

                      <td className="px-6 py-4">
                        <div className="max-w-md">
                          <p className="font-medium text-gray-800 line-clamp-1">{loc.address}</p>
                          <p className="text-xs text-gray-400 font-mono mt-0.5">
                            {loc.lat.toFixed(6)}, {loc.lng.toFixed(6)}
                          </p>
                        </div>
                      </td>

                      <td className="px-6 py-4">
                        <div className="text-sm">
                          {loc.contact_name ? (
                            <div className="flex items-center gap-1.5 text-gray-800">
                              <User className="h-3.5 w-3.5 text-gray-400" />
                              <span>{loc.contact_name}</span>
                            </div>
                          ) : (
                            <span className="text-xs text-gray-400 italic">Chưa khai báo</span>
                          )}
                          {loc.contact_phone && (
                            <div className="flex items-center gap-1.5 text-gray-500 text-xs mt-0.5">
                              <Phone className="h-3 w-3 text-gray-400" />
                              <span>{loc.contact_phone}</span>
                            </div>
                          )}
                        </div>
                      </td>

                      <td className="px-6 py-4">
                        <span
                          className={`inline-flex rounded-full px-2.5 py-0.5 text-xs font-medium border ${
                            PICKUP_LOCATION_STATUS_COLOR[loc.status]
                          }`}
                        >
                          {PICKUP_LOCATION_STATUS_LABEL[loc.status]}
                        </span>
                      </td>

                      <td className="px-6 py-4 text-right">
                        <div className="flex items-center justify-end gap-2">
                          {!loc.is_default && loc.status === 'ACTIVE' && (
                            <Button
                              variant="ghost"
                              size="sm"
                              title="Đặt làm mặc định"
                              onClick={() => handleSetDefault(loc.id)}
                              className="h-8 px-2 text-amber-600 hover:text-amber-700 hover:bg-amber-50"
                            >
                              <Star className="h-4 w-4" />
                            </Button>
                          )}
                          <Button
                            variant="ghost"
                            size="sm"
                            title="Chỉnh sửa"
                            onClick={() => openEditModal(loc)}
                            className="h-8 px-2 text-blue-600 hover:text-blue-700 hover:bg-blue-50"
                          >
                            <Edit2 className="h-4 w-4" />
                          </Button>
                          {loc.status === 'ACTIVE' && (
                            <Button
                              variant="ghost"
                              size="sm"
                              title="Ngừng hoạt động"
                              onClick={() => handleDelete(loc)}
                              className="h-8 px-2 text-rose-600 hover:text-rose-700 hover:bg-rose-50"
                            >
                              <Trash2 className="h-4 w-4" />
                            </Button>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </CardContent>
      </Card>

      {/* Add / Edit Modal */}
      {modalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <div className="max-h-[92vh] w-full max-w-4xl overflow-y-auto rounded-xl bg-white p-6 shadow-xl animate-in fade-in zoom-in-95">
            <div className="flex items-center justify-between border-b border-gray-100 pb-3">
              <h2 className="text-lg font-bold text-gray-900">
                {editingLoc ? 'Chỉnh sửa điểm lấy hàng' : 'Thêm điểm lấy hàng mới'}
              </h2>
              <button onClick={closeModal} className="text-gray-400 hover:text-gray-600">
                <X className="h-5 w-5" />
              </button>
            </div>

            <form onSubmit={handleSubmit} className="mt-4 grid gap-6 lg:grid-cols-[1fr_1.15fr]">
              <div className="space-y-4">
              <div>
                <label className="block text-xs font-medium text-gray-700">Tên điểm lấy hàng / kho *</label>
                <input
                  type="text"
                  required
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder="Ví dụ: Kho Tổng Cần Thơ, Chi nhánh Ninh Kiều"
                  className="mt-1 w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none"
                />
              </div>

              <div>
                <label className="block text-xs font-medium text-gray-700">Địa chỉ chi tiết *</label>
                <input
                  type="text"
                  required
                  value={address}
                  onChange={(e) => setAddress(e.target.value)}
                  placeholder="Ví dụ: 123 Đường 3/2, P. Xuân Khánh, Q. Ninh Kiều, Cần Thơ"
                  className="mt-1 w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none"
                />
              </div>

              <div className="overflow-hidden rounded-xl border border-slate-200">
                <LocationMapPicker
                  lat={Number(lat)}
                  lng={Number(lng)}
                  address={address}
                  onResolvingChange={setResolvingAddress}
                  onChange={(nextLat, nextLng, resolvedAddress) => {
                    setLat(nextLat.toFixed(6));
                    setLng(nextLng.toFixed(6));
                    setLocationConfirmed(true);
                    if (resolvedAddress) setAddress(resolvedAddress);
                  }}
                />
              </div>

              <button type="button" onClick={() => setShowCoordinates((value) => !value)} className="text-left text-xs font-semibold text-blue-700 underline underline-offset-4">
                {showCoordinates ? 'Ẩn tọa độ nâng cao' : 'Hiện tọa độ nâng cao'}
              </button>

              {showCoordinates && <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-medium text-gray-700">Vĩ độ (Latitude) *</label>
                  <input
                    type="number"
                    step="0.000001"
                    required
                    value={lat}
                    onChange={(e) => setLat(e.target.value)}
                    className="mt-1 w-full rounded-lg border border-gray-300 px-3 py-2 text-sm font-mono focus:border-blue-500 focus:outline-none"
                  />
                </div>
                <div>
                  <label className="block text-xs font-medium text-gray-700">Kinh độ (Longitude) *</label>
                  <input
                    type="number"
                    step="0.000001"
                    required
                    value={lng}
                    onChange={(e) => setLng(e.target.value)}
                    className="mt-1 w-full rounded-lg border border-gray-300 px-3 py-2 text-sm font-mono focus:border-blue-500 focus:outline-none"
                  />
                </div>
              </div>}

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-medium text-gray-700">Người liên hệ</label>
                  <input
                    type="text"
                    value={contactName}
                    onChange={(e) => setContactName(e.target.value)}
                    placeholder="Nguyễn Văn Quản Kho"
                    className="mt-1 w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none"
                  />
                </div>
                <div>
                  <label className="block text-xs font-medium text-gray-700">Số điện thoại liên hệ</label>
                  <input
                    type="text"
                    value={contactPhone}
                    onChange={(e) => setContactPhone(e.target.value)}
                    placeholder="0912345678"
                    className="mt-1 w-full rounded-lg border border-gray-300 px-3 py-2 text-sm focus:border-blue-500 focus:outline-none"
                  />
                </div>
              </div>

              <div className="flex items-center gap-2 pt-2">
                <input
                  type="checkbox"
                  id="isDefaultCheckbox"
                  checked={isDefault}
                  onChange={(e) => setIsDefault(e.target.checked)}
                  className="h-4 w-4 rounded border-gray-300 text-blue-600 focus:ring-blue-500"
                />
                <label htmlFor="isDefaultCheckbox" className="text-sm font-medium text-gray-700">
                  Đặt làm điểm lấy hàng mặc định của doanh nghiệp
                </label>
              </div>

              </div>
              <div className="space-y-4">
              <div className="rounded-xl bg-slate-50 p-4 text-sm text-slate-600">
                <p className="font-semibold text-slate-900">Cách chọn vị trí</p>
                <p className="mt-1">Điền địa chỉ để lưu thông tin hiển thị, sau đó đặt ghim chính xác trên bản đồ. Tài xế sẽ được dẫn tới chính tọa độ này.</p>
              </div>
              <div className="flex justify-end gap-3 border-t border-gray-100 pt-4">
                <Button type="button" variant="outline" onClick={closeModal}>
                  Hủy
                </Button>
                <Button
                  type="submit"
                  disabled={isSubmitting || resolvingAddress || !locationConfirmed}
                  className="bg-blue-600 hover:bg-blue-700"
                >
                  {isSubmitting ? 'Đang lưu...' : resolvingAddress ? 'Đang lấy địa chỉ...' : 'Lưu điểm lấy hàng'}
                </Button>
              </div>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
