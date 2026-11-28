import { streamSse } from '@/lib/api';
import { z } from 'zod';

export interface AssistantTurn {
  role: 'user' | 'assistant';
  text: string;
}

export interface AssistantRouteReference {
  planId: string;
  label: string;
}

const actionFields = {
  id: z.string().min(1),
  label: z.string().min(1),
  variant: z.enum(['primary', 'secondary']),
};

const assistantAction = z.discriminatedUnion('type', [
  z.object({
    ...actionFields,
    type: z.literal('TRIGGER_PLANNING'),
    payload: z.object({ depot_id: z.uuid(), order_count: z.number().int().positive() }),
  }),
  z.object({
    ...actionFields,
    type: z.literal('NAVIGATE'),
    payload: z.object({
      href: z.union([
        z.literal('/orders'),
        z.string().regex(/^\/dispatch(?:\?plan=[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12})?$/i),
      ]),
    }),
  }),
]);

export type AssistantAction = z.infer<typeof assistantAction>;
export type AssistantIntent = 'shift_briefing';

function readActions(value: unknown): AssistantAction[] {
  if (!Array.isArray(value)) return [];
  return value.flatMap((item) => {
    const parsed = assistantAction.safeParse(item);
    return parsed.success ? [parsed.data] : [];
  });
}

export const assistantRepository = {
  async stream(
    request: {
      question: string;
      history: AssistantTurn[];
      language: 'vi' | 'en';
      plan_id?: string;
      intent?: AssistantIntent;
    },
    tenantId: string,
    signal: AbortSignal,
    onEvent: (event: 'delta' | 'context' | 'sources' | 'actions' | 'error' | 'done', value: unknown) => void,
  ) {
    let completed = false;
    await streamSse('/orders/assistant/query', {
      body: request,
      signal,
      headers: { Accept: 'text/event-stream', 'X-Tenant-Id': tenantId },
      onEvent(event, data) {
        if (event === 'actions') {
          onEvent(event, readActions(JSON.parse(data)));
          return;
        }
        if (event !== 'context' && event !== 'sources' && event !== 'delta' && event !== 'error' && event !== 'done') return;
        if (event === 'done' || event === 'error') completed = true;
        onEvent(event, JSON.parse(data));
      },
    });
    if (!completed && !signal.aborted) throw new Error('Incomplete assistant stream');
  },
};
