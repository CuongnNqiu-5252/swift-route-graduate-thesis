export const FLOW_PATHS = {
  import: ['schedule', 'prepare', 'import'],
  planning: ['schedule', 'plan'],
} as const;

export type FlowKind = keyof typeof FLOW_PATHS;
export type FlowStep = (typeof FLOW_PATHS)[FlowKind][number];

export function isRunnableFlow(
  kind: FlowKind,
  nodes: { id: string; data: { step: FlowStep } }[],
  edges: { source: string; target: string }[],
) {
  const path = FLOW_PATHS[kind];
  if (nodes.length !== path.length || edges.length !== path.length - 1) return false;
  const ids = path.map((step) => nodes.find((node) => node.data.step === step)?.id);
  if (ids.some((id) => !id) || new Set(ids).size !== path.length) return false;
  return ids.slice(0, -1).every((source, index) =>
    edges.some((edge) => edge.source === source && edge.target === ids[index + 1]));
}
