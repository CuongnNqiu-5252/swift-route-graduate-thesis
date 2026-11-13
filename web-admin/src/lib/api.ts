import axios, { AxiosError, AxiosResponse, InternalAxiosRequestConfig } from 'axios';

// Standard API response wrapper from backend
export interface ApiResponse<T> {
  data: T;
  error: { code: string; message: string } | null;
  timestamp: string;
}

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8000/api';

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Mutex flag and queued requests to handle concurrent 401 errors cleanly
let isRefreshing = false;
let failedQueue: Array<{
  resolve: (token: string) => void;
  reject: (error: unknown) => void;
}> = [];

const processQueue = (error: unknown, token: string | null = null) => {
  failedQueue.forEach((prom) => {
    if (error) {
      prom.reject(error);
    } else if (token) {
      prom.resolve(token);
    }
  });
  failedQueue = [];
};

/**
 * Clear all cached auth credentials from storage and notify listeners
 */
export const clearSessionAndRedirect = () => {
  if (typeof window !== 'undefined') {
    localStorage.removeItem('access_token');
    localStorage.removeItem('refresh_token');
    localStorage.removeItem('auth_user');
    localStorage.removeItem('tenant_id');
    window.dispatchEvent(new CustomEvent('auth:logout'));
    const loginPath = window.location.pathname.startsWith('/admin') ? '/admin/login' : '/login';
    if (window.location.pathname !== loginPath) {
      window.location.href = loginPath;
    }
  }
};

/**
 * Perform refresh token exchange with the backend
 */
export async function refreshAccessToken(): Promise<string> {
  if (typeof window === 'undefined') {
    throw new Error('Cannot refresh token in server environment');
  }

  const refreshToken = localStorage.getItem('refresh_token');
  if (!refreshToken || refreshToken === 'undefined' || refreshToken === 'null') {
    throw new Error('No refresh token available');
  }

  // Use raw axios instance to prevent interceptor recursion
  const response = await axios.post<ApiResponse<{ access_token: string; refresh_token: string }>>(
    `${API_BASE_URL}/auth/refresh?refreshToken=${encodeURIComponent(refreshToken)}`,
    { refreshToken },
    {
      headers: { 'Content-Type': 'application/json' },
    }
  );

  const payload = response.data?.data;
  if (!payload?.access_token) {
    throw new Error('Invalid refresh token response from server');
  }

  localStorage.setItem('access_token', payload.access_token);
  if (payload.refresh_token) {
    localStorage.setItem('refresh_token', payload.refresh_token);
  }

  // A refresh retains its established format; restore the active membership context explicitly.
  const selectedTenant = localStorage.getItem('tenant_id');
  if (selectedTenant) {
    const switched = await axios.post<ApiResponse<{ access_token: string }>>(
      `${API_BASE_URL}/auth/tenants/${selectedTenant}/switch`, null,
      { headers: { Authorization: `Bearer ${payload.access_token}` } }
    );
    payload.access_token = switched.data.data.access_token;
    localStorage.setItem('access_token', payload.access_token);
  }

  window.dispatchEvent(
    new CustomEvent('auth:token-refreshed', {
      detail: { token: payload.access_token },
    })
  );

  return payload.access_token;
}

// Request interceptor: Attach current access token and tenant ID from localStorage
api.interceptors.request.use((config) => {
  if (typeof window !== 'undefined') {
    const token = localStorage.getItem('access_token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }

    // Attach X-Tenant-Id if not explicitly set
    if (!config.headers['X-Tenant-Id']) {
      const explicitTenant = localStorage.getItem('tenant_id');
      if (explicitTenant) {
        config.headers['X-Tenant-Id'] = explicitTenant;
      } else {
        const userStr = localStorage.getItem('auth_user');
        if (userStr) {
          try {
            const user = JSON.parse(userStr);
            const tenantId = user.tenantId || user.tenant_id;
            if (tenantId) {
              config.headers['X-Tenant-Id'] = tenantId;
            }
          } catch {
            // ignore JSON parse error
          }
        }
      }
    }
  }
  return config;
});

// Response interceptor: Unwrap API response and handle transparent token refresh on 401
api.interceptors.response.use(
  (response: AxiosResponse<ApiResponse<unknown>>) => {
    if (response.config.responseType === 'stream' || response.config.baseURL === '') {
      const raw = response.data as { error?: unknown } | undefined;
      if (response.config.baseURL === '' && raw && typeof raw === 'object' && 'error' in raw && raw.error) {
        const msg =
          typeof raw.error === 'string'
            ? raw.error
            : (raw.error as { message?: string }).message || 'Lỗi kết nối máy chủ';
        return Promise.reject(new Error(msg));
      }
      return response;
    }

    const body = response.data;
    if (body?.error) {
      return Promise.reject(new Error(body.error.message));
    }
    // Return synthetic response where .data is the inner domain payload
    return { ...response, data: body.data } as AxiosResponse;
  },
  async (error: AxiosError<ApiResponse<unknown>>) => {
    if (axios.isCancel(error) || error.name === 'AbortError') {
      return Promise.reject(error);
    }

    const originalRequest = error.config as (InternalAxiosRequestConfig & { _retry?: boolean }) | undefined;

    const requestUrl = originalRequest?.url || '';
    const isAuthEndpoint =
      requestUrl.includes('/auth/login') ||
      requestUrl.includes('/auth/refresh') ||
      requestUrl.includes('/auth/register');

    const status = error.response?.status;

    // Handle 401 Unauthorized by attempting a token refresh
    if (status === 401 && !isAuthEndpoint && originalRequest && !originalRequest._retry) {
      if (typeof window !== 'undefined') {
        const refreshToken = localStorage.getItem('refresh_token');
        if (!refreshToken) {
          clearSessionAndRedirect();
          return Promise.reject(error);
        }

        if (isRefreshing) {
          // If refresh is already in progress, wait for the pending refresh to finish
          return new Promise<AxiosResponse>((resolve, reject) => {
            failedQueue.push({
              resolve: (newToken: string) => {
                originalRequest.headers.Authorization = `Bearer ${newToken}`;
                resolve(api(originalRequest));
              },
              reject: (err: unknown) => {
                reject(err);
              },
            });
          });
        }

        originalRequest._retry = true;
        isRefreshing = true;

        try {
          const newAccessToken = await refreshAccessToken();
          processQueue(null, newAccessToken);
          originalRequest.headers.Authorization = `Bearer ${newAccessToken}`;
          return api(originalRequest);
        } catch (refreshErr) {
          processQueue(refreshErr, null);
          clearSessionAndRedirect();
          return Promise.reject(refreshErr);
        } finally {
          isRefreshing = false;
        }
      }
    }

    const errorPayload = error.response?.data as
      | { error?: { message?: string } | string; message?: string }
      | undefined;
    const nestedMessage =
      typeof errorPayload?.error === 'string' ? errorPayload.error : errorPayload?.error?.message;
    const msg =
      nestedMessage ||
      errorPayload?.message ||
      error.message ||
      'Lỗi kết nối máy chủ';

    const rejected = new Error(msg) as Error & { status?: number };
    if (status !== undefined) {
      rejected.status = status;
    }
    return Promise.reject(rejected);
  }
);

/**
 * Open a Server-Sent Events stream through the shared Axios client
 * so auth headers, tenant context, and 401 token refresh stay centralized.
 */
export async function streamSse(
  path: string,
  options: {
    signal: AbortSignal;
    headers?: Record<string, string>;
    onOpen?: () => void | Promise<void>;
    onEvent: (eventName: string, data: string) => void;
  },
): Promise<void> {
  const response = await api.get<ReadableStream<Uint8Array>>(path, {
    adapter: 'fetch',
    responseType: 'stream',
    headers: options.headers,
    signal: options.signal,
  });

  const stream = response.data;
  if (!stream || typeof stream.getReader !== 'function') {
    throw new Error('Máy chủ không trả về luồng SSE hợp lệ');
  }

  await options.onOpen?.();

  const reader = stream.getReader();
  const decoder = new TextDecoder();
  let buffer = '';

  while (!options.signal.aborted) {
    const chunk = await reader.read();
    if (chunk.done) break;

    buffer += decoder.decode(chunk.value, { stream: true }).replaceAll('\r\n', '\n');
    const blocks = buffer.split('\n\n');
    buffer = blocks.pop() ?? '';

    for (const block of blocks) {
      let eventName = 'message';
      const dataLines: string[] = [];
      for (const line of block.split('\n')) {
        if (line.startsWith('event:')) eventName = line.slice(6).trim();
        if (line.startsWith('data:')) dataLines.push(line.slice(5).trim());
      }
      if (dataLines.length > 0) {
        options.onEvent(eventName, dataLines.join('\n'));
      }
    }
  }
}

export default api;
