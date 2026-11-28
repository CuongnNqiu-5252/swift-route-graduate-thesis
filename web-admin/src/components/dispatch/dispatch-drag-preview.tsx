import { Package } from 'lucide-react';
import { createPortal } from 'react-dom';

export function DispatchDragPreview({ x, y, color, label }: {
  x: number;
  y: number;
  color: string;
  label: string;
}) {
  return createPortal(
    <div aria-hidden="true" className="pointer-events-none fixed z-[100] grid size-0 place-items-center"
      style={{ left: x, top: y }}>
      <span className="absolute bottom-3 flex max-w-52 items-center gap-2 whitespace-nowrap rounded-sm bg-white px-2.5 py-1.5 text-xs font-semibold text-slate-950 shadow-lg ring-1 ring-slate-200">
        <Package size={14} className="shrink-0" style={{ color }} />
        <span className="truncate">{label}</span>
      </span>
      <span className="absolute size-3 rounded-full border-2 border-white shadow-md" style={{ backgroundColor: color }} />
    </div>,
    document.body,
  );
}
