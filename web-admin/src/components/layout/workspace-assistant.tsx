'use client';

import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { ArrowRight, Play, Send, Sparkles, Square, Sun, X } from 'lucide-react';
import { useEffect, useRef, useState, type FormEvent } from 'react';
import { toast } from 'sonner';
import { createAssistantTextStream } from '@/application/assistant-text-stream';
import { reportDispatchPlanning } from '@/application/dispatch-planning';
import { Button } from '@/components/ui/button';
import { useLocale } from '@/i18n/provider';
import {
  assistantRepository,
  type AssistantAction,
  type AssistantIntent,
  type AssistantRouteReference,
  type AssistantTurn,
} from '@/infrastructure/repositories/assistant.repository';
import { orderRepository } from '@/infrastructure/repositories/order.repository';
import { ApiError } from '@/lib/api';

type Message = AssistantTurn & {
  actions?: AssistantAction[];
  routes?: AssistantRouteReference[];
  error?: string;
  sources?: string[];
};
type PlanningAction = Extract<AssistantAction, { type: 'TRIGGER_PLANNING' }>;

const defaultSuggestions = ['Tuyến nào có thể gặp mưa chiều nay?', 'Đơn nào có nguy cơ trễ?', 'Kho nào có đơn chờ lâu nhất?', 'Tuyến nào đang chờ xác nhận?'];

const sourceLabels: Record<string, string> = {
  workspace_get_capabilities: 'Hướng dẫn SwiftRoute', orders_search: 'Đơn hàng',
  dispatch_list_routes: 'Kế hoạch giao hàng', dispatch_get_route: 'Chi tiết tuyến', dispatch_get_selected_route: 'Chi tiết tuyến',
  dispatch_get_weather_risks: 'Dự báo thời tiết theo ETA', dispatch_list_waiting_orders_by_depot: 'Đơn chờ theo kho',
  dispatch_list_appointment_risks: 'Rủi ro giờ hẹn', dispatch_list_route_handoffs: 'Bàn giao tuyến',
};

function AssistantMessageActions({ message, planningId, onPlanning, onOpen }: {
  message: Message;
  planningId: string | undefined;
  onPlanning: (action: PlanningAction) => Promise<void>;
  onOpen: (href: string) => void;
}) {
  const { t, intlLocale } = useLocale();
  const actions = message.actions ?? [];
  const actionLinks = new Set(actions.flatMap((action) => action.type === 'NAVIGATE' ? [action.payload.href] : []));
  const routeLinks = message.routes?.map(({ planId, label }) => ({
    href: `/dispatch?plan=${encodeURIComponent(planId)}`,
    label,
  })).filter(({ href }) => !actionLinks.has(href)) ?? [];
  const formatCount = new Intl.NumberFormat(intlLocale);

  if (!actions.length && !routeLinks.length) return null;
  return (
    <div className="mt-3 flex flex-wrap gap-2">
      {actions.map((action) => action.type === 'TRIGGER_PLANNING' ? (
        <Button key={action.id} type="button" size="sm" className="min-h-11 max-w-full text-left"
          variant={action.variant === 'primary' ? 'default' : 'outline'}
          disabled={!!planningId} isLoading={planningId === action.id} onClick={() => void onPlanning(action)}>
          <Play size={14} className="shrink-0" aria-hidden="true" />
          <span className="min-w-0 wrap-break-word">
            {action.label}
            <span className="block text-xs opacity-90">
              {t('Số đơn đủ điều kiện:')} {formatCount.format(action.payload.order_count)}
            </span>
          </span>
        </Button>
      ) : (
        <Link key={action.id} href={action.payload.href} onNavigate={() => onOpen(action.payload.href)}
          className="inline-flex min-h-11 items-center gap-2 rounded-lg border border-slate-200 px-3 py-1.5 text-sm font-medium text-blue-700 hover:bg-blue-50 focus-visible:outline-2 focus-visible:outline-blue-700">
          {action.label}<ArrowRight size={14} className="shrink-0" aria-hidden="true" />
        </Link>
      ))}
      {routeLinks.map(({ href, label }) => (
        <Link key={href} href={href} onNavigate={() => onOpen(href)}
          className="inline-flex min-h-11 items-center rounded-md border border-slate-200 px-2 py-1 text-xs font-medium text-blue-700 hover:bg-blue-50 focus-visible:outline-2 focus-visible:outline-blue-700">
          {t('Mở tuyến')} {label}
        </Link>
      ))}
    </div>
  );
}

function renderAssistantText(text: string) {
  return text.split(/\*\*([^*\n]+)\*\*/g).map((segment, index) =>
    index % 2 ? <strong key={index}>{segment}</strong> : segment);
}

function WorkspaceAssistantSession({ tenantId }: { tenantId: string }) {
  const { t, locale } = useLocale();
  const pathname = usePathname();
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [input, setInput] = useState('');
  const [messages, setMessages] = useState<Message[]>([]);
  const [streaming, setStreaming] = useState(false);
  const [planningId, setPlanningId] = useState<string>();
  const [selectedPlan, setSelectedPlan] = useState<string>();
  const controller = useRef<AbortController | null>(null);
  const planningRequest = useRef<AbortController | null>(null);
  const launcher = useRef<HTMLButtonElement>(null);
  const composer = useRef<HTMLTextAreaElement>(null);
  const bottom = useRef<HTMLDivElement>(null);

  useEffect(() => () => {
    controller.current?.abort();
    planningRequest.current?.abort();
  }, []);
  useEffect(() => {
    if (open) composer.current?.focus();
  }, [open]);
  useEffect(() => {
    if (open) bottom.current?.scrollIntoView({ block: 'end' });
  }, [messages, open, streaming]);
  useEffect(() => {
    function receivePlan(event: Event) {
      setSelectedPlan((event as CustomEvent<{ planId?: string }>).detail.planId);
    }
    window.addEventListener('swiftroute:assistant-plan', receivePlan);
    return () => window.removeEventListener('swiftroute:assistant-plan', receivePlan);
  }, []);

  function close() {
    setOpen(false);
    launcher.current?.focus();
  }

  function openDispatch(href: string) {
    const planId = new URLSearchParams(href.split('?')[1]).get('plan');
    if (pathname === '/dispatch' && planId) {
      window.dispatchEvent(new CustomEvent('swiftroute:assistant-open-plan', { detail: { planId } }));
    }
    close();
  }

  async function runPlanning(action: PlanningAction) {
    if (planningRequest.current) return;
    const request = new AbortController();
    planningRequest.current = request;
    setPlanningId(action.id);
    try {
      const result = await orderRepository.triggerDispatchPlanning({
        scope: 'DEPOT',
        depotId: action.payload.depot_id,
        tenantId,
        signal: request.signal,
      });
      if (request.signal.aborted) return;
      const planId = reportDispatchPlanning(result, t);
      if (planId) {
        const href = `/dispatch?plan=${encodeURIComponent(planId)}`;
        openDispatch(href);
        router.push(href);
      }
    } catch (error) {
      if (request.signal.aborted) return;
      toast.error(t(error instanceof ApiError && error.status === 403
        ? 'Bạn không có quyền lập kế hoạch điều phối.'
        : 'Không thể chạy lập kế hoạch. Vui lòng thử lại.'));
    } finally {
      planningRequest.current = null;
      if (!request.signal.aborted) setPlanningId(undefined);
    }
  }

  async function send(question: string, intent?: AssistantIntent) {
    const text = question.trim();
    if (!text || controller.current) return;
    const abort = new AbortController();
    controller.current = abort;
    const history = messages.filter((message) => message.text && !message.error)
      .slice(-8).map(({ role, text }) => ({ role, text: text.slice(0, 4000) }));
    setInput('');
    setMessages((current) => [...current, { role: 'user', text }, { role: 'assistant', text: '' }]);
    setStreaming(true);
    function update(apply: (message: Message) => Message) {
      if (abort.signal.aborted) return;
      setMessages((current) => [...current.slice(0, -1), apply(current[current.length - 1])]);
    }
    const textStream = createAssistantTextStream(
      (delta) => update((message) => ({ ...message, text: message.text + delta })),
      abort.signal,
      window.matchMedia('(prefers-reduced-motion: reduce)').matches,
    );
    try {
      const planId = !intent && pathname === '/dispatch'
        ? selectedPlan ?? new URLSearchParams(window.location.search).get('plan') ?? undefined
        : undefined;
      await assistantRepository.stream({ question: text, history, plan_id: planId, language: locale, intent },
        tenantId, abort.signal, (event, payload) => {
          if (event === 'delta') {
            const { text: token } = payload as { text: string };
            textStream.push(token);
          } else if (event === 'context') {
            update((message) => ({ ...message, routes: payload as AssistantRouteReference[] }));
          } else if (event === 'sources') {
            const { tools } = payload as { tools: string[] };
            update((message) => ({ ...message, sources: tools.flatMap((name) => sourceLabels[name] ? [sourceLabels[name]] : []) }));
          } else if (event === 'actions') {
            update((message) => ({ ...message, actions: payload as AssistantAction[] }));
          } else if (event === 'error') {
            const { code } = payload as { code: string };
            update((message) => ({ ...message, error: code === 'assistant_not_configured'
              ? 'Trợ lý chưa được cấu hình Gemini API key.' : 'Gemini chưa trả lời được. Hãy thử lại.' }));
          }
        });
    } catch {
      update((message) => ({ ...message, actions: undefined, error: 'Mất kết nối với trợ lý. Hãy thử lại.' }));
    } finally {
      await textStream.finish();
      if (controller.current === abort) {
        controller.current = null;
        setStreaming(false);
      }
    }
  }

  function stop() {
    controller.current?.abort();
    setMessages((current) => {
      const last = current.at(-1);
      return last?.role === 'assistant' && !last.text ? current.slice(0, -1) : current;
    });
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void send(input);
  }

  const suggestions = pathname === '/dispatch' && selectedPlan
    ? ['Phân tích tuyến đang chọn', ...defaultSuggestions.slice(0, 2)]
    : pathname === '/orders' ? [defaultSuggestions[1], defaultSuggestions[0], defaultSuggestions[2]] : defaultSuggestions;

  return (
    <>
      <button ref={launcher} type="button" aria-label={t('Mở trợ lý AI')} aria-expanded={open} aria-controls={open ? 'workspace-assistant' : undefined}
        onClick={() => open ? close() : setOpen(true)}
        className="inline-flex min-h-9 items-center gap-1.5 rounded-lg border border-slate-200 bg-white px-2.5 text-sm font-semibold text-slate-800 hover:bg-slate-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-700">
        <Sparkles size={15} aria-hidden="true" /> AI
      </button>
      {open && (
        <aside id="workspace-assistant" aria-labelledby="workspace-assistant-heading"
          onKeyDown={(event) => { if (event.key === 'Escape') close(); }}
          className="fixed inset-0 z-50 flex min-h-0 flex-col border-slate-200 bg-white sm:inset-auto sm:right-2 sm:top-16 sm:bottom-2 sm:w-[27rem] sm:rounded-xl sm:border sm:shadow-lg">
          <div className="flex shrink-0 items-center justify-between gap-3 border-b border-slate-200 px-4 py-3">
            <h2 id="workspace-assistant-heading" className="font-semibold text-slate-950">{t('Trợ lý điều phối')}</h2>
            <div className="flex items-center gap-2">
              {messages.length > 0 && (
                <Button size="sm" variant="ghost" disabled={streaming || !!planningId} onClick={() => setMessages([])}>{t('Cuộc trò chuyện mới')}</Button>
              )}
              <button type="button" onClick={close} aria-label={t('Đóng trợ lý AI')}
                className="grid size-9 place-items-center rounded-lg text-slate-600 hover:bg-slate-100 focus-visible:outline-2 focus-visible:outline-blue-700">
                <X size={18} aria-hidden="true" />
              </button>
            </div>
          </div>
          <div className="min-h-0 flex-1 space-y-4 overflow-y-auto px-4 py-4">
            {messages.length === 0 && (
              <div className="space-y-3">
                <p className="text-sm text-slate-600">{t('Tra cứu đơn hàng, tuyến giao, giờ hẹn và dự báo mưa.')}</p>
                <Button type="button" variant="outline" className="min-h-11 w-full justify-start"
                  onClick={() => void send(t('Tóm tắt ca trực'), 'shift_briefing')}>
                  <Sun size={16} aria-hidden="true" /> {t('Tóm tắt ca trực')}
                </Button>
                {suggestions.map((suggestion) => (
                  <button type="button" key={suggestion} onClick={() => void send(t(suggestion))} className="flex min-h-10 w-full items-center justify-between gap-2 rounded-lg border border-slate-200 px-3 py-2 text-left text-sm text-slate-800 hover:bg-slate-50 focus-visible:outline-2 focus-visible:outline-blue-700">
                    {t(suggestion)} <ArrowRight className="shrink-0 text-slate-500" size={14} aria-hidden="true" />
                  </button>
                ))}
              </div>
            )}
            {messages.map((message, index) => (
              <div key={index} className={message.role === 'user' ? 'ml-6 rounded-xl bg-slate-100 px-3 py-2' : 'pr-2'}>
                <p className="wrap-break-word whitespace-pre-wrap text-sm leading-relaxed text-slate-900">
                  {message.role === 'assistant' && message.text
                    ? renderAssistantText(message.text)
                    : message.text || (!message.error ? t('Đang phân tích…') : '')}
                </p>
                {message.error && <p role="alert" className="mt-1 text-sm text-rose-800">{t(message.error)}</p>}
                {!!message.sources?.length && <p className="mt-2 text-xs leading-5 text-slate-600">{t('Đã tra cứu:')} {[...new Set(message.sources)].map((source) => t(source)).join(' · ')}</p>}
                {!message.error && !(streaming && index === messages.length - 1) && (
                  <AssistantMessageActions message={message} planningId={planningId}
                    onPlanning={runPlanning} onOpen={openDispatch} />
                )}
              </div>
            ))}
            <div ref={bottom} />
          </div>
          <form onSubmit={submit} className="shrink-0 border-t border-slate-200 p-3">
            <div className="flex items-end gap-2">
              <textarea ref={composer} value={input} onChange={(event) => setInput(event.target.value)} maxLength={2000}
                aria-label={t('Tin nhắn cho trợ lý')} placeholder={t('Hỏi trợ lý điều phối…')} rows={2}
                onKeyDown={(event) => {
                  if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) {
                    event.preventDefault();
                    if (!streaming && input.trim()) {
                      void send(input);
                    }
                  }
                }}
                className="min-w-0 flex-1 resize-none rounded-lg border border-slate-300 px-3 py-2 text-sm focus:outline-none" />
              {streaming ? (
                <Button type="button" size="sm" variant="outline" onClick={stop} aria-label={t('Dừng trả lời')}><Square size={14} aria-hidden="true" /></Button>
              ) : (
                <Button type="submit" size="sm" disabled={!input.trim()} aria-label={t('Gửi tin nhắn')}><Send size={15} aria-hidden="true" /></Button>
              )}
            </div>
            <p className="mt-2 text-xs text-slate-600">{t('Tin nhắn được xử lý bởi Gemini. Tránh nhập thông tin cá nhân.')}</p>
          </form>
        </aside>
      )}
    </>
  );
}

export function WorkspaceAssistant({ tenantId }: { tenantId: string }) {
  return <WorkspaceAssistantSession key={tenantId} tenantId={tenantId} />;
}
