'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { CheckCircle2, CircleDollarSign, Clock3, ReceiptText, Settings2 } from 'lucide-react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { DriverEarning, DriverSettlement, EarningPolicy, SettlementStatus } from '@/domain/entities/earning.entity';
import { earningRepository } from '@/infrastructure/repositories/earning.repository';

const money = new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 });
const dateTime = new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short' });

function monthRange() {
  const now = new Date();
  return {
    start: new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), 1)).toISOString(),
    end: new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth() + 1, 1)).toISOString(),
  };
}

export default function SettlementsPage() {
  const [earnings, setEarnings] = useState<DriverEarning[]>([]);
  const [settlements, setSettlements] = useState<DriverSettlement[]>([]);
  const [policy, setPolicy] = useState<EarningPolicy | null>(null);
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const [earningRows, settlementRows, currentPolicy] = await Promise.all([
        earningRepository.listEarnings(),
        earningRepository.listSettlements(),
        earningRepository.getPolicy(),
      ]);
      setEarnings(earningRows);
      setSettlements(settlementRows);
      setPolicy(currentPolicy);
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Không tải được dữ liệu đối soát');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    let active = true;
    Promise.all([
      earningRepository.listEarnings(),
      earningRepository.listSettlements(),
      earningRepository.getPolicy(),
    ]).then(([earningRows, settlementRows, currentPolicy]) => {
      if (active) {
        setEarnings(earningRows);
        setSettlements(settlementRows);
        setPolicy(currentPolicy);
      }
    }).catch((error) => {
      toast.error(error instanceof Error ? error.message : 'Không tải được dữ liệu đối soát');
    }).finally(() => {
      if (active) setLoading(false);
    });
    return () => { active = false; };
  }, []);

  const pendingByDriver = useMemo(() => {
    const grouped = new Map<string, { driverId: string; driverName: string; amount: number; count: number }>();
    earnings.filter((item) => item.status === 'PENDING' && !item.settlement_id).forEach((item) => {
      const current = grouped.get(item.driver_id) || { driverId: item.driver_id, driverName: item.driver_name, amount: 0, count: 0 };
      current.amount += item.net_amount;
      current.count += 1;
      grouped.set(item.driver_id, current);
    });
    return [...grouped.values()].sort((a, b) => b.amount - a.amount);
  }, [earnings]);

  const totals = useMemo(() => ({
    pending: earnings.filter((item) => item.status === 'PENDING').reduce((sum, item) => sum + item.net_amount, 0),
    approved: earnings.filter((item) => item.status === 'APPROVED').reduce((sum, item) => sum + item.net_amount, 0),
    paid: earnings.filter((item) => item.status === 'PAID').reduce((sum, item) => sum + item.net_amount, 0),
  }), [earnings]);

  async function createSettlement(driverId: string) {
    setBusyId(driverId);
    try {
      const range = monthRange();
      await earningRepository.createSettlement(driverId, range.start, range.end);
      toast.success('Đã lập kỳ đối soát');
      await load();
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Không thể lập đối soát');
    } finally { setBusyId(null); }
  }

  async function approve(id: string) {
    setBusyId(id);
    try {
      await earningRepository.approve(id);
      toast.success('Đã duyệt kỳ đối soát');
      await load();
    } catch (error) { toast.error(error instanceof Error ? error.message : 'Không thể duyệt'); }
    finally { setBusyId(null); }
  }

  async function markPaid(id: string) {
    const reference = window.prompt('Nhập mã tham chiếu thanh toán:');
    if (!reference?.trim()) return;
    setBusyId(id);
    try {
      await earningRepository.markPaid(id, reference.trim());
      toast.success('Đã xác nhận thanh toán');
      await load();
    } catch (error) { toast.error(error instanceof Error ? error.message : 'Không thể xác nhận thanh toán'); }
    finally { setBusyId(null); }
  }

  return (
    <div className="mx-auto max-w-7xl space-y-7">
      <header className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-950">Đối soát tài xế</h1>
          <p className="mt-1 max-w-2xl text-sm text-slate-600">Duyệt các khoản thu nhập đã hoàn tất và ghi nhận thanh toán theo kỳ. COD không được tính vào số tiền này.</p>
        </div>
        <Button variant="outline" onClick={() => void load()} disabled={loading}>Làm mới dữ liệu</Button>
      </header>

      <section className="grid overflow-hidden rounded-2xl border border-slate-200 bg-white sm:grid-cols-3">
        <SummaryCell icon={Clock3} label="Chờ đối soát" value={totals.pending} tone="amber" />
        <SummaryCell icon={CheckCircle2} label="Đã duyệt" value={totals.approved} tone="blue" />
        <SummaryCell icon={CircleDollarSign} label="Đã thanh toán" value={totals.paid} tone="green" />
      </section>

      <div className="grid gap-7 xl:grid-cols-[1fr_360px]">
        <div className="space-y-7">
          <section>
            <div className="mb-3 flex items-center justify-between">
              <h2 className="text-lg font-semibold text-slate-950">Sẵn sàng lập kỳ</h2>
              <span className="text-xs text-slate-500">Tháng hiện tại</span>
            </div>
            <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white">
              {loading ? <LoadingRows /> : pendingByDriver.length === 0 ? (
                <EmptyState text="Không có khoản thu nhập nào đang chờ lập kỳ." />
              ) : pendingByDriver.map((row) => (
                <div key={row.driverId} className="flex flex-col gap-3 border-b border-slate-100 px-5 py-4 last:border-b-0 sm:flex-row sm:items-center sm:justify-between">
                  <div>
                    <p className="font-semibold text-slate-900">{row.driverName}</p>
                    <p className="mt-1 text-sm text-slate-500">{row.count} nhiệm vụ đủ điều kiện</p>
                  </div>
                  <div className="flex items-center justify-between gap-5 sm:justify-end">
                    <p className="font-semibold tabular-nums text-slate-950">{money.format(row.amount)}</p>
                    <Button size="sm" isLoading={busyId === row.driverId} onClick={() => void createSettlement(row.driverId)}>Lập đối soát</Button>
                  </div>
                </div>
              ))}
            </div>
          </section>

          <section>
            <h2 className="mb-3 text-lg font-semibold text-slate-950">Các kỳ đối soát</h2>
            <div className="overflow-x-auto rounded-2xl border border-slate-200 bg-white">
              <table className="min-w-full text-sm">
                <thead className="bg-slate-50 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
                  <tr><th className="px-5 py-3">Tài xế</th><th className="px-5 py-3">Kỳ</th><th className="px-5 py-3">Tổng</th><th className="px-5 py-3">Trạng thái</th><th className="px-5 py-3 text-right">Thao tác</th></tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {settlements.map((item) => (
                    <tr key={item.id} className="text-slate-700">
                      <td className="px-5 py-4"><p className="font-medium text-slate-950">{item.driver_name}</p><p className="mt-0.5 text-xs text-slate-500">{item.earning_count} khoản</p></td>
                      <td className="whitespace-nowrap px-5 py-4 text-xs">{dateTime.format(new Date(item.period_start))}<br />{dateTime.format(new Date(item.period_end))}</td>
                      <td className="whitespace-nowrap px-5 py-4 font-semibold tabular-nums text-slate-950">{money.format(item.total_amount)}</td>
                      <td className="px-5 py-4"><StatusBadge status={item.status} /></td>
                      <td className="px-5 py-4 text-right">
                        {item.status === 'DRAFT' && <Button size="sm" variant="outline" isLoading={busyId === item.id} onClick={() => void approve(item.id)}>Duyệt</Button>}
                        {item.status === 'APPROVED' && <Button size="sm" isLoading={busyId === item.id} onClick={() => void markPaid(item.id)}>Xác nhận đã trả</Button>}
                        {item.status === 'PAID' && <span className="text-xs text-slate-500">{item.payment_reference}</span>}
                      </td>
                    </tr>
                  ))}
                  {!loading && settlements.length === 0 && <tr><td colSpan={5}><EmptyState text="Chưa có kỳ đối soát nào." /></td></tr>}
                </tbody>
              </table>
            </div>
          </section>
        </div>

        <PolicyPanel policy={policy} onSaved={load} />
      </div>
    </div>
  );
}

function SummaryCell({ icon: Icon, label, value, tone }: { icon: typeof Clock3; label: string; value: number; tone: 'amber' | 'blue' | 'green' }) {
  const colors = { amber: 'bg-amber-50 text-amber-700', blue: 'bg-blue-50 text-blue-700', green: 'bg-emerald-50 text-emerald-700' };
  return <div className="flex items-center gap-4 border-b border-slate-200 p-5 last:border-0 sm:border-b-0 sm:border-r">
    <div className={`flex h-10 w-10 items-center justify-center rounded-xl ${colors[tone]}`}><Icon size={20} /></div>
    <div><p className="text-xs font-medium text-slate-500">{label}</p><p className="mt-1 text-lg font-bold tabular-nums text-slate-950">{money.format(value)}</p></div>
  </div>;
}

function StatusBadge({ status }: { status: SettlementStatus }) {
  const meta = { DRAFT: ['Chờ duyệt', 'bg-amber-50 text-amber-700'], APPROVED: ['Đã duyệt', 'bg-blue-50 text-blue-700'], PAID: ['Đã trả', 'bg-emerald-50 text-emerald-700'], CANCELLED: ['Đã hủy', 'bg-slate-100 text-slate-600'] }[status];
  return <span className={`inline-flex rounded-full px-2.5 py-1 text-xs font-semibold ${meta[1]}`}>{meta[0]}</span>;
}

function PolicyPanel({ policy, onSaved }: { policy: EarningPolicy | null; onSaved: () => Promise<void> }) {
  const [prevPolicy, setPrevPolicy] = useState(policy);
  const [form, setForm] = useState<EarningPolicy | null>(policy);
  const [saving, setSaving] = useState(false);

  if (policy !== prevPolicy) {
    setPrevPolicy(policy);
    setForm(policy);
  }
  if (!form) return <aside className="h-80 animate-pulse rounded-2xl bg-slate-100" />;
  const field = (key: keyof EarningPolicy, label: string, suffix: string) => <label className="block text-xs font-semibold text-slate-700">{label}<div className="mt-1 flex rounded-lg border border-slate-300 bg-white focus-within:border-blue-500 focus-within:ring-2 focus-within:ring-blue-100"><input type="number" min="0" value={form[key]} onChange={(event) => setForm({ ...form, [key]: Number(event.target.value) })} className="min-w-0 flex-1 rounded-lg px-3 py-2 text-sm outline-none" /><span className="border-l border-slate-200 px-3 py-2 text-xs text-slate-500">{suffix}</span></div></label>;
  async function save() {
    setSaving(true);
    try {
      await earningRepository.updatePolicy({ base_amount: form!.base_amount, per_km_amount: form!.per_km_amount, minimum_amount: form!.minimum_amount, heavy_weight_threshold_kg: form!.heavy_weight_threshold_kg, heavy_bonus_amount: form!.heavy_bonus_amount });
      toast.success('Đã cập nhật chính sách thu nhập');
      await onSaved();
    } catch (error) { toast.error(error instanceof Error ? error.message : 'Không thể lưu chính sách'); }
    finally { setSaving(false); }
  }
  return <aside className="self-start rounded-2xl border border-slate-200 bg-white p-5 xl:sticky xl:top-0">
    <div className="flex items-center gap-3"><div className="flex h-9 w-9 items-center justify-center rounded-xl bg-slate-100 text-slate-700"><Settings2 size={18} /></div><div><h2 className="font-semibold text-slate-950">Chính sách thu nhập</h2><p className="text-xs text-slate-500">Áp dụng cho nhiệm vụ mới</p></div></div>
    <div className="mt-5 space-y-4">{field('base_amount', 'Phí cơ bản', 'đ')}{field('per_km_amount', 'Đơn giá khoảng cách', 'đ/km')}{field('minimum_amount', 'Mức tối thiểu', 'đ')}{field('heavy_weight_threshold_kg', 'Ngưỡng hàng nặng', 'kg')}{field('heavy_bonus_amount', 'Phụ phí hàng nặng', 'đ')}</div>
    <div className="mt-5 rounded-xl bg-slate-50 p-3 text-xs leading-5 text-slate-600"><ReceiptText className="mb-2 h-4 w-4 text-slate-500" />Tiền tài xế = phí cơ bản + khoảng cách × đơn giá + phụ phí, sau đó áp dụng mức tối thiểu. Mỗi khoản giữ nguyên công thức lúc ghi nhận.</div>
    <Button className="mt-5 w-full" isLoading={saving} onClick={() => void save()}>Lưu chính sách</Button>
  </aside>;
}

function LoadingRows() { return <div className="divide-y divide-slate-100">{[1, 2, 3].map((item) => <div key={item} className="h-20 animate-pulse bg-slate-50" />)}</div>; }
function EmptyState({ text }: { text: string }) { return <div className="px-6 py-12 text-center text-sm text-slate-500">{text}</div>; }
