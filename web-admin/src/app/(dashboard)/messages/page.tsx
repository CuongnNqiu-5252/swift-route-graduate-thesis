'use client';

import { useAuth } from '@/application/contexts/auth-context';
import { chatRepository, type ChatInbox as Inbox, type ChatMessage as Message } from '@/infrastructure/repositories/chat.repository';
import { ArrowLeft, ImagePlus, SendHorizontal, X } from 'lucide-react';
import { useSearchParams } from 'next/navigation';
import { Suspense, useCallback, useEffect, useRef, useState } from 'react';

type Pending = { id: string; peer: string; body: string; file: File | null };
type OptimisticImage = { clientMessageId: string; peer: string; previewUrl: string; createdAt: string };
type ChatEvent = { type: 'ready' | 'inbox.updated' | 'heartbeat' }
  | { type: 'message.saved'; message: Message }
  | { type: 'message.error'; client_message_id: string };

function messageId() {
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6] & 15) | 64;
  bytes[8] = (bytes[8] & 63) | 128;
  const hex = Array.from(bytes, (value) => value.toString(16).padStart(2, '0')).join('');
  return [hex.slice(0, 8), hex.slice(8, 12), hex.slice(12, 16), hex.slice(16, 20), hex.slice(20)].join('-');
}

const MAX_IMAGE_BYTES = 5 * 1024 * 1024;
const ALLOWED_IMAGE_TYPES = new Set(['image/jpeg', 'image/png', 'image/webp']);

function ChatImage({ id, cachedUrl }: { id: number; cachedUrl?: string }) {
  const [fetchedUrl, setFetchedUrl] = useState<string>();
  const [failed, setFailed] = useState(false);
  const [visible, setVisible] = useState(false);
  const container = useRef<HTMLDivElement>(null);
  const url = cachedUrl ?? fetchedUrl;

  useEffect(() => {
    if (cachedUrl) return;
    const node = container.current;
    if (!node) return;
    const observer = new IntersectionObserver((entries) => {
      if (entries.some((entry) => entry.isIntersecting)) {
        observer.disconnect();
        setVisible(true);
      }
    });
    observer.observe(node);
    return () => observer.disconnect();
  }, [cachedUrl]);

  useEffect(() => {
    if (cachedUrl || !visible) return;
    const request = new AbortController();
    let preview: string | undefined;
    chatRepository.loadImage(id, request.signal)
      .then((data) => {
        if (request.signal.aborted) return;
        preview = URL.createObjectURL(data);
        setFetchedUrl(preview);
      })
      .catch(() => { if (!request.signal.aborted) setFailed(true); });
    return () => {
      request.abort();
      if (preview) URL.revokeObjectURL(preview);
    };
  }, [cachedUrl, id, visible]);

  return <div ref={container}>
    {failed ? <p className="text-sm">Không tải được ảnh</p>
      : url ? <a href={url} target="_blank" rel="noreferrer" aria-label="Mở ảnh">
        {/* eslint-disable-next-line @next/next/no-img-element -- Authorized binary download creates a local object URL. */}
        <img src={url} alt="Ảnh trong tin nhắn" onError={() => setFailed(true)} className="max-h-64 max-w-full rounded-md object-contain" />
      </a> : <p className="flex h-24 min-w-32 items-center text-sm">Đang tải ảnh…</p>}
  </div>;
}

function Chat({ initialPeer }: { initialPeer: string }) {
  const [inbox, setInbox] = useState<Inbox>();
  const [peer, setPeer] = useState(initialPeer);
  const [query, setQuery] = useState('');
  const [messages, setMessages] = useState<Message[]>([]);
  const [older, setOlder] = useState(false);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [body, setBody] = useState('');
  const [file, setFile] = useState<File | null>(null);
  const [filePreviewUrl, setFilePreviewUrl] = useState<string>();
  const [optimisticImages, setOptimisticImages] = useState<OptimisticImage[]>([]);
  const [savedImageUrls, setSavedImageUrls] = useState<Record<number, string>>({});
  const [connected, setConnected] = useState(false);
  const [error, setError] = useState('');
  const [sending, setSending] = useState(false);
  const [retry, setRetry] = useState<Pending>();
  const socket = useRef<WebSocket | null>(null);
  const pending = useRef<Pending | undefined>(undefined);
  const ackTimeout = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const scroll = useRef<HTMLDivElement>(null);
  const composerRef = useRef<HTMLTextAreaElement>(null);
  const requestVersions = useRef({ inbox: 0, history: 0 });
  const followLatest = useRef(true);
  const ownedUrlsRef = useRef<Set<string>>(new Set());
  const filePreviewUrlRef = useRef<string | undefined>(undefined);

  const releaseAllLocalUrls = useCallback(() => {
    ownedUrlsRef.current.forEach((url) => URL.revokeObjectURL(url));
    ownedUrlsRef.current.clear();
  }, []);

  useEffect(() => releaseAllLocalUrls, [releaseAllLocalUrls]);

  const clearSelectedFile = useCallback((revokePreview = true) => {
    const current = filePreviewUrlRef.current;
    filePreviewUrlRef.current = undefined;
    if (current && revokePreview) {
      URL.revokeObjectURL(current);
      ownedUrlsRef.current.delete(current);
    }
    setFile(null);
    setFilePreviewUrl(undefined);
  }, []);

  const attachImageFile = useCallback((picked: File) => {
    if (picked.size > MAX_IMAGE_BYTES || !ALLOWED_IMAGE_TYPES.has(picked.type)) {
      setError('Chọn ảnh JPG, PNG hoặc WebP tối đa 5 MB.');
      return;
    }
    const previous = filePreviewUrlRef.current;
    if (previous) {
      URL.revokeObjectURL(previous);
      ownedUrlsRef.current.delete(previous);
    }
    const nextUrl = URL.createObjectURL(picked);
    ownedUrlsRef.current.add(nextUrl);
    filePreviewUrlRef.current = nextUrl;
    setError('');
    setFile(picked);
    setFilePreviewUrl(nextUrl);
    requestAnimationFrame(() => composerRef.current?.focus());
  }, []);

  const refreshInbox = useCallback(async () => {
    const version = ++requestVersions.current.inbox;
    try {
      const data = await chatRepository.getInbox();
      if (version === requestVersions.current.inbox) setInbox(data);
    } catch {
      if (version === requestVersions.current.inbox) setError('Không tải được danh sách tin nhắn.');
    }
  }, []);

  const contact = inbox?.contacts.find((entry) => entry.id === peer);
  const activePeer = contact?.id;
  const unread = inbox?.conversations.find((entry) => entry.peer_id === activePeer)?.unread ?? 0;

  const fetchHistory = useCallback(async (id: string, before?: number) => {
    const version = ++requestVersions.current.history;
    setHistoryLoading(true);
    try {
      const data = await chatRepository.getMessages(id, before);
      if (version !== requestVersions.current.history) return;
      if (before && scroll.current) {
        const position = scroll.current.scrollTop;
        const height = scroll.current.scrollHeight;
        requestAnimationFrame(() => {
          if (version === requestVersions.current.history && scroll.current) {
            scroll.current.scrollTop = position + scroll.current.scrollHeight - height;
          }
        });
      }
      setMessages((previous) => {
        const samePair = previous.filter((message) => message.sender_id === id || message.recipient_id === id);
        const combined = before ? [...samePair, ...data] : [...data, ...samePair.filter((message) => message.id > (data[0]?.id ?? 0))];
        return Array.from(new Map(combined.map((message) => [message.id, message])).values()).sort((a, b) => a.id - b.id);
      });
      setOlder(data.length === 50);
    } catch {
      if (version === requestVersions.current.history) setError('Không tải được cuộc trò chuyện.');
    } finally {
      if (version === requestVersions.current.history) setHistoryLoading(false);
    }
  }, []);

  const onEvent = useRef<(event: ChatEvent) => void>(() => { });
  useEffect(() => {
    onEvent.current = (event) => {
      if (event.type === 'ready') {
        setConnected(true);
        void refreshInbox();
        if (activePeer) void fetchHistory(activePeer);
      }
      if (event.type === 'inbox.updated') void refreshInbox();
      if (event.type === 'message.error' && pending.current?.id === event.client_message_id) {
        clearTimeout(ackTimeout.current);
        pending.current = undefined;
        setSending(false);
        setError('Không gửi được tin nhắn. Bạn có thể thử lại.');
      }
      if (event.type === 'message.saved') {
        const message = event.message;
        if (pending.current?.id === message.client_message_id && inbox?.user_id === message.sender_id) {
          clearTimeout(ackTimeout.current);
          pending.current = undefined;
          setSending(false);
          setRetry(undefined);
          setBody('');
        }
        if (activePeer && (activePeer === message.sender_id || activePeer === message.recipient_id)) {
          setMessages((previous) => previous.some((item) => item.id === message.id)
            ? previous : [...previous, message].sort((a, b) => a.id - b.id));
        }
        void refreshInbox();
      }
    };
  }, [activePeer, fetchHistory, inbox?.user_id, refreshInbox]);

  useEffect(() => {
    const versions = requestVersions.current;
    let stopped = false;
    let delay = 1000;
    let reconnect: ReturnType<typeof setTimeout> | undefined;
    const connect = async () => {
      try {
        const ticket = await chatRepository.createSocketTicket();
        if (stopped) return;
        const connection = new WebSocket(chatRepository.socketUrl());
        socket.current = connection;
        connection.onopen = () => connection.send(JSON.stringify({ type: 'authenticate', ticket: ticket }));
        connection.onmessage = ({ data: payload }) => {
          if (stopped) return;
          const event = JSON.parse(payload) as ChatEvent;
          if (event.type === 'ready') delay = 1000;
          onEvent.current(event);
        };
        connection.onclose = () => {
          if (stopped) return;
          setConnected(false);
          schedule();
        };
      } catch {
        schedule();
      }
    };
    const schedule = () => {
      if (stopped) return;
      reconnect = setTimeout(() => void connect(), delay);
      delay = Math.min(delay * 2, 15000);
    };
    const start = setTimeout(() => { void refreshInbox(); void connect(); }, 0);
    return () => {
      stopped = true;
      clearTimeout(start);
      clearTimeout(reconnect);
      clearTimeout(ackTimeout.current);
      socket.current?.close();
      ++versions.inbox;
      ++versions.history;
    };
  }, [refreshInbox]);

  useEffect(() => {
    if (!activePeer) return;
    const versions = requestVersions.current;
    const timer = setTimeout(() => void fetchHistory(activePeer), 0);
    return () => { clearTimeout(timer); ++versions.history; };
  }, [activePeer, fetchHistory]);

  const visibleOptimisticImages = optimisticImages.filter(
    (item) => item.peer === activePeer && !messages.some((message) => message.client_message_id === item.clientMessageId),
  );

  useEffect(() => {
    if (followLatest.current && scroll.current) scroll.current.scrollTop = scroll.current.scrollHeight;
  }, [messages, visibleOptimisticImages.length]);

  const latestReceived = messages.filter((message) => message.sender_id === activePeer).at(-1)?.id;
  useEffect(() => {
    if (!activePeer || !latestReceived || !unread) return;
    const mark = () => {
      if (document.visibilityState !== 'visible') return;
      void chatRepository.markRead(activePeer, latestReceived).then(refreshInbox).catch(() => { });
    };
    mark();
    document.addEventListener('visibilitychange', mark);
    return () => document.removeEventListener('visibilitychange', mark);
  }, [activePeer, latestReceived, refreshInbox, unread]);

  function selectPeer(id: string) {
    if (sending) return;
    ++requestVersions.current.history;
    setPeer(id);
    setBody('');
    clearSelectedFile(true);
    setOptimisticImages([]);
    setSavedImageUrls({});
    releaseAllLocalUrls();
    setRetry(undefined);
    setMessages([]);
    setOlder(false);
    setError('');
    followLatest.current = true;
  }

  async function send() {
    if (!activePeer || sending || (!file && (!body.trim() || !connected))) return;
    const draft = retry?.peer === activePeer && retry.body === body.trim() && retry.file === file
      ? retry : { id: messageId(), peer: activePeer, body: body.trim(), file };
    setRetry(draft);
    setSending(true);
    setError('');
    followLatest.current = true;
    try {
      if (file) {
        const previewUrl = filePreviewUrl ?? URL.createObjectURL(file);
        ownedUrlsRef.current.add(previewUrl);
        setOptimisticImages((current) => [
          ...current,
          { clientMessageId: draft.id, peer: activePeer, previewUrl, createdAt: new Date().toISOString() },
        ]);
        // Keep the local URL alive so the saved bubble renders immediately without re-downloading.
        clearSelectedFile(false);
        const data = await chatRepository.uploadImage(activePeer, file, draft.id);
        setSavedImageUrls((current) => ({ ...current, [data.id]: previewUrl }));
        setOptimisticImages((current) => current.filter((item) => item.clientMessageId !== draft.id));
        onEvent.current({ type: 'message.saved', message: data });
        setRetry(undefined);
        setSending(false);
      } else {
        pending.current = draft;
        socket.current?.send(JSON.stringify({ type: 'message.send', peer_id: activePeer, client_message_id: draft.id, body: draft.body }));
        ackTimeout.current = setTimeout(() => {
          pending.current = undefined;
          setSending(false);
          setError('Chưa nhận được xác nhận. Bạn có thể gửi lại.');
        }, 8000);
      }
    } catch {
      pending.current = undefined;
      if (draft.file) {
        setOptimisticImages((current) => current.filter((item) => item.clientMessageId !== draft.id));
        setFile(draft.file);
        const restoredUrl = URL.createObjectURL(draft.file);
        ownedUrlsRef.current.add(restoredUrl);
        setFilePreviewUrl(restoredUrl);
      }
      setSending(false);
      setError('Không gửi được tin nhắn. Bạn có thể thử lại.');
    }
  }

  function handlePasteImage(event: React.ClipboardEvent<HTMLElement>) {
    if (!activePeer || sending || event.defaultPrevented) return;
    const items = Array.from(event.clipboardData?.items ?? []);
    const imageItem = items.find((item) => item.type.startsWith('image/'));
    const pastedFile = imageItem?.getAsFile() ?? Array.from(event.clipboardData?.files ?? []).find((f) => f.type.startsWith('image/'));
    if (!pastedFile) return;
    event.preventDefault();
    attachImageFile(pastedFile);
  }

  const conversationByPeer = new Map(inbox?.conversations.map((item) => [item.peer_id, item]));
  const contactById = new Map(inbox?.contacts.map((item) => [item.id, item]));
  const contacts = [
    ...(inbox?.conversations.flatMap((item) => contactById.get(item.peer_id) ?? []) ?? []),
    ...(inbox?.contacts.filter((item) => !conversationByPeer.has(item.id)) ?? []),
  ].filter((item) => item.name.toLocaleLowerCase().includes(query.trim().toLocaleLowerCase()));

  return <section data-ui="inbox" className="flex h-[calc(100dvh-9rem)] min-h-[28rem] flex-col text-slate-900 lg:h-[calc(100dvh-6rem)]">
    <header className="mb-4 flex items-center justify-between gap-4">
      <h1 className="text-xl font-semibold tracking-tight">Tin nhắn</h1>
      <span className="text-xs text-slate-500">{connected ? 'Đã kết nối' : 'Đang kết nối…'}</span>
    </header>
    {error && <p role="alert" className="mb-3 text-sm text-red-700">{error}</p>}
    <div className="grid min-h-0 flex-1 overflow-hidden rounded-lg border border-slate-200 bg-white md:grid-cols-[18rem_minmax(0,1fr)]">
      <aside className={`min-h-0 flex-col border-slate-200 md:flex md:border-r ${contact ? 'hidden' : 'flex'}`}>
        <div className="border-b border-slate-200 p-3">
          <input aria-label="Tìm người nhận" placeholder="Tìm người nhận" value={query} onChange={(event) => setQuery(event.target.value)}
            className="h-10 w-full rounded-md border border-slate-300 px-3 text-sm" />
        </div>
        <div className="overflow-y-auto">
          {contacts.map((entry) => {
            const last = conversationByPeer.get(entry.id);
            return <button key={entry.id} type="button" disabled={sending} aria-pressed={entry.id === activePeer} onClick={() => { if (entry.id !== activePeer) selectPeer(entry.id); }}
              className={`flex w-full items-center justify-between gap-3 border-b border-slate-100 px-4 py-3 text-left hover:bg-slate-50 ${entry.id === activePeer ? 'bg-slate-100' : ''}`}>
              <span className="min-w-0"><span className="block truncate text-sm font-medium">{entry.name}</span>
                {last && <span className="block truncate text-xs text-slate-600">{last.has_image ? 'Ảnh' : last.body}</span>}
              </span>
              {!!last?.unread && <span className="rounded-full bg-blue-100 px-2 py-0.5 text-xs font-medium text-blue-800">{last.unread}</span>}
            </button>;
          })}
          {!contacts.length && <p className="p-4 text-sm text-slate-600">{inbox ? 'Không tìm thấy người nhận.' : 'Đang tải danh sách…'}</p>}
        </div>
      </aside>
      {contact ? <div className="flex min-h-0 min-w-0 flex-col" onPaste={handlePasteImage}>
        <header className="flex min-h-14 items-center gap-2 border-b border-slate-200 px-4">
          <button type="button" aria-label="Về danh sách tin nhắn" className="p-2 md:hidden" disabled={sending} onClick={() => selectPeer('')}><ArrowLeft size={18} /></button>
          <h2 className="truncate text-sm font-semibold">{contact.name}</h2>
        </header>
        <div ref={scroll} className="min-h-0 flex-1 space-y-3 overflow-y-auto px-4 py-3" onScroll={() => {
          if (scroll.current) followLatest.current = scroll.current.scrollHeight - scroll.current.scrollTop - scroll.current.clientHeight < 80;
        }}>
          {older && <button type="button" disabled={historyLoading} onClick={() => void fetchHistory(contact.id, messages[0]?.id)}
            className="mx-auto block text-sm font-medium text-blue-800">Xem tin cũ hơn</button>}
          {!messages.length && !visibleOptimisticImages.length && <p className="pt-4 text-center text-sm text-slate-500">{historyLoading ? 'Đang tải tin nhắn…' : 'Chưa có tin nhắn.'}</p>}
          {messages.map((message) => <div key={message.id} className={`flex ${message.sender_id === inbox?.user_id ? 'justify-end' : 'justify-start'}`}>
            <div className={`max-w-[85%] rounded-lg px-3 py-2 md:max-w-[70%] ${message.sender_id === inbox?.user_id ? 'bg-blue-50' : 'bg-slate-100'}`}>
              {message.has_image && <ChatImage id={message.id} cachedUrl={savedImageUrls[message.id]} />}
              {message.body && <p className="whitespace-pre-wrap break-words text-sm">{message.body}</p>}
              <time dateTime={message.created_at} className="mt-1 block text-[11px] text-slate-600">
                {new Date(message.created_at).toLocaleString('vi-VN', { hour: '2-digit', minute: '2-digit', day: '2-digit', month: '2-digit' })}
              </time>
            </div>
          </div>)}
          {visibleOptimisticImages.map((item) => <div key={item.clientMessageId} className="flex justify-end">
            <div className="max-w-[85%] rounded-lg bg-blue-50 px-3 py-2 opacity-80 md:max-w-[70%]">
              {/* eslint-disable-next-line @next/next/no-img-element -- Local object URL for immediate upload preview. */}
              <img src={item.previewUrl} alt="Ảnh đang gửi" className="max-h-64 max-w-full rounded-md object-contain" />
              <p className="mt-1 text-[11px] text-slate-600">Đang gửi…</p>
            </div>
          </div>)}
        </div>
        <form className="border-t border-slate-200 p-3" onSubmit={(event) => { event.preventDefault(); void send(); }}>
          <div className="flex items-end gap-2">
            <label aria-label="Chọn ảnh" className="flex h-10 w-10 shrink-0 cursor-pointer items-center justify-center rounded-lg border border-slate-300 bg-white hover:bg-slate-50 focus-within:ring-2 focus-within:ring-blue-600">
              <ImagePlus size={18} />
              <input type="file" className="sr-only" disabled={sending} accept="image/jpeg,image/png,image/webp" aria-label="Chọn ảnh"
                onChange={(event) => {
                  const picked = event.target.files?.[0];
                  event.target.value = '';
                  if (picked) attachImageFile(picked);
                }} />
            </label>
            <div className="flex min-w-0 flex-1 flex-col rounded-xl border border-slate-300 bg-white focus-within:border-blue-600 focus-within:ring-1 focus-within:ring-blue-600">
              {file && filePreviewUrl && (
                <div className="px-3 pt-2.5">
                  <div className="relative inline-block">
                    {/* eslint-disable-next-line @next/next/no-img-element -- Local object URL preview before sending. */}
                    <img
                      src={filePreviewUrl}
                      alt="Ảnh xem trước"
                      className="h-20 max-w-40 rounded-lg border border-slate-200 bg-slate-50 object-contain"
                    />
                    <button
                      type="button"
                      aria-label="Bỏ ảnh"
                      disabled={sending}
                      onClick={() => clearSelectedFile(true)}
                      className="absolute -right-2 -top-2 flex size-5 items-center justify-center rounded-full bg-slate-800 text-white shadow-sm hover:bg-slate-950"
                    >
                      <X size={12} aria-hidden="true" />
                    </button>
                  </div>
                </div>
              )}
              <textarea ref={composerRef} aria-label="Nội dung tin nhắn"
                rows={1} maxLength={4000}
                disabled={sending} readOnly={!!file} value={body} onChange={(event) => setBody(event.target.value)}
                className="max-h-32 min-h-10 w-full resize-none rounded-xl bg-transparent px-3 py-2 text-sm outline-none read-only:text-slate-500"
                onKeyDown={(event) => {
                  if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) {
                    event.preventDefault();
                    void send();
                  }
                }} />
            </div>
            <button type="submit" disabled={sending || (!file && (!body.trim() || !connected))}
              className="flex h-10 shrink-0 items-center gap-2 rounded-md bg-slate-900 px-3 text-sm font-medium text-white disabled:opacity-50">
              <SendHorizontal size={16} />{sending ? 'Đang gửi' : retry ? 'Gửi lại' : 'Gửi'}
            </button>
          </div>
        </form>
      </div> : <p className="hidden place-content-center text-center text-sm text-slate-500 md:grid">
        {peer && inbox ? 'Người nhận hiện không có trong danh bạ chat.' : 'Chọn người nhận để mở cuộc trò chuyện.'}
      </p>}
    </div>
  </section>;
}

function ScopedChat() {
  const { user, isLoading } = useAuth();
  const search = useSearchParams();
  if (isLoading) return <p className="text-sm text-slate-500">Đang tải…</p>;
  if (!user?.tenantId || !['OWNER', 'TENANT_ADMIN', 'OPERATOR'].includes(user.tenantRole ?? '')) {
    return <p className="text-sm text-slate-600">Tài khoản này không có quyền nhắn tin.</p>;
  }
  const initialPeer = search.get('peer') ?? '';
  return <Chat key={`${user.tenantId}:${user.id}:${initialPeer}`} initialPeer={initialPeer} />;
}

export default function MessagesPage() {
  return <Suspense fallback={<p className="text-sm text-slate-500">Đang tải…</p>}><ScopedChat /></Suspense>;
}
