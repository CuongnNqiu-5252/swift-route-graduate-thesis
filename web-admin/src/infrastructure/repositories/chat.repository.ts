import api, { API_BASE_URL } from '@/lib/api';

type ChatContact = { id: string; name: string };
type Conversation = { peer_id: string; body: string | null; has_image: boolean; unread: number };
export type ChatInbox = { user_id: string; contacts: ChatContact[]; conversations: Conversation[] };
export type ChatMessage = {
  id: number;
  sender_id: string;
  recipient_id: string;
  client_message_id: string;
  body: string | null;
  has_image: boolean;
  created_at: string;
};

export const chatRepository = {
  async loadImage(id: number, signal: AbortSignal) {
    return (await api.get<Blob>(`/chat/images/${id}`, { responseType: 'blob', signal })).data;
  },
  async getInbox() {
    return (await api.get<ChatInbox>('/chat/inbox')).data;
  },
  async getMessages(peerId: string, before?: number) {
    return (await api.get<ChatMessage[]>(`/chat/messages/${peerId}`, { params: { before } })).data;
  },
  async createSocketTicket() {
    const { data } = await api.post<{ ticket: string }>('/chat/socket-tickets');
    return data.ticket;
  },
  socketUrl() {
    const url = new URL(API_BASE_URL, window.location.origin);
    url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:';
    url.pathname = url.pathname.replace(/\/api\/?$/, '').replace(/\/$/, '') + '/ws/chat';
    return url;
  },
  async markRead(peerId: string, messageId: number) {
    await api.put(`/chat/messages/${peerId}/read/${messageId}`);
  },
  async uploadImage(peerId: string, file: File, clientMessageId: string) {
    const form = new FormData();
    form.append('file', file);
    form.append('client_message_id', clientMessageId);
    return (await api.post<ChatMessage>(`/chat/messages/${peerId}/image`, form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })).data;
  },
};
