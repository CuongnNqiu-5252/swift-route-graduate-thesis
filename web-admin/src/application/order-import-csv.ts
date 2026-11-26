import type { OrderPriority } from '@/domain/entities/order.entity';
import type { ImportResult, ImportRow } from '@/infrastructure/repositories/order-import.repository';

import { CSV_MAX_BYTES } from './order-import-limits.mjs';

export async function readOrderCsv(file: File) {
  if (file.size > CSV_MAX_BYTES) throw new Error('File CSV vượt quá 512 KB.');
  let text: string;
  try {
    text = new TextDecoder('utf-8', { fatal: true }).decode(await file.arrayBuffer());
  } catch {
    throw new Error('Lưu file CSV bằng mã hóa UTF-8 rồi thử lại.');
  }
  return { name: file.name, ...parseOrderCsv(text) };
}

const required = ['externalOrderCode', 'recipientName', 'recipientPhone', 'weightKg'];
const columns = [...required, 'timeWindowStart', 'timeWindowEnd', 'deliveryAddress', 'deliveryLat', 'deliveryLng', 'codAmount', 'notes', 'priority'];

function records(csv: string) {
  const input = csv.replace(/^\uFEFF/, '');
  const field = /(?:"((?:[^"]|"")*)"|([^",\r\n]*))(,|\r\n|\n|\r|$)/y;
  const output: { line: number; cells: string[] }[] = [];
  let line = 1;
  let startLine = line;
  let cells: string[] = [];
  let cursor = 0;
  while (cursor < input.length) {
    field.lastIndex = cursor;
    const match = field.exec(input);
    if (!match || !match[0].length) throw new Error('CSV có dấu ngoặc kép sai định dạng.');
    cursor = field.lastIndex;
    cells.push((match[1] !== undefined ? match[1].replace(/""/g, '"') : match[2]).trim());
    line += (match[0].match(/\r\n|\r|\n/g) ?? []).length;
    if (match[3] === ',' && cursor < input.length) continue;
    if (match[3] === ',') cells.push('');
    if (cells.some(Boolean)) output.push({ line: startLine, cells });
    if (output.length > 201) throw new Error('Mỗi file chỉ được chứa tối đa 200 dòng vận đơn.');
    startLine = line;
    cells = [];
  }
  return output;
}

export function parseOrderCsv(csv: string) {
  const [header, ...data] = records(csv);
  if (!header || !data.length) throw new Error('File CSV không có dòng vận đơn.');
  const names = header.cells;
  if (new Set(names).size !== names.length || required.some((name) => !names.includes(name))
      || names.some((name) => !columns.includes(name))) {
    throw new Error('Tên cột CSV chưa đúng. Tải file mẫu để xem các cột hỗ trợ.');
  }
  const rows: ImportRow[] = [];
  const errors: ImportResult[] = [];
  for (const { line, cells } of data) {
    const value = (name: string) => cells[names.indexOf(name)] ?? '';
    const externalOrderCode = value('externalOrderCode');
    const number = (name: string) => {
      const raw = value(name);
      if (!raw) return null;
      if (!/^[+-]?\d+(?:\.\d+)?$/.test(raw) || !Number.isFinite(Number(raw))) throw new Error(name);
      return Number(raw);
    };
    const time = (name: string) => {
      const raw = value(name);
      if (!raw) return null;
      const validShape = /^\d{4}-\d\d-\d\dT(?:[01]\d|2[0-3]):[0-5]\d(?::[0-5]\d)?(?:Z|[+-](?:0\d|1[0-8]):[0-5]\d)$/.test(raw);
      const day = new Date(raw.slice(0, 10));
      if (!validShape || !Number.isFinite(Date.parse(raw)) || !Number.isFinite(day.valueOf())
          || day.toISOString().slice(0, 10) !== raw.slice(0, 10)) throw new Error(name);
      return raw;
    };
    try {
      if (cells.length !== names.length) throw new Error('Số cột không khớp');
      const missing = required.find((name) => !value(name));
      if (missing) throw new Error(missing);
      const codAmount = value('codAmount');
      if (codAmount && !/^\d{1,12}$/.test(codAmount)) throw new Error('codAmount');
      const deliveryLat = number('deliveryLat');
      const deliveryLng = number('deliveryLng');
      const validPoint = deliveryLat !== null && deliveryLng !== null
        && Math.abs(deliveryLat) <= 90 && Math.abs(deliveryLng) <= 180;
      const timeWindowStart = time('timeWindowStart');
      const timeWindowEnd = time('timeWindowEnd');
      if ((timeWindowStart === null) !== (timeWindowEnd === null)
          || (timeWindowStart && timeWindowEnd && Date.parse(timeWindowEnd) <= Date.parse(timeWindowStart))) {
        throw new Error('Chọn đủ giờ hẹn và đặt giờ kết thúc sau giờ bắt đầu.');
      }
      const rawPriority = value('priority').toUpperCase();
      let priority: OrderPriority = 'NORMAL';
      if (rawPriority) {
        if (rawPriority === 'NORMAL' || rawPriority === 'BÌNH THƯỜNG' || rawPriority === 'BINH THUONG') {
          priority = 'NORMAL';
        } else if (rawPriority === 'HIGH' || rawPriority === 'CAO' || rawPriority === 'ƯU TIÊN CAO' || rawPriority === 'UU TIEN CAO') {
          priority = 'HIGH';
        } else if (rawPriority === 'URGENT' || rawPriority === 'KHẨN CẤP' || rawPriority === 'KHAN CAP' || rawPriority === 'KHẨN' || rawPriority === 'KHAN') {
          priority = 'URGENT';
        } else {
          throw new Error('priority');
        }
      }
      rows.push({
        line, externalOrderCode, recipientName: value('recipientName'), recipientPhone: value('recipientPhone'),
        deliveryAddress: value('deliveryAddress'), deliveryLat: validPoint ? deliveryLat : null,
        deliveryLng: validPoint ? deliveryLng : null, timeWindowStart,
        timeWindowEnd, weightKg: number('weightKg'), codAmount: codAmount || null,
        notes: value('notes'), priority,
      });
    } catch (error) {
      errors.push({ line, externalOrderCode, status: 'INVALID', message: (error as Error).message });
    }
  }
  return { rows, errors };
}
