'use client';

import { useState } from 'react';
import { 
  FlaskConical, 
  Minus, 
  Plus, 
  Clock, 
  Route, 
  AlertTriangle, 
  CheckCircle2, 
  TrendingDown, 
  TrendingUp, 
  Users, 
  Sparkles,
  ArrowRight
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { OptimizationStop, WhatIfResult } from '@/domain/entities/routing.entity';

interface WhatIfComparisonProps {
  stops: OptimizationStop[];
  onRun: (baselineDrivers: number, scenarioDrivers: number) => Promise<WhatIfResult>;
}

export function WhatIfComparison({ stops, onRun }: WhatIfComparisonProps) {
  const [baselineDrivers, setBaselineDrivers] = useState(2);
  const [scenarioDrivers, setScenarioDrivers] = useState(3);
  const [result, setResult] = useState<WhatIfResult | null>(null);
  const [isRunning, setIsRunning] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const update = (type: 'baseline' | 'scenario', delta: number) => {
    const current = type === 'baseline' ? baselineDrivers : scenarioDrivers;
    const maxVal = Math.max(stops.length, 5);
    const next = Math.min(Math.max(current + delta, 1), maxVal);
    if (type === 'baseline') setBaselineDrivers(next);
    else setScenarioDrivers(next);
  };

  const run = async () => {
    if (!stops.length) return;
    setIsRunning(true);
    setError(null);
    try {
      const data = await onRun(baselineDrivers, scenarioDrivers);
      setResult(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không thể chạy mô phỏng.');
    } finally {
      setIsRunning(false);
    }
  };

  // Helper for recommendation text
  const getRecommendation = (res: WhatIfResult) => {
    const timeSaved = res.baseline.completion_minutes - res.scenario.completion_minutes;
    const percentTime = Math.abs(res.completion_delta_percent);

    if (res.scenario.driver_count > res.baseline.driver_count) {
      if (timeSaved > 0) {
        return `Tăng từ ${res.baseline.driver_count} lên ${res.scenario.driver_count} tài xế giúp rút ngắn ${timeSaved.toFixed(0)} phút (${percentTime.toFixed(1)}%) thời gian giao, giảm áp lực dồn đơn giờ cao điểm.`;
      }
      return `Tăng số lượng tài xế không làm giảm đáng kể thời gian ca do số điểm dừng (${stops.length} đơn) phân tán hẹp. Có thể duy trì mức ${res.baseline.driver_count} tài xế để tối ưu chi phí.`;
    } else if (res.scenario.driver_count < res.baseline.driver_count) {
      return `Giảm xuống ${res.scenario.driver_count} tài xế giúp tiết kiệm nhân lực ca trực, tuy nhiên thời gian hoàn thành ca sẽ kéo dài thêm ${Math.abs(timeSaved).toFixed(0)} phút. Cần theo dõi sát cam kết SLA của các đơn hẹn giờ.`;
    }
    return `Hai kịch bản có cùng số lượng tài xế (${res.baseline.driver_count}), thông số hoàn thành ca không có biến động.`;
  };

  return (
    <div className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm space-y-5">
      {/* Header */}
      <div className="flex flex-wrap items-start justify-between gap-4 border-b border-gray-100 pb-4">
        <div>
          <div className="flex items-center gap-2">
            <h2 className="text-base font-bold text-gray-900 flex items-center gap-2">
              <FlaskConical className="text-purple-600" size={20} />
              Mô phỏng Định lượng Năng lực Ca (What-If Simulation)
            </h2>
            <span className="rounded-full bg-purple-50 px-2.5 py-0.5 text-[11px] font-semibold text-purple-700 border border-purple-200">
              Mô phỏng Toán học Dry-Run
            </span>
          </div>
          <p className="mt-1 text-xs text-gray-500">
            Giả lập và so sánh KPI khi thay đổi số lượng tài xế cho tập {stops.length} đơn hàng đã chọn (không lưu vào cơ sở dữ liệu).
          </p>
        </div>

        <Button
          size="sm"
          onClick={run}
          isLoading={isRunning}
          disabled={!stops.length}
          className="bg-purple-600 hover:bg-purple-700 text-white font-semibold"
        >
          <Sparkles size={14} /> Chạy mô phỏng so sánh
        </Button>
      </div>

      {/* Driver Controls */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4 max-w-2xl">
        {/* Baseline Card */}
        <div className="rounded-lg border border-blue-200 bg-blue-50/40 p-4">
          <div className="flex items-center justify-between text-xs font-semibold text-blue-900 mb-2">
            <span className="flex items-center gap-1.5">
              <Users size={14} className="text-blue-600" /> Kịch bản 1 (Hiện tại)
            </span>
            <span className="text-[11px] font-normal text-blue-700">Baseline</span>
          </div>
          <div className="flex items-center justify-between mt-3">
            <button
              aria-label="Giảm tài xế hiện tại"
              onClick={() => update('baseline', -1)}
              className="h-8 w-8 rounded-lg border border-blue-200 bg-white flex items-center justify-center text-blue-700 hover:bg-blue-50 transition"
            >
              <Minus size={14} />
            </button>
            <div className="text-center">
              <span className="text-2xl font-black text-gray-900">{baselineDrivers}</span>
              <span className="ml-1 text-xs text-gray-500">tài xế</span>
              <p className="text-[11px] text-gray-400 mt-0.5">
                ~{(stops.length / baselineDrivers).toFixed(1)} đơn / người
              </p>
            </div>
            <button
              aria-label="Tăng tài xế hiện tại"
              onClick={() => update('baseline', 1)}
              className="h-8 w-8 rounded-lg border border-blue-200 bg-white flex items-center justify-center text-blue-700 hover:bg-blue-50 transition"
            >
              <Plus size={14} />
            </button>
          </div>
        </div>

        {/* Scenario Card */}
        <div className="rounded-lg border border-purple-200 bg-purple-50/40 p-4">
          <div className="flex items-center justify-between text-xs font-semibold text-purple-900 mb-2">
            <span className="flex items-center gap-1.5">
              <Sparkles size={14} className="text-purple-600" /> Kịch bản 2 (Giả định)
            </span>
            <span className="text-[11px] font-normal text-purple-700">Scenario</span>
          </div>
          <div className="flex items-center justify-between mt-3">
            <button
              aria-label="Giảm tài xế giả định"
              onClick={() => update('scenario', -1)}
              className="h-8 w-8 rounded-lg border border-purple-200 bg-white flex items-center justify-center text-purple-700 hover:bg-purple-50 transition"
            >
              <Minus size={14} />
            </button>
            <div className="text-center">
              <span className="text-2xl font-black text-purple-900">{scenarioDrivers}</span>
              <span className="ml-1 text-xs text-purple-600">tài xế</span>
              <p className="text-[11px] text-gray-400 mt-0.5">
                ~{(stops.length / scenarioDrivers).toFixed(1)} đơn / người
              </p>
            </div>
            <button
              aria-label="Tăng tài xế giả định"
              onClick={() => update('scenario', 1)}
              className="h-8 w-8 rounded-lg border border-purple-200 bg-white flex items-center justify-center text-purple-700 hover:bg-purple-50 transition"
            >
              <Plus size={14} />
            </button>
          </div>
        </div>
      </div>

      {!stops.length && (
        <div className="rounded-lg bg-gray-50 p-4 text-center text-xs text-gray-500">
          Vui lòng chọn ít nhất 1 đơn hàng ở bảng trên để thực hiện mô phỏng năng lực ca.
        </div>
      )}

      {error && (
        <p className="text-xs text-red-600 bg-red-50 p-3 rounded-lg border border-red-200">
          {error}
        </p>
      )}

      {/* Visual KPI Comparison Cards */}
      {result && (
        <div className="space-y-4 pt-2">
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            {/* KPI 1: Completion Time */}
            <Card className="border border-gray-200 shadow-none bg-gradient-to-br from-white to-gray-50/50">
              <CardContent className="p-4">
                <div className="flex items-center justify-between text-gray-500 text-xs">
                  <span className="flex items-center gap-1 font-medium">
                    <Clock size={14} className="text-blue-600" /> Thời gian hoàn ca
                  </span>
                  <span className="text-[10px] text-gray-400">max(duration)</span>
                </div>

                <div className="mt-3 flex items-baseline justify-between">
                  <div className="flex items-center gap-2">
                    <span className="text-xl font-bold text-gray-700">
                      {result.baseline.completion_minutes.toFixed(0)}m
                    </span>
                    <ArrowRight size={14} className="text-gray-400" />
                    <span className="text-2xl font-black text-gray-900">
                      {result.scenario.completion_minutes.toFixed(0)}m
                    </span>
                  </div>

                  <span
                    className={`inline-flex items-center gap-1 px-2 py-0.5 rounded text-xs font-bold ${
                      result.completion_delta_percent <= 0
                        ? 'bg-emerald-100 text-emerald-800'
                        : 'bg-amber-100 text-amber-800'
                    }`}
                  >
                    {result.completion_delta_percent <= 0 ? (
                      <TrendingDown size={12} />
                    ) : (
                      <TrendingUp size={12} />
                    )}
                    {result.completion_delta_percent > 0 ? '+' : ''}
                    {result.completion_delta_percent.toFixed(1)}%
                  </span>
                </div>

                {/* Progress bar comparison */}
                <div className="mt-3 space-y-1">
                  <div className="flex justify-between text-[10px] text-gray-400">
                    <span>Baseline ({result.baseline.driver_count} TX)</span>
                    <span>Scenario ({result.scenario.driver_count} TX)</span>
                  </div>
                  <div className="h-1.5 w-full bg-gray-100 rounded-full overflow-hidden flex">
                    <div
                      className="bg-blue-500 h-full transition-all"
                      style={{
                        width: `${Math.min(
                          100,
                          (result.scenario.completion_minutes /
                            Math.max(result.baseline.completion_minutes, result.scenario.completion_minutes)) *
                            100
                        )}%`,
                      }}
                    />
                  </div>
                </div>
              </CardContent>
            </Card>

            {/* KPI 2: Total Distance */}
            <Card className="border border-gray-200 shadow-none bg-gradient-to-br from-white to-gray-50/50">
              <CardContent className="p-4">
                <div className="flex items-center justify-between text-gray-500 text-xs">
                  <span className="flex items-center gap-1 font-medium">
                    <Route size={14} className="text-emerald-600" /> Tổng quãng đường
                  </span>
                  <span className="text-[10px] text-gray-400">sum(distances)</span>
                </div>

                <div className="mt-3 flex items-baseline justify-between">
                  <div className="flex items-center gap-2">
                    <span className="text-xl font-bold text-gray-700">
                      {result.baseline.total_distance_km.toFixed(1)} km
                    </span>
                    <ArrowRight size={14} className="text-gray-400" />
                    <span className="text-2xl font-black text-gray-900">
                      {result.scenario.total_distance_km.toFixed(1)} km
                    </span>
                  </div>

                  <span
                    className={`inline-flex items-center gap-1 px-2 py-0.5 rounded text-xs font-bold ${
                      result.distance_delta_percent <= 0
                        ? 'bg-emerald-100 text-emerald-800'
                        : 'bg-gray-100 text-gray-700'
                    }`}
                  >
                    {result.distance_delta_percent > 0 ? '+' : ''}
                    {result.distance_delta_percent.toFixed(1)}%
                  </span>
                </div>

                <div className="mt-3 text-[11px] text-gray-400">
                  {result.scenario.driver_count > 1 ? (
                    <span>Phân bổ thành {result.scenario.driver_count} cung đường con song song</span>
                  ) : (
                    <span>1 cung đường xuyên suốt cho toàn bộ đơn</span>
                  )}
                </div>
              </CardContent>
            </Card>

            {/* KPI 3: Late Risk SLA */}
            <Card className="border border-gray-200 shadow-none bg-gradient-to-br from-white to-gray-50/50">
              <CardContent className="p-4">
                <div className="flex items-center justify-between text-gray-500 text-xs">
                  <span className="flex items-center gap-1 font-medium">
                    <AlertTriangle size={14} className="text-amber-500" /> Rủi ro trễ hẹn SLA
                  </span>
                  <span className="text-[10px] text-gray-400">&gt; Max Shift</span>
                </div>

                <div className="mt-3 flex items-baseline justify-between">
                  <div className="flex items-center gap-2">
                    <span className="text-xl font-bold text-gray-700">
                      {result.baseline.late_risk_orders} đơn
                    </span>
                    <ArrowRight size={14} className="text-gray-400" />
                    <span className={`text-2xl font-black ${
                      result.scenario.late_risk_orders === 0 ? 'text-emerald-600' : 'text-red-600'
                    }`}>
                      {result.scenario.late_risk_orders} đơn
                    </span>
                  </div>

                  {result.scenario.late_risk_orders === 0 ? (
                    <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-xs font-semibold bg-emerald-100 text-emerald-800">
                      <CheckCircle2 size={12} /> Đảm bảo SLA
                    </span>
                  ) : (
                    <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-xs font-semibold bg-red-100 text-red-800">
                      {result.late_risk_delta > 0 ? `+${result.late_risk_delta}` : result.late_risk_delta} đơn
                    </span>
                  )}
                </div>

                <div className="mt-3 text-[11px] text-gray-400">
                  {result.scenario.late_risk_orders === 0
                    ? 'Tất cả các điểm giao đều hoàn thành trước giới hạn ca'
                    : 'Cảnh báo: Có đơn hàng dự kiến hoàn thành sau giờ hẹn'}
                </div>
              </CardContent>
            </Card>
          </div>

          {/* Smart Recommendation Banner */}
          <div className="rounded-lg bg-indigo-50 border border-indigo-100 p-4 flex items-start gap-3 text-xs text-indigo-900">
            <Sparkles className="shrink-0 text-indigo-600 mt-0.5" size={16} />
            <div>
              <p className="font-semibold text-indigo-950 mb-0.5">Nhận định & Khuyến nghị Điều phối:</p>
              <p className="text-indigo-800 leading-relaxed">{getRecommendation(result)}</p>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
