const frameIntervalMs = 16;
const catchUpFrames = 20;

/**
 * Reveal the available text progressively when the provider groups several
 * characters into one SSE event. It begins draining before the stream ends.
 */
export function createAssistantTextStream(
  append: (text: string) => void,
  signal: AbortSignal,
  reduceMotion: boolean,
) {
  const segmenter = typeof Intl.Segmenter === 'function'
    ? new Intl.Segmenter(undefined, { granularity: 'grapheme' })
    : undefined;
  const pending: string[] = [];
  let timer: ReturnType<typeof setTimeout> | undefined;
  let ended = false;
  let complete!: () => void;
  const drained = new Promise<void>((resolve) => { complete = resolve; });

  function settle() {
    signal.removeEventListener('abort', abort);
    complete();
  }

  function abort() {
    if (timer !== undefined) clearTimeout(timer);
    timer = undefined;
    pending.length = 0;
    settle();
  }

  function reveal() {
    timer = undefined;
    const count = Math.max(1, Math.ceil(pending.length / catchUpFrames));
    append(pending.splice(0, count).join(''));
    if (pending.length) timer = setTimeout(reveal, frameIntervalMs);
    else if (ended) settle();
  }

  signal.addEventListener('abort', abort, { once: true });

  return {
    push(text: string) {
      if (!text || ended || signal.aborted) return;
      if (reduceMotion) {
        append(text);
        return;
      }
      const characters = segmenter ? Array.from(segmenter.segment(text), ({ segment }) => segment) : Array.from(text);
      for (const character of characters) pending.push(character);
      if (timer === undefined) timer = setTimeout(reveal, frameIntervalMs);
    },
    finish() {
      ended = true;
      if (timer === undefined) settle();
      return drained;
    },
  };
}
