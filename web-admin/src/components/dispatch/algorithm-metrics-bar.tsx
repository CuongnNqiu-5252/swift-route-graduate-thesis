import { Card, CardContent } from '@/components/ui/card';
import { OptimizationResult } from '@/domain/entities/routing.entity';
import { Cpu, Clock, Navigation, CheckCircle2 } from 'lucide-react';

interface AlgorithmMetricsBarProps {
  result: OptimizationResult;
}

const ALGORITHM_NAMES: Record<string, string> = {
  ortools: 'Google OR-Tools (Tối ưu hóa công nghiệp)',
  auto: 'Tự động (Google OR-Tools)',
};

export function AlgorithmMetricsBar({ result }: AlgorithmMetricsBarProps) {
  const algoDisplay = ALGORITHM_NAMES[result.algorithm_used] || result.algorithm_used;

  return (
    <Card className="border-blue-100 bg-gradient-to-r from-blue-50/50 via-indigo-50/30 to-blue-50/50 shadow-sm">
      <CardContent className="p-4">
        <div className="flex flex-wrap items-center justify-between gap-4">
          {/* Algorithm Badge */}
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-blue-600 text-white shadow">
              <Cpu size={20} />
            </div>
            <div>
              <div className="flex items-center gap-1.5">
                <span className="text-xs font-semibold uppercase tracking-wider text-blue-700">
                  Thuật toán sử dụng
                </span>
                <span className="inline-flex items-center gap-1 rounded bg-blue-100 px-1.5 py-0.5 text-[10px] font-bold text-blue-800">
                  <CheckCircle2 size={10} /> Đã tối ưu
                </span>
              </div>
              <p className="text-sm font-bold text-gray-900">{algoDisplay}</p>
            </div>
          </div>

          {/* Metrics Grid */}
          <div className="flex flex-wrap items-center gap-6">
            <div className="flex items-center gap-2.5">
              <div className="flex h-8 w-8 items-center justify-center rounded-md bg-amber-100 text-amber-700">
                <Clock size={16} />
              </div>
              <div>
                <p className="text-xs text-gray-500">Thời gian tính toán</p>
                <p className="text-sm font-bold text-gray-900">
                  {result.execution_time_ms < 1
                    ? `${(result.execution_time_ms * 1000).toFixed(0)} µs`
                    : `${result.execution_time_ms.toFixed(2)} ms`}
                </p>
              </div>
            </div>

            <div className="h-8 w-px bg-gray-200" />

            <div className="flex items-center gap-2.5">
              <div className="flex h-8 w-8 items-center justify-center rounded-md bg-emerald-100 text-emerald-700">
                <Navigation size={16} />
              </div>
              <div>
                <p className="text-xs text-gray-500">Tổng quãng đường</p>
                <p className="text-sm font-bold text-emerald-700">
                  {result.total_distance_km.toFixed(2)} km
                </p>
              </div>
            </div>

            <div className="h-8 w-px bg-gray-200" />

            <div>
              <p className="text-xs text-gray-500">Ước tính thời gian giao</p>
              <p className="text-sm font-bold text-gray-900">
                ~{result.total_duration_min.toFixed(0)} phút ({result.ordered_stops.length} điểm)
              </p>
            </div>
          </div>
        </div>
      </CardContent>
    </Card>
  );
}
