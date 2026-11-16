import { CSV_MAX_BYTES } from '../../application/order-import-limits.mjs';

export interface DriveCsvSelection {
  id: string;
  name: string;
  resourceKey?: string;
  mimeType?: string;
}

export const GOOGLE_SHEET_MIME = 'application/vnd.google-apps.spreadsheet';

export async function downloadDriveCsv(selection: DriveCsvSelection, token: string, signal: AbortSignal) {
  if (!/\.csv$/i.test(selection.name)) throw new Error('Hãy chọn một file .csv trên Google Drive.');

  const url = new URL('https://www.googleapis.com/drive/v3/files/' + encodeURIComponent(selection.id));
  url.search = new URLSearchParams({ alt: 'media', supportsAllDrives: 'true' }).toString();
  const response = await fetch(url, {
    headers: {
      Authorization: 'Bearer ' + token,
      ...(selection.resourceKey ? {
        'X-Goog-Drive-Resource-Keys': selection.id + '/' + selection.resourceKey,
      } : {}),
    },
    signal,
  });
  if (response.status === 401 || response.status === 403 || response.status === 404) {
    throw new Error('Google Drive không cho tải file này. Hãy kiểm tra quyền và chọn lại file.');
  }
  if (!response.ok || !response.body) throw new Error('Không thể tải file từ Google Drive. Vui lòng thử lại.');

  const bytes = await readBounded(response, CSV_MAX_BYTES, 'File CSV vượt quá 512 KB.');
  return new File([bytes], selection.name, { type: 'text/csv' });
}

async function readBounded(response: Response, limit: number, message: string) {
  if (!response.body) throw new Error(message);
  if (Number(response.headers.get('content-length')) > limit) {
    await response.body.cancel();
    throw new Error(message);
  }
  const reader = response.body.getReader();
  const bytes = new Uint8Array(limit);
  let size = 0;
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      if (size + value.length > limit) {
        await reader.cancel();
        throw new Error(message);
      }
      bytes.set(value, size);
      size += value.length;
    }
  } finally {
    reader.releaseLock();
  }
  return bytes.subarray(0, size);
}

async function requestSheets<T>(
  selection: DriveCsvSelection, path: string, params: URLSearchParams, token: string, signal: AbortSignal,
): Promise<T> {
  const url = new URL('https://sheets.googleapis.com/v4/spreadsheets/' + encodeURIComponent(selection.id) + path);
  url.search = params.toString();
  const response = await fetch(url, {
    signal,
    headers: {
      Authorization: 'Bearer ' + token,
      ...(selection.resourceKey ? {
        'X-Goog-Drive-Resource-Keys': selection.id + '/' + selection.resourceKey,
      } : {}),
    },
  });
  if (!response.ok) {
    if (response.status === 403) {
      throw new Error('Không có quyền đọc Google Sheets. Kiểm tra quyền file và bật Google Sheets API.');
    }
    throw new Error('Không thể đọc Google Sheets. Hãy chọn lại bảng tính và thử lại.');
  }
  const bytes = await readBounded(response, 2 * 1024 * 1024, 'Tab Google Sheets quá lớn để nhập.');
  return JSON.parse(new TextDecoder().decode(bytes)) as T;
}

export async function listSheetTabs(selection: DriveCsvSelection, token: string, signal: AbortSignal) {
  const data = await requestSheets<{ sheets?: { properties: { title: string; sheetType: string } }[] }>(
    selection, '', new URLSearchParams({ fields: 'sheets(properties(title,sheetType))' }), token, signal,
  );
  return (data.sheets ?? []).filter(({ properties }) => properties.sheetType === 'GRID').map(({ properties }) => properties.title);
}

export async function downloadSheetTab(selection: DriveCsvSelection, title: string, token: string, signal: AbortSignal) {
  const range = "'" + title.replaceAll("'", "''") + "'";
  const data = await requestSheets<{ values?: string[][] }>(
    selection, '/values/' + encodeURIComponent(range),
    new URLSearchParams({ majorDimension: 'ROWS', valueRenderOption: 'FORMATTED_VALUE' }), token, signal,
  );
  const rows = data.values ?? [];
  const headerLength = rows.find((row) => row.some((cell) => String(cell).trim()))?.length ?? 0;
  const csv = rows.map((row) => Array.from({ length: Math.max(headerLength, row.length) }, (_, i) =>
    '"' + String(row[i] ?? '').replaceAll('"', '""') + '"').join(',')).join('\r\n');
  return new File([csv], selection.name + ' — ' + title + '.csv', { type: 'text/csv' });
}
