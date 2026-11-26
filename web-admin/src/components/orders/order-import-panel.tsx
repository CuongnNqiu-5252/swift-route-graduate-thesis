'use client';

import { useEffect, useState } from 'react';

import { readOrderCsv } from '@/application/order-import-csv';
import { DriveCsvPicker } from '@/components/orders/drive-csv-picker';
import { OrderImportReview, type ImportReviewRow } from '@/components/orders/order-import-review';
import LocationMapPicker from '@/components/locations/location-map-picker';
import { Button } from '@/components/ui/button';
import type { OrderPriority } from '@/domain/entities/order.entity';
import { useLocale } from '@/i18n/provider';
import { goongMapsClient } from '@/infrastructure/maps/goong-maps.client';
import {
  orderImportRepository, type ImportAddress, type ImportRow, type PendingAddress,
} from '@/infrastructure/repositories/order-import.repository';

const panel = 'space-y-4 rounded-xl border border-slate-200 bg-white p-4';
const input = 'w-full rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-900';
function errorMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback;
}

const normalizeAddress = (address: string) => address.normalize('NFC').toLocaleLowerCase('vi')
  .replace(/[,;]+/g, ' ').replace(/\s+/g, ' ').trim();

async function locateExactAddress(address: string, signal?: AbortSignal) {
  const session = crypto.randomUUID();
  const candidates = await goongMapsClient.searchPlaces(address, undefined, session, signal);
  // A suggestion that only matches part of the address needs an operator to confirm the point.
  const exact = candidates.filter((candidate) =>
    normalizeAddress(candidate.description) === normalizeAddress(address));
  if (exact.length !== 1) return null;
  const detail = await goongMapsClient.getPlaceDetail(exact[0].place_id, session, signal);
  return detail && Math.abs(detail.lat) <= 90 && Math.abs(detail.lng) <= 180 ? detail : null;
}

function hasCoordinates(row: ImportRow) {
  return row.deliveryLat != null && row.deliveryLng != null
    && Math.abs(row.deliveryLat) <= 90 && Math.abs(row.deliveryLng) <= 180;
}

async function resolveAddress(row: ImportRow, signal: AbortSignal) {
  if (hasCoordinates(row) || row.deliveryAddress.length < 3 || row.deliveryAddress.length > 500) return null;
  try {
    return await locateExactAddress(row.deliveryAddress, signal);
  } catch {
    return null;
  }
}

type PreparedFile = Awaited<ReturnType<typeof readOrderCsv>>;
type LocationMatch = NonNullable<Awaited<ReturnType<typeof resolveAddress>>>;

export function OrderImportPanel({
  onImported, onDiscard, prepared, resolveCoordinates = true, stopOnCsvErrors = false, inModal = false,
}: {
  onImported?: () => void;
  onDiscard?: () => void;
  prepared?: PreparedFile;
  resolveCoordinates?: boolean;
  stopOnCsvErrors?: boolean;
  inModal?: boolean;
}) {
  const { t } = useLocale();
  const [localPreview, setPreview] = useState<PreparedFile | null>(null);
  const preview = prepared ?? localPreview;
  const [lookups, setLookups] = useState<{ source: PreparedFile; matches: Map<number, LocationMatch> }>();
  const [priorityOverrides, setPriorityOverrides] = useState<Record<number, OrderPriority>>({});
  const [submission, setSubmission] = useState<{ source: PreparedFile; rows: ImportReviewRow[] }>();
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [driveBusy, setDriveBusy] = useState(false);

  const shouldResolve = Boolean(resolveCoordinates && preview?.rows.some((row) =>
    !hasCoordinates(row) && row.deliveryAddress.length >= 3 && row.deliveryAddress.length <= 500));
  const matches = shouldResolve && lookups?.source === preview ? lookups?.matches : undefined;
  const checking = shouldResolve && !matches;
  const blocked = stopOnCsvErrors && Boolean(preview?.errors.length);
  const results = submission?.source === preview ? submission?.rows : undefined;

  useEffect(() => {
    if (!preview || !shouldResolve) return;
    const controller = new AbortController();
    async function geocode(source: PreparedFile) {
      const matches = new Map<number, LocationMatch>();
      for (let i = 0; i < source.rows.length && !controller.signal.aborted; i += 6) {
        await Promise.all(source.rows.slice(i, i + 6).map(async (row) => {
          const detail = await resolveAddress(row, controller.signal);
          if (detail) matches.set(row.line, detail);
        }));
      }
      if (!controller.signal.aborted) setLookups({ source, matches });
    }
    void geocode(preview);
    return () => controller.abort();
  }, [preview, shouldResolve]);

  async function choose(file?: File) {
    setPreview(null);
    setPriorityOverrides({});
    setSubmission(undefined);
    setError('');
    if (!file) return;
    setBusy(true);
    try {
      setPreview(/\.xlsx$/i.test(file.name)
        ? await orderImportRepository.previewExcel(file) : await readOrderCsv(file));
    } catch (failure) {
      setError(errorMessage(failure, 'Không thể đọc file.'));
    } finally {
      setBusy(false);
    }
  }

  function discard() {
    setPreview(null);
    setLookups(undefined);
    setPriorityOverrides({});
    setSubmission(undefined);
    setError('');
    onDiscard?.();
  }

  const reviewRows: ImportReviewRow[] = preview ? [
    ...preview.errors,
    ...preview.rows.map((row): ImportReviewRow => {
      const matched = matches?.get(row.line);
      const priority = priorityOverrides[row.line] ?? row.priority ?? 'NORMAL';
      const rowWithPriority = { ...row, priority };
      return {
        line: row.line,
        externalOrderCode: row.externalOrderCode,
        status: matched ? 'GEOCODED' : hasCoordinates(row) && row.deliveryAddress.trim()
          ? 'COORDINATES' : checking && row.deliveryAddress.length >= 3
            && row.deliveryAddress.length <= 500 && !hasCoordinates(row) ? 'CHECKING' : 'NEEDS_ADDRESS',
        data: matched ? { ...rowWithPriority, deliveryLat: matched.lat, deliveryLng: matched.lng } : rowWithPriority,
        matchedAddress: matched?.address,
      };
    }),
  ].sort((a, b) => a.line - b.line) : [];

  async function submit() {
    if (!preview?.rows.length || results || busy || driveBusy || blocked || checking) return;
    setBusy(true);
    setError('');
    try {
      const byLine = new Map(reviewRows.map((row) => [row.line, row]));
      const rows = reviewRows.flatMap((row) => row.data ? [row.data] : []);
      const imported = await orderImportRepository.submit(rows);
      setSubmission({
        source: preview,
        rows: [...preview.errors, ...imported.map((result) => ({ ...byLine.get(result.line), ...result }))]
          .sort((a, b) => a.line - b.line),
      });
      onImported?.();
    } catch (failure) {
      setError(errorMessage(failure, 'Không thể hoàn tất lần nhập. Vui lòng thử lại.'));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className={inModal ? 'space-y-4' : panel} aria-labelledby={inModal ? undefined : 'order-import-heading'}>
      <div className={inModal ? 'flex flex-wrap items-center justify-end gap-2' : 'flex flex-wrap items-center justify-between gap-2'}>
        {!inModal && (
          <h2 id="order-import-heading" className="font-semibold text-slate-950">
            {t('Nhập vận đơn từ Excel hoặc CSV')}
          </h2>
        )}
        <div className="flex items-center gap-3">
          <a href="/templates/order-import.xlsx" download className="text-sm font-medium text-blue-700 underline">{t('Tải file Excel mẫu (.xlsx)')}</a>
          <span className="text-slate-300">|</span>
          <a href="/templates/order-import.csv" download className="text-sm font-medium text-slate-600 underline">{t('CSV')}</a>
        </div>
      </div>
      {!prepared && <div>
        <div className="flex flex-wrap items-center gap-3">
          <input id="order-import-file" type="file" accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,.csv,text/csv" disabled={busy || driveBusy}
            onChange={(event) => {
              const file = event.currentTarget.files?.[0];
              event.currentTarget.value = '';
              if (file) void choose(file);
            }} className="peer sr-only" />
          <label htmlFor="order-import-file"
            className="inline-flex min-h-10 cursor-pointer items-center rounded-lg border border-slate-300 px-3 text-sm font-medium text-slate-800 hover:bg-slate-50 peer-focus-visible:outline-2 peer-focus-visible:outline-blue-600 peer-disabled:cursor-default peer-disabled:opacity-50">
            {preview ? t('Chọn file khác') : t('Chọn file Excel (.xlsx) hoặc CSV')}
          </label>
          <DriveCsvPicker disabled={busy || driveBusy} onFile={choose} onBusy={setDriveBusy} onError={setError} />
        </div>
        {driveBusy && <p role="status" className="mt-2 text-sm text-slate-700">{t('Đang lấy file từ Google Drive…')}</p>}
        {busy && !preview && <p role="status" className="mt-2 text-sm text-slate-700">{t('Đang đọc file vận đơn…')}</p>}
        {!preview && <p className="mt-2 text-xs text-slate-600">{t('Hỗ trợ .xlsx hoặc .csv, tối đa 200 dòng vận đơn. Các đơn chưa có tọa độ sẽ được đưa vào hàng đợi bổ sung địa chỉ.')}</p>}
      </div>}
      {error && <p role="alert" className="text-sm text-red-700">{t(error)}</p>}
      {preview && <OrderImportReview
        key={results ? 'saved' : preview.name}
        fileName={preview.name}
        rows={results ?? reviewRows}
        saved={Boolean(results)}
        checking={checking}
        saving={busy}
        blocked={blocked}
        onSave={() => void submit()}
        onDiscard={discard}
        onPriorityChange={(line, nextPriority) => {
          setPriorityOverrides((previous) => ({ ...previous, [line]: nextPriority }));
        }}
        renderPending={(row) => <PendingRowActions row={row} onResolved={(status, address) => {
          setSubmission((previous) => previous?.source === preview ? {
            ...previous,
            rows: previous.rows.map((item) => item.line === row.line ? {
              ...item, status, data: address ? { ...row, ...address } : row,
              matchedAddress: address ? undefined : item.matchedAddress,
            } : item),
          } : previous);
          onImported?.();
        }} />}
      />}
    </section>
  );
}

export function OrderImportAddressQueue({ revision, onCompleted }: { revision: number; onCompleted: () => void }) {
  const { t } = useLocale();
  const [cursor, setCursor] = useState(['']);
  const [page, setPage] = useState<{ key: string; rows: PendingAddress[]; error?: string }>();
  const [retry, setRetry] = useState(0);
  const after = cursor.at(-1) ?? '';
  const key = JSON.stringify([after, revision, retry]);

  useEffect(() => {
    let active = true;
    orderImportRepository.pending(after).then(
      (rows) => { if (active) setPage({ key, rows }); },
      (error) => {
        if (active) setPage({ key, rows: [], error: errorMessage(error, 'Không thể tải danh sách địa chỉ.') });
      },
    );
    return () => { active = false; };
  }, [after, key]);

  const current = page?.key === key ? page : undefined;
  const rows = current?.rows.slice(0, 50) ?? [];
  return (
    <section className={panel} aria-label={t('Cần bổ sung địa chỉ')}>
      {!current && <p role="status" className="text-sm text-slate-600">{t('Đang tải...')}</p>}
      {current?.error && (
        <div className="flex flex-wrap items-center gap-3">
          <p role="alert" className="text-sm text-red-700">{t(current.error)}</p>
          <Button size="sm" variant="outline" onClick={() => setRetry((n) => n + 1)}>{t('Thử lại')}</Button>
        </div>
      )}
      {current && !current.error && !rows.length && (
        <p className="text-sm text-slate-600">{t('Không có dòng nhập nào cần bổ sung địa chỉ.')}</p>
      )}
      {rows.length > 0 && (
        <ul className="divide-y divide-slate-200">
          {rows.map((row) => (
            <li key={row.externalOrderCode} className="py-3">
              <p className="break-all text-sm font-medium text-slate-900">{row.externalOrderCode}</p>
              <PendingRowActions row={row} onResolved={onCompleted} />
            </li>
          ))}
        </ul>
      )}
      {(cursor.length > 1 || (current?.rows.length ?? 0) > 50) && (
        <div className="flex gap-2">
          <Button size="sm" variant="outline" disabled={cursor.length === 1}
            onClick={() => setCursor((values) => values.slice(0, -1))}>{t('Trang trước')}</Button>
          <Button size="sm" variant="outline" disabled={(current?.rows.length ?? 0) <= 50}
            onClick={() => setCursor((values) => [...values, rows[49].externalOrderCode])}>{t('Trang sau')}</Button>
        </div>
      )}
    </section>
  );
}

function PendingRowActions({ row, onResolved }: {
  row: PendingAddress;
  onResolved: (status: 'CREATED' | 'CANCELLED', address?: ImportAddress) => void;
}) {
  const { t } = useLocale();
  const [mode, setMode] = useState<'edit' | 'cancel'>();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  async function cancel() {
    setBusy(true);
    setError('');
    try {
      if (await orderImportRepository.cancel(row.externalOrderCode)) onResolved('CANCELLED');
      else setError('Dòng này đã được xử lý. Hãy làm mới danh sách.');
    } catch (failure) {
      setError(errorMessage(failure, 'Không thể hủy dòng nhập. Vui lòng thử lại.'));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="space-y-3 pb-3">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="min-w-0 break-words text-sm text-slate-600">
          {row.recipientName} · {row.deliveryAddress || t('Thiếu địa chỉ')}
        </p>
        {!mode && <div className="flex flex-wrap gap-2">
          <Button size="sm" variant="outline" onClick={() => setMode('edit')}>{t('Bổ sung địa chỉ')}</Button>
          <Button size="sm" variant="outline" className="text-red-700" onClick={() => setMode('cancel')}>{t('Hủy dòng nhập')}</Button>
        </div>}
      </div>
      {mode === 'edit' && <AddressEditor row={row} onClose={() => setMode(undefined)} onSaved={(address) => onResolved('CREATED', address)} />}
      {mode === 'cancel' && (
        <div className="space-y-2 rounded-md border border-slate-200 p-3">
          <p className="text-sm text-slate-800">{t('Hủy dòng nhập này? Bạn có thể nhập lại từ CSV sau.')}</p>
          {error && <p role="alert" className="text-sm text-red-700">{t(error)}</p>}
          <div className="flex flex-wrap gap-2">
            <Button size="sm" variant="outline" disabled={busy} onClick={() => { setMode(undefined); setError(''); }}>{t('Giữ lại')}</Button>
            <Button size="sm" variant="outline" className="text-red-700" isLoading={busy} onClick={() => void cancel()}>{t('Xác nhận hủy dòng')}</Button>
          </div>
        </div>
      )}
    </div>
  );
}

function AddressEditor({ row, onClose, onSaved }: { row: PendingAddress; onClose: () => void; onSaved: (address: ImportAddress) => void }) {
  const { t } = useLocale();
  const [address, setAddress] = useState(row.deliveryAddress ?? '');
  const [point, setPoint] = useState<{ lat: number; lng: number }>();
  const [resolving, setResolving] = useState(false);
  const [lookingUp, setLookingUp] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  return (
    <form className="space-y-3 border-t border-slate-200 pt-4" onSubmit={async (event) => {
      event.preventDefault();
      if (!point || !address.trim() || resolving || lookingUp || saving) return;
      setSaving(true);
      setError('');
      try {
        const resolved = {
          deliveryAddress: address.trim(), deliveryLat: point.lat, deliveryLng: point.lng,
        };
        await orderImportRepository.complete(row.externalOrderCode, resolved);
        onSaved(resolved);
      } catch (failure) {
        setError(errorMessage(failure, 'Không thể tạo vận đơn. Kiểm tra địa chỉ và thử lại.'));
      } finally {
        setSaving(false);
      }
    }}>
      <h3 className="font-semibold text-slate-950">{row.externalOrderCode} · {t('Bổ sung địa chỉ')}</h3>
      <div className="flex flex-col gap-2 sm:flex-row sm:items-end">
        <label className="block min-w-0 flex-1 text-sm font-medium">
          {t('Địa chỉ giao hàng')}
          <input required value={address} maxLength={500} disabled={lookingUp || saving} className={`mt-1 ${input}`}
            onChange={(event) => {
              setAddress(event.target.value);
              setPoint(undefined);
              setError('');
            }} />
        </label>
        <Button size="sm" type="button" variant="outline" isLoading={lookingUp}
          disabled={address.trim().length < 3 || saving || resolving} onClick={async () => {
            if (lookingUp) return;
            setLookingUp(true);
            setPoint(undefined);
            setError('');
            try {
              const match = await locateExactAddress(address.trim());
              if (match) setPoint({ lat: match.lat, lng: match.lng });
              else setError('Chưa xác định được điểm giao. Hãy chọn vị trí trên bản đồ.');
            } catch {
              setError('Không thể tra địa chỉ. Hãy thử lại hoặc chọn vị trí trên bản đồ.');
            } finally {
              setLookingUp(false);
            }
          }}>{t('Tìm điểm giao')}</Button>
      </div>
      <p className="text-sm text-slate-600">{t('Tìm địa chỉ hoặc chọn điểm giao trên bản đồ.')}</p>
      <LocationMapPicker lat={point?.lat ?? 10.7769} lng={point?.lng ?? 106.7009} address={address}
        hasSelection={Boolean(point)} onResolvingChange={setResolving}
        onChange={(lat, lng, found) => {
          if (lookingUp || saving) return;
          setPoint({ lat, lng });
          setError('');
          if (found && !address.trim()) setAddress(found);
        }} />
      {error && <p role="alert" className="text-sm text-red-700">{t(error)}</p>}
      <div className="flex flex-wrap gap-2">
        <Button size="sm" type="submit" isLoading={saving} disabled={!point || !address.trim() || resolving || lookingUp}>{t('Lưu và tạo vận đơn')}</Button>
        <Button size="sm" type="button" variant="outline" onClick={onClose} disabled={saving}>{t('Đóng')}</Button>
      </div>
    </form>
  );
}
