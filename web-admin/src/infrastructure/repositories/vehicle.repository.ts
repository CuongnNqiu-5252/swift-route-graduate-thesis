import api from '@/lib/api';
import { Vehicle, VehicleStatus, VehicleType } from '@/domain/entities/vehicle.entity';

export interface VehiclePayload {
  tenantId: string; licensePlate: string; vehicleType: VehicleType; capacity?: number;
  make?: string; model?: string; status?: VehicleStatus; assignedDriverId?: string;
}

export const vehicleRepository = {
  async list(): Promise<Vehicle[]> { const r = await api.get<Vehicle[]>('/vehicles'); return Array.isArray(r.data) ? r.data : []; },
  async create(payload: VehiclePayload): Promise<Vehicle> { return (await api.post<Vehicle>('/vehicles', payload)).data; },
  async assign(id: string, driverId?: string): Promise<Vehicle> { return (await api.patch<Vehicle>(`/vehicles/${id}/assignment`, { driverId: driverId || null })).data; },
};
