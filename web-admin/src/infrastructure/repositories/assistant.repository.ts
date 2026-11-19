import { streamSse } from '@/lib/api';

export interface AssistantTurn {
  role: 'user' | 'assistant';
  text: string;
}

export interface AssistantRouteReference {
  planId: string;
  label: string;
}

export const assistantRepository = {
  async stream(
    request: { question: string; history: AssistantTurn[]; language: 'vi' | 'en'; planId?: string },
    tenantId: string,
    signal: AbortSignal,
    onEvent: (event: 'delta' | 'context' | 'error' | 'done', value: unknown) => void,
  ) {
    let completed = false;
    await streamSse('/orders/assistant/query', {
      body: request,
      signal,
      headers: { Accept: 'text/event-stream', 'X-Tenant-Id': tenantId },
      onEvent(event, data) {
        if (event !== 'context' && event !== 'delta' && event !== 'error' && event !== 'done') return;
        if (event === 'done' || event === 'error') completed = true;
        onEvent(event, JSON.parse(data));
      },
    });
    if (!completed && !signal.aborted) throw new Error('Incomplete assistant stream');
  },
};
