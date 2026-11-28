'use client';

import { useCallback, useEffect, useState } from 'react';
import { toast } from 'sonner';
import {
  DeliveryZoneCheckResult,
  DeliveryZoneEnforcementMode,
  TenantDeliveryZone,
} from '@/domain/entities/delivery-zone.entity';
import { PickupLocation } from '@/domain/entities/pickup-location.entity';
import { goongMapsClient } from '@/infrastructure/maps/goong-maps.client';
import { deliveryZoneRepository } from '@/infrastructure/repositories/delivery-zone.repository';
import { pickupLocationRepository } from '@/infrastructure/repositories/pickup-location.repository';

export interface DeliveryZoneDraft {
  centerLat: number;
  centerLng: number;
  radiusKm: number;
  enforcementMode: DeliveryZoneEnforcementMode;
  isActive: boolean;
}

export function useDeliveryZone() {
  const [savedZone, setSavedZone] = useState<TenantDeliveryZone | null>(null);
  const [defaultPickup, setDefaultPickup] = useState<PickupLocation | null>(null);
  const [draft, setDraft] = useState<DeliveryZoneDraft | null>(null);
  const [centerAddress, setCenterAddress] = useState<string>('');
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  const [isChecking, setIsChecking] = useState(false);
  const [checkResult, setCheckResult] = useState<DeliveryZoneCheckResult | null>(null);

  const hydrateFromSources = useCallback(
    (zone: TenantDeliveryZone, pickup: PickupLocation | null) => {
      setSavedZone(zone);
      setDefaultPickup(pickup);
      setCheckResult(null);

      if (zone.configured && zone.center_lat !== null && zone.center_lng !== null && zone.radius_km !== null) {
        setDraft({
          centerLat: zone.center_lat,
          centerLng: zone.center_lng,
          radiusKm: zone.radius_km,
          enforcementMode: zone.enforcement_mode,
          isActive: zone.is_active,
        });
        if (
          pickup &&
          Math.abs(pickup.lat - zone.center_lat) < 0.0002 &&
          Math.abs(pickup.lng - zone.center_lng) < 0.0002
        ) {
          setCenterAddress(pickup.address);
        } else {
          setCenterAddress('');
          void goongMapsClient
            .reverseGeocode(zone.center_lat, zone.center_lng)
            .then((resolved) => {
              if (resolved) setCenterAddress(resolved);
            })
            .catch(() => {
              // Reverse geocode is best-effort for display only; keep empty cleanly without fake text.
            });
        }
        return;
      }

      if (pickup) {
        setDraft({
          centerLat: pickup.lat,
          centerLng: pickup.lng,
          radiusKm: 10,
          enforcementMode: 'STRICT_BLOCK',
          isActive: true,
        });
        setCenterAddress(pickup.address);
        return;
      }

      setDraft(null);
      setCenterAddress('');
    },
    [],
  );

  const reload = useCallback(async () => {
    setIsLoading(true);
    setLoadError(null);
    try {
      const [zone, locations] = await Promise.all([
        deliveryZoneRepository.getMyZone(),
        pickupLocationRepository.getPickupLocations('ACTIVE'),
      ]);
      const anchor = locations.find((item) => item.is_default) ?? locations[0] ?? null;
      hydrateFromSources(zone, anchor);
    } catch {
      setLoadError('Không thể tải cấu hình vùng giao hàng từ máy chủ.');
    } finally {
      setIsLoading(false);
    }
  }, [hydrateFromSources]);

  useEffect(() => {
    let active = true;
    Promise.all([
      deliveryZoneRepository.getMyZone(),
      pickupLocationRepository.getPickupLocations('ACTIVE'),
    ])
      .then(([zone, locations]) => {
        if (!active) return;
        const anchor = locations.find((item) => item.is_default) ?? locations[0] ?? null;
        hydrateFromSources(zone, anchor);
      })
      .catch(() => {
        if (!active) return;
        setLoadError('Không thể tải cấu hình vùng giao hàng từ máy chủ.');
      })
      .finally(() => {
        if (active) setIsLoading(false);
      });
    return () => {
      active = false;
    };
  }, [hydrateFromSources]);

  const updateCenter = useCallback((lat: number, lng: number, resolvedAddress?: string) => {
    setDraft((prev) => {
      if (!prev) {
        return {
          centerLat: lat,
          centerLng: lng,
          radiusKm: 10,
          enforcementMode: 'STRICT_BLOCK',
          isActive: true,
        };
      }
      return { ...prev, centerLat: lat, centerLng: lng };
    });
    if (resolvedAddress !== undefined) {
      setCenterAddress(resolvedAddress);
    }
    setCheckResult(null);
  }, []);

  const updateRadius = useCallback((radiusKm: number) => {
    const clamped = Math.min(50, Math.max(0.5, Math.round(radiusKm * 10) / 10));
    setDraft((prev) => (prev ? { ...prev, radiusKm: clamped } : prev));
    setCheckResult(null);
  }, []);

  const updateEnforcementMode = useCallback((enforcementMode: DeliveryZoneEnforcementMode) => {
    setDraft((prev) => (prev ? { ...prev, enforcementMode } : prev));
    setCheckResult(null);
  }, []);

  const updateActive = useCallback((isActive: boolean) => {
    setDraft((prev) => (prev ? { ...prev, isActive } : prev));
    setCheckResult(null);
  }, []);

  const resetToSaved = useCallback(() => {
    if (!savedZone) return;
    hydrateFromSources(savedZone, defaultPickup);
  }, [defaultPickup, hydrateFromSources, savedZone]);

  const resetCenterToDefaultPickup = useCallback(() => {
    if (!defaultPickup) return;
    updateCenter(defaultPickup.lat, defaultPickup.lng, defaultPickup.address);
  }, [defaultPickup, updateCenter]);

  const save = useCallback(async () => {
    if (!draft) return;
    setIsSaving(true);
    try {
      const updated = await deliveryZoneRepository.upsertMyZone({
        center_lat: draft.centerLat,
        center_lng: draft.centerLng,
        radius_km: draft.radiusKm,
        enforcement_mode: draft.enforcementMode,
        is_active: draft.isActive,
      });
      setSavedZone(updated);
      setCheckResult(null);
      toast.success('Đã lưu cấu hình vùng giao hàng.');
    } catch {
      toast.error('Không thể lưu cấu hình vùng giao hàng.');
    } finally {
      setIsSaving(false);
    }
  }, [draft]);

  const isDirty = Boolean(
    draft &&
      (!savedZone?.configured ||
        savedZone.center_lat === null ||
        savedZone.center_lng === null ||
        savedZone.radius_km === null ||
        Math.abs(savedZone.center_lat - draft.centerLat) > 1e-6 ||
        Math.abs(savedZone.center_lng - draft.centerLng) > 1e-6 ||
        Math.abs(savedZone.radius_km - draft.radiusKm) > 1e-3 ||
        savedZone.enforcement_mode !== draft.enforcementMode ||
        savedZone.is_active !== draft.isActive),
  );

  const checkTestPoint = useCallback(
    async (lat: number, lng: number): Promise<DeliveryZoneCheckResult | null> => {
      if (!savedZone?.configured || isDirty) {
        toast.info('Hãy lưu vùng giao hàng trước khi kiểm tra điểm trên hệ thống.');
        return null;
      }
      setIsChecking(true);
      try {
        const result = await deliveryZoneRepository.checkMyLocation(lat, lng);
        setCheckResult(result);
        return result;
      } catch {
        toast.error('Không thể kiểm tra tọa độ điểm giao.');
        return null;
      } finally {
        setIsChecking(false);
      }
    },
    [isDirty, savedZone?.configured],
  );

  return {
    savedZone,
    defaultPickup,
    draft,
    centerAddress,
    isLoading,
    loadError,
    isSaving,
    isChecking,
    checkResult,
    isDirty,
    reload,
    updateCenter,
    updateRadius,
    updateEnforcementMode,
    updateActive,
    resetToSaved,
    resetCenterToDefaultPickup,
    save,
    checkTestPoint,
    setCheckResult,
  };
}
