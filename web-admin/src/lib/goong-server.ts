const GOONG_BASE_URL = 'https://rsapi.goong.io';

export class GoongConfigurationError extends Error {}

export async function requestGoong(path: string, params: URLSearchParams) {
  const apiKey = process.env.GOONG_API_KEY;
  if (!apiKey) {
    throw new GoongConfigurationError('GOONG_API_KEY chưa được cấu hình cho Web Admin.');
  }

  params.set('api_key', apiKey);
  const response = await fetch(`${GOONG_BASE_URL}${path}?${params.toString()}`, {
    cache: 'no-store',
    signal: AbortSignal.timeout(8000),
  });

  if (!response.ok) {
    throw new Error(`Goong API trả về HTTP ${response.status}`);
  }

  return response.json() as Promise<Record<string, unknown>>;
}

export function goongErrorResponse(error: unknown) {
  const isConfigurationError = error instanceof GoongConfigurationError;
  return Response.json(
    {
      error: isConfigurationError
        ? error.message
        : 'Không thể xác định địa chỉ từ Goong. Vui lòng thử lại hoặc nhập địa chỉ thủ công.',
    },
    { status: isConfigurationError ? 503 : 502 },
  );
}
