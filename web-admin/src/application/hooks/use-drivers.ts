'use client';

import { useState, useEffect } from 'react';
import { CreateDriverPayload, Driver } from '@/domain/entities/driver.entity';
import { driverRepository } from '@/infrastructure/repositories/driver.repository';

export function useDrivers() {
  const [drivers, setDrivers] = useState<Driver[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    driverRepository
      .getDrivers()
      .then(setDrivers)
      .catch((err) => setError(err instanceof Error ? err.message : 'Lỗi tải tài xế'))
      .finally(() => setIsLoading(false));
  }, []);

  const createDriver = async (payload: CreateDriverPayload) => {
    const created = await driverRepository.createDriver(payload);
    setDrivers((current) => [created, ...current]);
    return created;
  };

  const reviewDriver = async (driverId: string, approved: boolean, note?: string) => {
    const updated = await driverRepository.reviewDriver(driverId, approved, note);
    setDrivers((current) => current.map((driver) => driver.id === driverId ? updated : driver));
    return updated;
  };

  const refetch = async () => {
    setIsLoading(true);
    setError(null);
    try { setDrivers(await driverRepository.getDrivers()); }
    catch (err) { setError(err instanceof Error ? err.message : 'Lỗi tải tài xế'); }
    finally { setIsLoading(false); }
  };

  return { drivers, isLoading, error, createDriver, reviewDriver, refetch };
}
