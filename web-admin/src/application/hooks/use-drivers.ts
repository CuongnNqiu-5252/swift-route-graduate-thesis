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

  return { drivers, isLoading, error, createDriver };
}
