'use client';

import { useEffect, useState } from 'react';

import { readOrderCsv } from '@/application/order-import-csv';
import { DriveCsvPicker } from '@/components/orders/drive-csv-picker';
import LocationMapPicker from '@/components/locations/location-map-picker';
import { Button } from '@/components/ui/button';
import { useLocale } from '@/i18n/provider';
import { goongMapsClient } from '@/infrastructure/maps/goong-maps.client';
import {
  orderImportRepository, type ImportResult, type ImportRow, type PendingAddress,
} from '@/infrastructure/repositories/order-import.repository';

const panel = 'space-y-4 rounded-xl border border-slate-200 bg-white p-4';
const input = 'w-full rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-900';
const labels = {
  CREATED: 'Đã tạo',
  NEEDS_ADDRESS: 'Cần bổ sung địa chỉ',
  DUPLICATE: 'Trùng mã nguồn',
  INVALID: 'Có lỗi',
  CANCELLED: 'Đã hủy',
};

function errorMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback;
}

const normalizeAddress = (address: string) => address.normalize('NFC').toLocaleLowerCase('vi')
  .replace(/[,;]+/g, ' ').replace(/\s+/g, ' ').trim();

async function locateExactAddress(address: string) {
  const session = crypto.randomUUID();
  const candidates = await goongMapsClient.searchPlaces(address, undefined, session);
  // A suggestion that only matches part of the address needs an operator to confirm the point.
  const exact = candidates.filter((candidate) =>
    normalizeAddress(candidate.description) === normalizeAddress(address));
  if (exact.length !== 1) return null;
  const detail = await goongMapsClient.getPlaceDetail(exact[0].place_id, session);
  return detail && Math.abs(detail.lat) <= 90 && Math.abs(detail.lng) <= 180 ? detail : null;
}

async function resolveAddress(row: ImportRow): Promise<ImportRow> {
  if (row.deliveryLat !== null || row.deliveryAddress.length < 3 || row.deliveryAddress.length > 500) return row;
  try {
    const detail = await locateExactAddress(row.deliveryAddress);
    return detail ? { ...row, deliveryLat: detail.lat, deliveryLng: detail.lng } : row;
  } catch {
    // A map lookup can be retried during manual correction. Keep the CSV address intact.
    return row;
  }
}

type PreparedCsv = Awaited<ReturnType<typeof readOrderCsv>>;

export function OrderImportPanel({ onImported, prepared, resolveCoordinates = true, stopOnCsvErrors = false }: {
  onImported?: () => void;
  prepared?: PreparedCsv;
  resolveCoordinates?: boolean;
  stopOnCsvErrors?: boolean;
}) {
  const { t } = useLocale();
  const [localPreview, setPreview] = useState<PreparedCsv | null>(null);
  const preview = prepared ?? localPreview;
  const blocked = stopOnCsvErrors && Boolean(preview?.errors.length);
  const [results, setResults] = useState<ImportResult[] | null>(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [driveBusy, setDriveBusy] = useState(false);
  const [fileName, setFileName] = useState('');

  async function choose(file?: File) {
    setPreview(null);
    setResults(null);
    setError('');
    setFileName(file?.name ?? '');
    if (!file) return;
    setBusy(true);
    try {
      setPreview(await readOrderCsv(file));
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : 'Không thể đọc file CSV.');
    } finally {
      setBusy(false);
    }
  }

  async function submit() {
    if (!preview || !preview.rows.length || blocked || busy || driveBusy) return;
    setBusy(true);
    setError('');
    try {
      const rows: ImportRow[] = resolveCoordinates ? [] : preview.rows;
      if (resolveCoordinates) {
        for (let i = 0; i < preview.rows.length; i += 6) {
          rows.push(...await Promise.all(preview.rows.slice(i, i + 6).map(resolveAddress)));
        }
      }
      const imported = await orderImportRepository.submit(rows);
      setResults([...preview.errors, ...imported].sort((a, b) => a.line - b.line));
      onImported?.();
    } catch (failure) {
      setError(errorMessage(failure, 'Không thể hoàn tất lần nhập. Vui lòng thử lại.'));
    } finally {
      setBusy(false);
    }
  }

  const displayed = results ?? preview?.errors ?? [];
  return (
    <section className={panel} aria-labelledby="order-import-heading">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 id="order-import-heading" className="font-semibold text-slate-950">
          {t('Nhập vận đơn từ CSV')}
        </h2>
        <a href="/templates/order-import.csv" download className="text-sm font-medium text-blue-700 underline">{t('Tải CSV mẫu')}</a>
      </div>
      {prepared ? <p className="break-all text-sm font-medium text-slate-800">{prepared.name}</p> : <div>
        <label htmlFor="order-import-file" className="mb-1 block text-sm font-medium">{t('Chọn file CSV')}</label>
        <div className="flex flex-wrap items-center gap-3">
          <input id="order-import-file" type="file" accept=".csv,text/csv" disabled={busy || driveBusy}
            onChange={(event) => {
              const file = event.currentTarget.files?.[0];
              event.currentTarget.value = '';
              if (file) void choose(file);
            }} className={`max-w-sm ${input}`} />
          <DriveCsvPicker disabled={busy || driveBusy} onFile={choose} onBusy={setDriveBusy} onError={setError} />
        </div>
        {driveBusy && <p role="status" className="mt-2 text-sm text-slate-700">{t('Đang lấy file từ Google Drive…')}</p>}
        {fileName && <p className="mt-2 break-all text-sm font-medium text-slate-800">{fileName}</p>}
        <p className="mt-1 text-xs text-slate-600">{t('UTF-8, tối đa 200 dòng và 512 KB. Dùng dấu chấm cho số thập phân.')}</p>
      </div>}
      {error && <p role="alert" className="text-sm text-red-700">{t(error)}</p>}
      {preview && !results && (
        <>
          <p role="status" className="text-sm text-slate-700">
            {t('Chờ xác nhận')}: {preview.rows.length} · {t('Có lỗi')}: {preview.errors.length}
          </p>
          {preview.rows.length > 0 && (
            <ul className="max-h-44 divide-y divide-slate-200 overflow-auto text-sm">
              {preview.rows.map((row) => (
                <li key={row.line} className="grid gap-1 py-2 sm:grid-cols-[auto_1fr_2fr]">
                  <span className="tabular-nums text-slate-600">{row.line}</span>
                  <span>{row.externalOrderCode} · {row.recipientName}</span>
                  <span className="break-words text-slate-600">{row.deliveryAddress || '—'}</span>
                </li>
              ))}
            </ul>
          )}
          {blocked && <p role="alert" className="text-sm text-red-700">
            {t('Đang bật dừng khi CSV có lỗi. Hãy sửa file hoặc đổi tùy chọn trong node Nhập vận đơn.')}
          </p>}
          <Button size="sm" onClick={() => void submit()} disabled={!preview.rows.length || blocked || driveBusy} isLoading={busy}>
            {t('Xác nhận nhập')}
          </Button>
        </>
      )}
      {results && (
        <div className="space-y-2 border-t border-slate-200 pt-4">
          <h3 className="text-sm font-semibold text-slate-950">{t('Kết quả nhập CSV')}</h3>
          <p role="status" className="flex flex-wrap gap-x-4 gap-y-1 text-sm text-slate-700">
            {Object.entries(labels).map(([status, label]) => {
              const count = results.filter((row) => row.status === status).length;
              return count > 0 && <span key={status}>{t(label)} <strong className="tabular-nums">{count}</strong></span>;
            })}
          </p>
        </div>
      )}
      {displayed.length > 0 && (
        <ul className="divide-y divide-slate-200 rounded-lg border border-slate-200 text-sm">
          {displayed.map((row) => {
            const original = row.status === 'NEEDS_ADDRESS' ? preview?.rows.find((item) => item.line === row.line) : undefined;
            return (
              <li key={row.line} className="px-3">
                <div className="flex flex-wrap items-center gap-x-3 gap-y-1 py-3">
                  <span className="tabular-nums text-slate-600">{t('Dòng')} {row.line}</span>
                  <span className="break-all font-medium">{row.externalOrderCode}</span>
                  <span className={row.status === 'INVALID' ? 'text-red-700' :
                    row.status === 'NEEDS_ADDRESS' ? 'rounded bg-amber-50 px-2 py-0.5 text-amber-900' : 'text-slate-700'}>
                    {t(labels[row.status])}
                  </span>
                  {row.message && <span className="text-slate-700">{t(row.message)}</span>}
                </div>
                {original && <PendingRowActions row={original} onResolved={(status) => {
                  setResults((previous) => previous?.map((item) =>
                    item.line === row.line ? { ...item, status } : item) ?? null);
                  onImported?.();
                }} />}
              </li>
            );
          })}
        </ul>
      )}
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
  onResolved: (status: 'CREATED' | 'CANCELLED') => void;
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
      {mode === 'edit' && <AddressEditor row={row} onClose={() => setMode(undefined)} onSaved={() => onResolved('CREATED')} />}
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

function AddressEditor({ row, onClose, onSaved }: { row: PendingAddress; onClose: () => void; onSaved: () => void }) {
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
        await orderImportRepository.complete(row.externalOrderCode, {
          deliveryAddress: address.trim(), deliveryLat: point.lat, deliveryLng: point.lng,
        });
        onSaved();
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
