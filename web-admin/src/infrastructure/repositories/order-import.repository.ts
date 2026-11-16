import api from '@/lib/api';

export interface ImportAddress {
  deliveryAddress: string;
  deliveryLat: number | null;
  deliveryLng: number | null;
}

export interface ImportRow extends ImportAddress {
  line: number;
  externalOrderCode: string;
  recipientName: string;
  recipientPhone: string;
  timeWindowStart: string;
  timeWindowEnd: string;
  weightKg: number | null;
  codAmount: string | null;
  notes: string;
}

export interface ImportResult {
  line: number;
  externalOrderCode: string;
  status: 'CREATED' | 'NEEDS_ADDRESS' | 'DUPLICATE' | 'INVALID' | 'CANCELLED';
  message?: string;
}

export interface PendingAddress {
  externalOrderCode: string;
  recipientName: string;
  deliveryAddress: string | null;
}

function addressPayload(address: ImportAddress) {
  return {
    delivery_address: address.deliveryAddress,
    delivery_lat: address.deliveryLat,
    delivery_lng: address.deliveryLng,
  };
}

export const orderImportRepository = {
  async submit(rows: ImportRow[]) {
    const response = await api.post<(Omit<ImportResult, 'externalOrderCode'> & {
      external_order_code: string;
    })[]>('/orders/import', rows.map((row) => ({
      ...addressPayload(row),
      line: row.line,
      external_order_code: row.externalOrderCode,
      recipient_name: row.recipientName,
      recipient_phone: row.recipientPhone,
      time_window_start: row.timeWindowStart,
      time_window_end: row.timeWindowEnd,
      weight_kg: row.weightKg,
      cod_amount: row.codAmount,
      notes: row.notes,
    })));
    return response.data.map(({ external_order_code, ...row }) => ({
      ...row,
      externalOrderCode: external_order_code,
    }));
  },
  async pending(after: string) {
    const response = await api.get<{
      external_order_code: string;
      recipient_name: string;
      delivery_address: string | null;
    }[]>('/orders/import/pending', { params: { after } });
    return response.data.map((row): PendingAddress => ({
      externalOrderCode: row.external_order_code,
      recipientName: row.recipient_name,
      deliveryAddress: row.delivery_address,
    }));
  },
  async complete(externalOrderCode: string, address: ImportAddress) {
    await api.post('/orders/import/pending/complete', {
      external_order_code: externalOrderCode,
      address: addressPayload(address),
    });
  },
  async cancel(externalOrderCode: string) {
    const response = await api.post<boolean>('/orders/import/pending/cancel', {
      external_order_code: externalOrderCode,
    });
    return response.data;
  },
};
