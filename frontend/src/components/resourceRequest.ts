export type ResourceResult<T> = { status: 'success'; data: T } | { status: 'error'; error: unknown }

// İstemci iptali uygulamasa bile geç dönen istek, yeni ekranın sonucunu yazamaz.
export function startResourceRequest<T>(
  load: (signal: AbortSignal) => Promise<T>, finish: (result: ResourceResult<T>) => void,
) {
  const controller = new AbortController()
  void (async () => {
    let result: ResourceResult<T>
    try { result = { status: 'success', data: await load(controller.signal) } }
    catch (error) { result = { status: 'error', error } }
    if (!controller.signal.aborted) finish(result)
  })()
  return () => controller.abort()
}
