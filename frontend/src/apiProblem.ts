interface ApiProblem {
  code: string
  errors?: unknown[]
}

// Problem yanıtından yalnızca yapılandırılmış kod ve alan kayıtları alınır.
// Sunucunun detail veya message metni kullanıcıya aktarılmaz.
export async function readApiProblem(response: Response, signal?: AbortSignal): Promise<ApiProblem | undefined> {
  try {
    const problem: unknown = await response.json()
    signal?.throwIfAborted()
    if (!problem || typeof problem !== 'object') return
    const data = problem as Record<string, unknown>
    if (data.status !== response.status || typeof data.code !== 'string') return
    return { code: data.code, errors: Array.isArray(data.errors) ? data.errors : undefined }
  } catch {
    signal?.throwIfAborted()
    // Bozuk bir Problem gövdesi, ilgili HTTP durumunun varsayılan hatasını değiştirmez.
  }
}

// 503 yalnızca sunucu eşleşen kodu verirse bekleyen kalıcılık entegrasyonu olarak tanınır.
export async function isIntegrationPending(response: Response, signal?: AbortSignal): Promise<boolean> {
  return response.status === 503 && (await readApiProblem(response, signal))?.code === 'INTEGRATION_PENDING'
}