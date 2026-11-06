export type VehicleType = 'CAR' | 'MOTORCYCLE' | 'VAN';
export type VehicleStatus = 'AVAILABLE' | 'ASSIGNED' | 'MAINTENANCE' | 'INACTIVE';

export interface Vehicle {
  id: string;
  tenantId: string;
  licensePlate: string;
  vehicleType: VehicleType;
  capacity?: number;
  make?: string;
  model?: string;
  status: VehicleStatus;
  assignedDriverId?: string;
  assignedDriverName?: string;
}
