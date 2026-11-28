'use client';

import { useAuth } from '@/application/contexts/auth-context';
import { FLOW_PATHS, flowPath, isRunnableFlow, type FlowKind, type FlowStep } from '@/application/operation-flow-graph';
import { readOrderCsv } from '@/application/order-import-csv';
import { AccessDeniedNotice } from '@/components/auth/permission-guard';
import { DriveCsvPicker, type GoogleDriveSession } from '@/components/orders/drive-csv-picker';
import { OrderImportPanel } from '@/components/orders/order-import-panel';
import { Button } from '@/components/ui/button';
import { CustomSelect } from '@/components/ui/select';
import type { PickupLocation } from '@/domain/entities/pickup-location.entity';
import { useLocale } from '@/i18n/provider';
import { operationFlowRepository, type PlanningActivation } from '@/infrastructure/repositories/operation-flow.repository';
import { pickupLocationRepository } from '@/infrastructure/repositories/pickup-location.repository';
import * as Dialog from '@radix-ui/react-dialog';
import {
  addEdge, Background, Controls, Handle, Position, ReactFlow, useEdgesState, useNodesState, useStore,
  type Connection,
  type Edge, type Node, type NodeProps,
  type ReactFlowInstance
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import { CalendarClock, FileSpreadsheet, GitBranch, PackagePlus, Play, Plus, Power, Route, Save, Trash2, Undo2, Warehouse, X } from 'lucide-react';
import Link from 'next/link';
import { createContext, memo, useContext, useEffect, useRef, useState } from 'react';
import { toast } from 'sonner';
import { z } from 'zod';
import { PlanningRunHistory } from './planning-run-history';

const catalog = {
  schedule: { label: 'Lịch chạy', detail: 'Đặt giờ kích hoạt workflow.', icon: CalendarClock },
  prepare: { label: 'Chuẩn bị CSV từ Drive', detail: 'Chọn file Drive và kiểm tra dữ liệu.', icon: FileSpreadsheet },
  import: { label: 'Nhập vận đơn', detail: 'Tạo vận đơn sau khi xác nhận bản xem trước.', icon: PackagePlus },
  'select-orders': { label: 'Lấy đơn chờ điều phối', detail: 'Chọn điểm lấy hàng để giới hạn đơn đưa vào lập tuyến.', icon: Warehouse },
  plan: { label: 'Lập kế hoạch giao hàng', detail: 'Lập kế hoạch cho đơn đủ điều kiện của doanh nghiệp.', icon: Route },
} as const;

const kindLabels = { import: 'Luồng nhập vận đơn', planning: 'Luồng lập kế hoạch giao hàng' } as const;
const stepColors: Record<FlowStep, string> = {
  schedule: 'border-teal-200 bg-teal-50 text-teal-800',
  prepare: 'border-sky-200 bg-sky-50 text-sky-800',
  import: 'border-indigo-200 bg-indigo-50 text-indigo-800',
  'select-orders': 'border-slate-200 bg-slate-50 text-slate-800',
  plan: 'border-amber-200 bg-amber-50 text-amber-900',
};
const draftSchema = z.object({
  nodes: z.array(z.object({
    id: z.string(),
    step: z.enum(['schedule', 'prepare', 'import', 'select-orders', 'plan']),
    position: z.object({ x: z.number(), y: z.number() }),
  })).max(4),
  edges: z.array(z.object({ id: z.string(), source: z.string(), target: z.string() })).max(3),
  schedule: z.object({
    mode: z.enum(['daily', 'interval']),
    time: z.string().regex(/^(?:[01]\d|2[0-3]):[0-5]\d$/),
    hours: z.number().int().min(1).max(168),
  }),
  resolveCoordinates: z.boolean().default(true),
  stopOnCsvErrors: z.boolean().default(false),
  pickupLocationId: z.uuid().nullable().default(null),
});
type Draft = z.infer<typeof draftSchema>;
type SharedDraft = { revision: number; draft: Draft };

function decodeSharedDraft(value: { revision: number; draft: Record<string, unknown> } | null): SharedDraft | null {
  if (!value) return null;
  return {
    revision: value.revision, draft: draftSchema.parse({
      ...value.draft,
      resolveCoordinates: value.draft.resolve_coordinates,
      stopOnCsvErrors: value.draft.stop_on_csv_errors,
      pickupLocationId: value.draft.pickup_location_id,
    })
  };
}
type CanvasNode = Node<{ step: FlowStep }, 'workflow'>;
const NODE_MIME = 'application/x-swiftroute-flow-node';
const SelectNextStep = createContext<(step: FlowStep) => void>(() => { });

const WorkflowNode = memo(function WorkflowNode({ data, id, selected }: NodeProps<CanvasNode>) {
  const { t } = useLocale();
  const selectNext = useContext(SelectNextStep);
  const connected = useStore((state) => state.edges.some((edge) => edge.source === id));
  const canInsertFilter = useStore((state) => data.step === 'schedule'
    && !state.nodes.some((node) => node.id === 'select-orders')
    && state.edges.some((edge) => edge.source === id && edge.target === 'plan'));
  const { label, icon: Icon } = catalog[data.step];
  const hasOutput = data.step === 'schedule' || data.step === 'prepare' || data.step === 'select-orders';
  return (
    <div className={`relative flex min-h-14 w-[184px] items-center gap-2 rounded-xl border bg-white px-2.5 py-2 text-xs font-semibold text-slate-900 shadow-sm ${selected ? 'border-teal-600 ring-2 ring-teal-100' : 'border-slate-300'}`}>
      {data.step !== 'schedule' && <Handle type="target" position={Position.Left} />}
      <span className={`grid size-8 shrink-0 place-items-center rounded-sm border ${stepColors[data.step]}`}>
        <Icon size={16} aria-hidden />
      </span>
      <span className="flex-1">{t(label)}</span>
      {hasOutput && (!connected || canInsertFilter) && (
        <button type="button" aria-label={`${t('Thêm bước tiếp theo')}: ${t(label)}`}
          className="nodrag nopan shrink-0 rounded border border-slate-300 bg-white p-1 hover:bg-slate-100 focus-visible:outline-2"
          onClick={(event) => { event.stopPropagation(); selectNext(data.step); }}>
          <Plus size={13} aria-hidden />
        </button>
      )}
      {hasOutput && <Handle type="source" position={Position.Right} />}
    </div>
  );
});

const nodeTypes = { workflow: WorkflowNode };
const edgeDefaults = { style: { stroke: '#64748b', strokeWidth: 1.8 } };

function template(kind: FlowKind) {
  const steps = flowPath(kind, false);
  return {
    nodes: steps.map((step, i) => ({ id: step, step, position: { x: i * 244, y: 64 } })),
    edges: steps.slice(1).map((target, i) => ({ id: steps[i] + '-' + target, source: steps[i], target })),
  };
}

function makeNode(step: FlowStep, position: { x: number; y: number }): CanvasNode {
  return {
    id: step,
    position,
    data: { step },
    type: 'workflow',
  };
}

function loadDraft(key: string, kind: FlowKind): Draft {
  try {
    const stored = localStorage.getItem(key);
    const parsed = draftSchema.safeParse(stored && JSON.parse(stored));
    if (parsed.success && parsed.data.nodes.every((node) => node.id === node.step
      && FLOW_PATHS[kind].some((step) => step === node.step))
      && new Set(parsed.data.nodes.map((node) => node.id)).size === parsed.data.nodes.length
      && parsed.data.edges.every((edge) => parsed.data.nodes.some((node) => node.id === edge.source)
        && parsed.data.nodes.some((node) => node.id === edge.target))) return parsed.data;
  } catch {
    // A draft can be unavailable when the browser blocks local storage.
  }
  return {
    ...template(kind),
    schedule: { mode: 'daily', time: '06:00', hours: 3 },
    resolveCoordinates: true,
    stopOnCsvErrors: false,
    pickupLocationId: null,
  };
}

export default function OperationFlowPage() {
  const { t } = useLocale();
  const { user, hasPermission } = useAuth();
  const canImport = hasPermission('order:create');
  const canPlan = hasPermission('dispatch:execute');
  const [selectedFlow, setSelectedFlow] = useState<FlowKind>('import');
  if (!user || (!canImport && !canPlan)) {
    return <AccessDeniedNotice />;
  }
  const kind = selectedFlow === 'import' && canImport || !canPlan ? 'import' : 'planning';
  const draftKey = ['swiftroute-flow-v1', user.tenantId, user.id, kind].join(':');
  return (
    <div className="min-w-0 space-y-4">
      <header className="flex flex-wrap items-center justify-between gap-4">
        <div className="flex items-center gap-3">
          <span className="grid size-10 shrink-0 place-items-center rounded-xl border border-teal-200 bg-teal-50 text-teal-800">
            <GitBranch size={20} aria-hidden />
          </span>
          <div>
            <h1 className="text-2xl font-bold tracking-tight text-slate-950">{t('Luồng vận hành')}</h1>
            <p className="mt-1 text-sm text-slate-600">{t('Thêm node, nối các bước và chạy thử workflow.')}</p>
          </div>
        </div>
        <nav aria-label={t('Luồng vận hành')} className="flex flex-wrap gap-1 rounded-sm border border-slate-200 bg-slate-50 p-1">
          {(['import', 'planning'] as const).map((item) =>
            (item === 'import' ? canImport : canPlan) && (
              <Button key={item} size="sm" variant={kind === item ? 'default' : 'ghost'}
                aria-pressed={kind === item} onClick={() => setSelectedFlow(item)}>
                {t(kindLabels[item])}
              </Button>
            ))}
        </nav>
      </header>
      <SharedFlowEditor key={draftKey} kind={kind} draftKey={draftKey} />
    </div>
  );
}

function SharedFlowEditor({ kind, draftKey }: { kind: FlowKind; draftKey: string }) {
  const { t } = useLocale();
  const [loaded, setLoaded] = useState<{
    draft: SharedDraft | null;
    unavailable: boolean;
    activation: PlanningActivation | null;
    activationUnavailable: boolean;
  }>();
  useEffect(() => {
    const abort = new AbortController();
    const draft = operationFlowRepository.getDraft(kind, abort.signal).then(decodeSharedDraft);
    const activation = kind === 'planning'
      ? operationFlowRepository.getPlanningActivation(abort.signal)
      : Promise.resolve(null);
    void Promise.allSettled([draft, activation]).then(([saved, published]) => {
      if (!abort.signal.aborted) setLoaded({
        draft: saved.status === 'fulfilled' ? saved.value : null,
        unavailable: saved.status === 'rejected',
        activation: published.status === 'fulfilled' ? published.value : null,
        activationUnavailable: published.status === 'rejected',
      });
    });
    return () => abort.abort();
  }, [kind]);
  if (!loaded) return <p role="status" className="text-sm text-slate-600">{t('Đang tải bản nháp workflow…')}</p>;
  return <FlowEditor kind={kind} draftKey={draftKey} sharedDraft={loaded.draft} unavailable={loaded.unavailable}
    initialActivation={loaded.activation} activationUnavailable={loaded.activationUnavailable} />;
}

function FlowEditor({ kind, draftKey, sharedDraft, unavailable, initialActivation, activationUnavailable }: {
  kind: FlowKind; draftKey: string; sharedDraft: SharedDraft | null; unavailable: boolean;
  initialActivation: PlanningActivation | null; activationUnavailable: boolean;
}) {
  const { t, intlLocale } = useLocale();
  const [initial] = useState(() => sharedDraft?.draft ?? loadDraft(draftKey, kind));
  const [revision, setRevision] = useState(sharedDraft?.revision ?? 0);
  const [saving, setSaving] = useState(false);
  const [activation, setActivation] = useState(initialActivation);
  const [updatingActivation, setUpdatingActivation] = useState(false);
  const [nodes, setNodes, onNodesChange] = useNodesState<CanvasNode>(
    initial.nodes.map(({ step, position }) => makeNode(step, position)));
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>(initial.edges);
  const [schedule, setSchedule] = useState(initial.schedule);
  const [pickupLocationId, setPickupLocationId] = useState(initial.pickupLocationId);
  const [pickups, setPickups] = useState<PickupLocation[] | null>(null);
  const [pickupError, setPickupError] = useState(false);
  const [pickupAttempt, setPickupAttempt] = useState(0);
  const [tab, setTab] = useState<'editor' | 'history'>('editor');
  const [resolveCoordinates, setResolveCoordinates] = useState(initial.resolveCoordinates);
  const [stopOnCsvErrors, setStopOnCsvErrors] = useState(initial.stopOnCsvErrors);
  const [prepared, setPrepared] = useState<Awaited<ReturnType<typeof readOrderCsv>>>();
  const [driveBusy, setDriveBusy] = useState(false);
  const driveSession = useRef<GoogleDriveSession | null>(null);
  const [driveError, setDriveError] = useState('');
  const [selected, setSelected] = useState<FlowStep>();
  const [runOpen, setRunOpen] = useState(false);
  const [planning, setPlanning] = useState(false);
  const [canvas, setCanvas] = useState<ReactFlowInstance<CanvasNode, Edge>>();
  const [viewRequest, setViewRequest] = useState(0);
  const [deleted, setDeleted] = useState<{ nodes: CanvasNode[]; edges: Edge[] }>();
  const [saved, setSaved] = useState(() => JSON.stringify(initial));
  const hasPickupFilter = nodes.some(({ id }) => id === 'select-orders');
  const availableSteps: readonly FlowStep[] = FLOW_PATHS[kind];
  const path = flowPath(kind, hasPickupFilter);
  const activePickups = pickups?.filter(({ status }) => status === 'ACTIVE') ?? [];
  const selectedPickup = activePickups.find(({ id }) => id === pickupLocationId);
  const runnable = isRunnableFlow(kind, nodes, edges) && (!hasPickupFilter || Boolean(selectedPickup));
  const inspected = nodes.some((node) => node.id === selected) ? selected : undefined;
  const missingStep = path.find((step) => !nodes.some((node) => node.id === step));
  const missingLink = path.slice(0, -1).find((step, index) =>
    !edges.some((edge) => edge.source === step && edge.target === path[index + 1]));

  useEffect(() => {
    if (kind !== 'planning') return;
    let mounted = true;
    pickupLocationRepository.getPickupLocations().then(
      (locations) => { if (mounted) setPickups(locations); },
      () => { if (mounted) setPickupError(true); },
    );
    return () => { mounted = false; };
  }, [kind, pickupAttempt]);

  useEffect(() => {
    if (!canvas || !viewRequest) return;
    const frame = requestAnimationFrame(() => {
      void canvas.fitView({ padding: 0.25, maxZoom: 1.15, duration: 150 });
    });
    return () => cancelAnimationFrame(frame);
  }, [canvas, viewRequest]);

  function addStep(step: FlowStep, from?: FlowStep, dropPosition?: { x: number; y: number }) {
    if (nodes.some((node) => node.id === step)) return;
    const previous = nodes.find((node) => node.id === from);
    const position = dropPosition ?? (previous ? { x: previous.position.x + 244, y: previous.position.y }
      : { x: availableSteps.indexOf(step) * 244, y: 64 });
    const directPlan = step === 'select-orders' && edges.some(({ source, target }) => source === 'schedule' && target === 'plan');
    if (directPlan) {
      setEdges((current) => current.filter(({ source, target }) => source !== 'schedule' || target !== 'plan')
        .concat({ id: 'schedule-select-orders', source: 'schedule', target: 'select-orders' },
          { id: 'select-orders-plan', source: 'select-orders', target: 'plan' }));
    }
    setNodes((current) => [...current.map((node) => directPlan && !dropPosition && node.id === 'plan'
      && Math.abs(node.position.x - position.x) < 200 && Math.abs(node.position.y - position.y) < 70
      ? { ...node, position: { x: position.x + 244, y: node.position.y } } : node), makeNode(step, position)]);
    if (!dropPosition) setSelected(step);
    if (!dropPosition) setViewRequest((value) => value + 1);
  }

  function addNextStep(source: FlowStep) {
    if (source === 'schedule' && kind === 'planning' && !hasPickupFilter
      && edges.some((edge) => edge.source === source && edge.target === 'plan')) {
      addStep('select-orders', source);
      return;
    }
    const target = path[path.indexOf(source) + 1];
    if (!target || edges.some((edge) => edge.source === source && edge.target === target)) return;

    if (nodes.some(({ id }) => id === target)) {
      setSelected(target);
    } else {
      addStep(target, source);
    }
    connect({ source, target, sourceHandle: null, targetHandle: null });
  }

  function useTemplate() {
    const graph = template(kind);
    setNodes(graph.nodes.map(({ step, position }) => makeNode(step, position)));
    setEdges(graph.edges);
    setSelected('schedule');
    setViewRequest((value) => value + 1);
  }

  function undoDelete() {
    if (!deleted) return;
    const restored = deleted.nodes.filter((node) => !nodes.some(({ id }) => id === node.id));
    const ids = new Set([...nodes, ...restored].map(({ id }) => id));
    setNodes([...nodes, ...restored.map((node) => ({ ...node, selected: false }))]);
    setEdges(deleted.edges.reduce((current, edge) =>
      ids.has(edge.source) && ids.has(edge.target) ? addEdge({ ...edge, selected: false }, current) : current, edges));
    if (restored.length) setSelected(restored[0].data.step);
    setDeleted(undefined);
    setViewRequest((value) => value + 1);
  }

  function connect(connection: Connection) {
    const sourceIndex = path.indexOf(connection.source as FlowStep);
    if (sourceIndex < 0 || path[sourceIndex + 1] !== connection.target) {
      toast.error(t('Hãy nối các node theo thứ tự của luồng.'));
      return;
    }
    setEdges((current) => addEdge(connection, current));
  }

  const draft = draftSchema.safeParse({
    nodes: nodes.map(({ id, position, data }) => ({ id, step: data.step, position })),
    edges,
    schedule,
    resolveCoordinates,
    stopOnCsvErrors,
    pickupLocationId: hasPickupFilter ? pickupLocationId : null,
  });
  const currentDraft = draft.success ? JSON.stringify(draft.data) : null;
  const unsaved = currentDraft !== saved;
  const canActivate = kind === 'planning' && !unavailable && !activationUnavailable
    && !unsaved && runnable && revision > 0 && activation?.revision !== revision;

  async function updateActivation(enabled: boolean) {
    if (updatingActivation || (enabled && !canActivate)) return;
    setUpdatingActivation(true);
    try {
      if (enabled) {
        const data = await operationFlowRepository.activatePlanning(revision);
        setActivation(data);
      } else {
        await operationFlowRepository.deactivatePlanning();
        setActivation(null);
      }
    } catch (error) {
      toast.error(t(error instanceof Error && 'status' in error && error.status === 409
        ? 'Bản nháp đã được cập nhật ở nơi khác. Tải lại trang trước khi lưu tiếp.'
        : 'Không thể cập nhật lịch lập kế hoạch.'));
    } finally {
      setUpdatingActivation(false);
    }
  }

  async function save() {
    if (saving) return;
    if (!draft.success) {
      toast.error(t('Kiểm tra lại cấu hình lịch chạy.'));
      return;
    }
    try {
      const value = JSON.stringify(draft.data);
      if (!unavailable) {
        setSaving(true);
        const { resolveCoordinates, stopOnCsvErrors, pickupLocationId, ...rest } = draft.data;
        const data = await operationFlowRepository.saveDraft(kind, {
          revision,
          draft: {
            ...rest, resolve_coordinates: resolveCoordinates, stop_on_csv_errors: stopOnCsvErrors,
            pickup_location_id: pickupLocationId
          },
        });
        setRevision(data.revision);
        setSaved(value);
        toast.success(t('Đã lưu bản nháp cho doanh nghiệp.'));
        return;
      }
      localStorage.setItem(draftKey, value);
      setSaved(value);
      toast.success(t('Đã lưu bản nháp trên trình duyệt.'));
    } catch (error) {
      toast.error(t(error instanceof Error && 'status' in error && error.status === 409
        ? 'Bản nháp đã được cập nhật ở nơi khác. Tải lại trang trước khi lưu tiếp.'
        : 'Không lưu được bản nháp.'));
    } finally {
      setSaving(false);
    }
  }

  async function runPlanning() {
    if (!runnable || planning) return;
    setPlanning(true);
    try {
      await operationFlowRepository.runPlanning(hasPickupFilter ? pickupLocationId : null);
      setRunOpen(false);
      setTab('history');
    } catch {
      toast.error(t('Không thể chạy lập kế hoạch. Vui lòng thử lại.'));
    } finally {
      setPlanning(false);
    }
  }

  const planningTabs = kind === 'planning' && (
    <nav aria-label={t('Luồng lập kế hoạch giao hàng')} className="flex gap-2">
      <Button size="sm" variant={tab === 'editor' ? 'default' : 'outline'} aria-pressed={tab === 'editor'}
        onClick={() => setTab('editor')}>{t('Sơ đồ luồng')}</Button>
      <Button size="sm" variant={tab === 'history' ? 'default' : 'outline'} aria-pressed={tab === 'history'}
        onClick={() => setTab('history')}>{t('Lịch sử chạy')}</Button>
    </nav>
  );

  if (tab === 'history') {
    return <div className="space-y-3">{planningTabs}<PlanningRunHistory pickups={pickups ?? []} /></div>;
  }

  return (
    <div className="space-y-3">
      {planningTabs}
      <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-slate-200 bg-white p-3">
        <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
          <span role="status" className="inline-flex items-center gap-2 rounded-full border border-slate-200 bg-slate-50 px-2.5 py-1 text-xs font-semibold text-slate-800">
            <span className={`size-2 rounded-full ${activation ? 'bg-emerald-600' : 'bg-slate-400'}`} aria-hidden />
            {t(activation ? 'Đã kích hoạt lịch' : activationUnavailable ? 'Chưa tải được trạng thái lịch' : 'Chưa kích hoạt')}
          </span>
          <span className="text-xs text-slate-600">
            {t(unsaved ? 'Có thay đổi chưa lưu' : revision ? 'Bản nháp doanh nghiệp' : 'Bản nháp trên trình duyệt')}
          </span>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <Button size="sm" variant="outline" disabled={updatingActivation} isLoading={saving} onClick={() => void save()}><Save size={15} aria-hidden />{t('Lưu bản nháp')}</Button>
          {kind === 'planning' && activation && (
            <Button size="sm" variant="outline" disabled={updatingActivation || saving}
              onClick={() => void updateActivation(false)}>{t('Tạm dừng lịch')}</Button>
          )}
          {kind === 'planning' && (
            <Button size="sm" disabled={!canActivate || saving || updatingActivation}
              aria-describedby="flow-activation-hint" onClick={() => void updateActivation(true)}>
              <Power size={15} aria-hidden />{t(activation ? 'Áp dụng lịch mới' : 'Kích hoạt')}
            </Button>
          )}
        </div>
        <div id="flow-activation-hint" className="basis-full space-y-1 text-xs text-slate-600" role="status">
          {activation && <p>
            {t('Lịch đang chạy')} · {activation.schedule.mode === 'daily'
              ? `${t('Hằng ngày')} ${activation.schedule.time}`
              : `${t('Khoảng lặp (giờ)')} ${activation.schedule.hours}`}
            {activation.pickup_location_id && <> · {pickups?.find((p) => p.id === activation.pickup_location_id)?.name
              ?? t('Điểm lấy hàng đã cấu hình')}</>}
            {' · '}{t('Lần chạy kế tiếp')}{' '}
            {new Intl.DateTimeFormat(intlLocale, {
              timeZone: 'Asia/Ho_Chi_Minh', dateStyle: 'short', timeStyle: 'short',
            }).format(new Date(activation.next_run_at))} (UTC+7)
          </p>}
          <p>{t(kind === 'import' ? 'Luồng nhập vận đơn hiện chạy thủ công.'
            : activationUnavailable ? 'Tải lại trang để xem trạng thái lịch.'
              : 'Lưu luồng hoàn chỉnh rồi áp dụng bản nháp để thay đổi lịch chạy.')}</p>
        </div>
        {unavailable && <p role="status" className="basis-full text-xs text-amber-900">
          {t('Chưa tải được bản nháp doanh nghiệp. Bạn có thể lưu trên trình duyệt này.')}
        </p>}
      </div>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex flex-wrap items-center gap-2">
          {availableSteps.map((step) => {
            const Icon = catalog[step].icon;
            return <Button key={step} size="sm" variant="outline" disabled={nodes.some((node) => node.id === step)}
              className="cursor-grab active:cursor-grabbing"
              draggable={!nodes.some((node) => node.id === step)}
              onDragStart={(event) => {
                event.dataTransfer.setData(NODE_MIME, step);
                event.dataTransfer.effectAllowed = 'copy';
              }}
              onClick={() => addStep(step)}><Plus size={13} aria-hidden /><Icon size={15} aria-hidden />{t(catalog[step].label)}</Button>;
          })}
        </div>
        <div className="flex gap-2">
          <Button size="sm" variant="outline" disabled={!runnable || planning || driveBusy} onClick={() => {
            if (kind === 'import' && !prepared) {
              setSelected('prepare');
              toast.info(t('Chọn file CSV trong node Chuẩn bị CSV từ Drive để chạy.'));
              return;
            }
            setRunOpen(true);
          }}>
            <Play size={15} aria-hidden />{t(kind === 'import' ? 'Xem trước dữ liệu' : 'Lập kế hoạch thủ công')}
          </Button>
        </div>
      </div>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p role="status" className="text-sm text-slate-600">
          {missingStep ? `${t('Thiếu node')}: ${t(catalog[missingStep].label)}` : missingLink
            ? `${t('Chưa nối')}: ${t(catalog[missingLink].label)} → ${t(catalog[path[path.indexOf(missingLink) + 1]].label)}`
            : hasPickupFilter && !selectedPickup ? t('Chọn điểm lấy hàng trong node Lấy đơn chờ điều phối.')
              : !runnable ? t('Kiểm tra các dây nối trong luồng.')
                : kind === 'import' && !prepared ? t('Chọn file CSV trong node Chuẩn bị CSV từ Drive để chạy.') : undefined}
        </p>
        {deleted && <Button size="sm" variant="outline" onClick={undoDelete}>
          <Undo2 size={15} aria-hidden />{t('Hoàn tác xóa')}
        </Button>}
      </div>
      <div>
        <div className="relative h-[clamp(350px,56vh,620px)] min-w-0 overflow-hidden rounded-xl border border-slate-200 bg-[#f4f8f8]"
          onDragOver={(event) => {
            if (!event.dataTransfer.types.includes(NODE_MIME)) return;
            event.preventDefault();
            event.dataTransfer.dropEffect = 'copy';
          }}
          onDrop={(event) => {
            const step = availableSteps.find((item) => item === event.dataTransfer.getData(NODE_MIME));
            if (!step || !canvas) return;
            event.preventDefault();
            const position = canvas.screenToFlowPosition({ x: event.clientX, y: event.clientY });
            addStep(step, undefined, position);
          }}>
          <SelectNextStep.Provider value={addNextStep}>
            <ReactFlow
              nodes={nodes}
              nodeTypes={nodeTypes}
              defaultEdgeOptions={edgeDefaults}
              edges={edges}
              onNodesChange={onNodesChange}
              onEdgesChange={onEdgesChange}
              onConnect={connect}
              onInit={setCanvas}
              onDelete={(removed) => {
                setDeleted(removed);
                setRunOpen(false);
              }}
              onNodeClick={(_, node) => setSelected(node.data.step)}
              onPaneClick={() => setSelected(undefined)}
              fitView
              minZoom={0.35}
              maxZoom={1.8}
            >
              <Background gap={22} color="#b8cecd" />
              <Controls />
            </ReactFlow>
          </SelectNextStep.Provider>
          {nodes.length === 0 && (
            <div className="pointer-events-none absolute inset-0 z-10 grid place-items-center p-4">
              <section className="pointer-events-auto max-w-xs space-y-3 rounded-sm border border-slate-200 bg-white p-4 text-center">
                <h2 className="text-sm font-semibold text-slate-950">{t('Canvas đang trống')}</h2>
                <div className="flex flex-wrap justify-center gap-2">
                  <Button size="sm" onClick={() => addStep('schedule')}>
                    <Plus size={15} aria-hidden />{t('Thêm bước đầu tiên')}
                  </Button>
                  <Button size="sm" variant="outline" onClick={useTemplate}>{t('Dùng mẫu')}</Button>
                </div>
              </section>
            </div>
          )}
        </div>
        <Dialog.Root modal={inspected !== 'prepare'} open={Boolean(inspected)} onOpenChange={(open) => {
          if (!open && !driveBusy) setSelected(undefined);
        }}>
          <Dialog.Portal>
            <div className="fixed inset-0 z-[60] bg-slate-950/45" aria-hidden="true" />
            <Dialog.Content className="fixed left-1/2 top-1/2 z-[61] max-h-[90dvh] w-[min(42rem,calc(100%_-_2rem))] -translate-x-1/2 -translate-y-1/2 space-y-4 overflow-y-auto rounded-xl border border-slate-200 bg-white p-5 shadow-xl"
              onInteractOutside={(event) => { if (driveBusy) event.preventDefault(); }}
              onEscapeKeyDown={(event) => { if (driveBusy) event.preventDefault(); }}>
              <div className="flex items-start justify-between gap-3">
                <Dialog.Title className="text-lg font-semibold text-slate-950">
                  {inspected ? t(catalog[inspected].label) : t('Cấu hình node')}
                </Dialog.Title>
                <Dialog.Close asChild><Button size="sm" variant="ghost" disabled={driveBusy} aria-label={t('Đóng')}>
                  <X size={17} aria-hidden />
                </Button></Dialog.Close>
              </div>
              <Dialog.Description className="text-sm text-slate-600">{t(inspected ? catalog[inspected].detail : 'Chọn một node trên canvas để xem cấu hình.')}</Dialog.Description>
              {inspected === 'schedule' && (
                <div className="space-y-3">
                  <label className="block text-sm font-medium">
                    {t('Kiểu lịch')}
                    <CustomSelect
                      className="mt-1"
                      value={schedule.mode}
                      onValueChange={(next) =>
                        setSchedule({ ...schedule, mode: next === 'interval' ? 'interval' : 'daily' })
                      }
                      options={[
                        { value: 'daily', label: t('Hằng ngày') },
                        { value: 'interval', label: t('Lặp mỗi X giờ') },
                      ]}
                    />
                  </label>
                  <label className="block text-sm font-medium">
                    {schedule.mode === 'daily' ? t('Giờ chạy (UTC+7)') : t('Khoảng lặp (giờ)')}
                    {schedule.mode === 'daily' ? (
                      <input type="time" className="mt-1 block w-full rounded-md border border-slate-300 p-2" value={schedule.time}
                        onChange={(event) => setSchedule({ ...schedule, time: event.target.value })} />
                    ) : (
                      <input type="number" min={1} max={168} className="mt-1 block w-full rounded-md border border-slate-300 p-2"
                        value={schedule.hours} onChange={(event) => setSchedule({ ...schedule, hours: Number(event.target.value) })} />
                    )}
                  </label>
                  <p className="text-xs text-slate-600">{t(kind === 'planning'
                    ? 'Lịch đã kích hoạt được chạy theo giờ Việt Nam, kể cả khi đóng trình duyệt.'
                    : 'Giờ chạy được lưu trong bản nháp. Luồng chưa tự chạy theo lịch.')}</p>
                </div>
              )}
              {kind === 'import' && (
                <div hidden={inspected !== 'prepare'} className="space-y-3">
                  <div className="space-y-2 rounded-sm border border-slate-200 p-3">
                    <p className="text-sm font-medium text-slate-900">{t('CSV hoặc Google Sheets trên Drive')}</p>
                    <p className="text-xs text-slate-600">{t('Chọn file từ tài khoản Google đang kết nối hoặc đổi tài khoản trong cửa sổ Google.')}</p>
                    <DriveCsvPicker disabled={driveBusy || runOpen} onBusy={setDriveBusy} onError={setDriveError}
                      buttonLabel={prepared ? 'Đổi file nguồn' : undefined}
                      session={driveSession}
                      allowAccountSwitch
                      onFile={async (file) => {
                        setPrepared(undefined);
                        try {
                          setPrepared(await readOrderCsv(file));
                        } catch (error) {
                          setDriveError(error instanceof Error ? error.message : 'Không thể đọc file CSV.');
                        }
                      }} />
                    {driveBusy && <p role="status" className="text-sm">{t('Đang lấy file từ Google Drive…')}</p>}
                    {driveError && <p role="alert" className="text-sm text-red-700">{t(driveError)}</p>}
                    {prepared && (
                      <div className="rounded-md bg-slate-50 p-2 text-sm text-slate-700">
                        <p className="break-all font-medium">{prepared.name}</p>
                        <p>{t('Dòng đọc được')}: {prepared.rows.length} · {t('Có lỗi')}: {prepared.errors.length}</p>
                      </div>
                    )}
                  </div>
                  <label className="flex items-start gap-2 text-sm text-slate-800">
                    <input type="checkbox" className="mt-1" checked={resolveCoordinates} disabled={runOpen}
                      onChange={(event) => setResolveCoordinates(event.target.checked)} />
                    {t('Tra tọa độ từ địa chỉ khi thiếu tọa độ')}
                  </label>
                  <p className="text-xs text-slate-600">{t('File đã chọn chỉ dùng trong phiên này. Các tùy chọn được lưu cùng bản nháp.')}</p>
                </div>
              )}
              {inspected === 'import' && (
                <div className="space-y-2">
                  <label className="flex items-start gap-2 text-sm text-slate-800">
                    <input type="checkbox" className="mt-1" checked={stopOnCsvErrors} disabled={runOpen}
                      onChange={(event) => setStopOnCsvErrors(event.target.checked)} />
                    {t('Dừng nhập khi bản xem trước có dòng CSV lỗi')}
                  </label>
                  <p className="text-xs text-slate-600">{t('Dòng chưa có điểm giao vẫn vào Cần bổ sung địa chỉ.')}</p>
                </div>
              )}
              {inspected === 'select-orders' && (
                <div className="space-y-2">
                  <label className="block text-sm font-medium">
                    {t('Điểm lấy hàng')}
                    <CustomSelect
                      className="mt-1"
                      disabled={!activePickups.length}
                      value={selectedPickup?.id ?? ''}
                      onValueChange={(next) => setPickupLocationId(next || null)}
                      options={[
                        { value: '', label: t('Chọn điểm lấy hàng') },
                        ...activePickups.map((pickup) => ({ value: pickup.id, label: pickup.name })),
                      ]}
                    />
                  </label>
                  {pickupError ? <div className="flex items-center gap-2 text-sm">
                    <span role="alert">{t('Không thể tải điểm lấy hàng.')}</span>
                    <Button size="sm" variant="outline" onClick={() => { setPickupError(false); setPickupAttempt((attempt) => attempt + 1); }}>
                      {t('Thử lại')}
                    </Button>
                  </div> : pickups === null ? <p role="status" className="text-sm">{t('Đang tải điểm lấy hàng…')}</p>
                    : activePickups.length === 0 ? <Link href="/locations" className="text-sm text-blue-700 underline">{t('Thêm điểm lấy hàng')}</Link>
                      : pickupLocationId && !selectedPickup ? <p role="status" className="text-sm">{t('Điểm lấy hàng đã chọn không còn hoạt động.')}</p> : null}
                </div>
              )}
              {inspected === 'plan' && (
                <div className="space-y-2 text-sm text-slate-600">
                  <p>{hasPickupFilter ? `${t('Điểm lấy hàng')} · ${selectedPickup?.name ?? '—'}`
                    : t('Phạm vi: đơn đủ điều kiện của doanh nghiệp.')}</p>
                  <p>{t('Quy tắc duyệt và chọn tài xế lấy từ Cài đặt doanh nghiệp.')}</p>
                  <Link href="/settings" className="font-medium text-blue-700 underline">{t('Mở cài đặt doanh nghiệp')}</Link>
                </div>
              )}
              {inspected && <Button size="sm" variant="outline" onClick={() => {
                setDeleted({
                  nodes: nodes.filter(({ id }) => id === inspected),
                  edges: edges.filter(({ source, target }) => source === inspected || target === inspected),
                });
                setNodes((current) => current.filter(({ id }) => id !== inspected));
                setEdges((current) => current.filter(({ source, target }) => source !== inspected && target !== inspected));
                setSelected(undefined);
                setRunOpen(false);
              }}><Trash2 size={14} aria-hidden />{t('Xóa node')}</Button>}
            </Dialog.Content>
          </Dialog.Portal>
        </Dialog.Root>
      </div>
      {runOpen && runnable && (kind === 'import' ? (
        <section className="space-y-2">
          <div className="flex items-center justify-between gap-2">
            <h2 className="text-sm font-semibold text-slate-900">{t('Chạy luồng nhập vận đơn')}</h2>
            <Button size="sm" variant="outline" onClick={() => setRunOpen(false)}>{t('Đóng')}</Button>
          </div>
          {prepared && <OrderImportPanel prepared={prepared} resolveCoordinates={resolveCoordinates}
            stopOnCsvErrors={stopOnCsvErrors} onDiscard={() => { setPrepared(undefined); setRunOpen(false); }} />}
        </section>
      ) : (
        <section className="flex flex-wrap items-center gap-3 rounded-sm border border-slate-200 p-4">
          <span className="text-sm text-slate-700">{t('Chạy lập kế hoạch cho các đơn đang chờ?')}</span>
          {selectedPickup && <span className="text-sm font-medium">{selectedPickup.name}</span>}
          <Button size="sm" isLoading={planning} onClick={() => void runPlanning()}>{t('Xác nhận chạy')}</Button>
          <Button size="sm" variant="outline" disabled={planning} onClick={() => setRunOpen(false)}>{t('Hủy')}</Button>
        </section>
      ))}
    </div>
  );
}
