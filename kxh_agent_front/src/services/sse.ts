export type OnChunk = (text: string) => void
export type OnDone = () => void
export type OnError = (error: any) => void

export function openSSE(url: string, query: Record<string, string>, onChunk: OnChunk, onDone: OnDone, onError: OnError) {
  const params = new URLSearchParams(query)
  const full = `${url}?${params.toString()}`
  
  console.log('SSE连接URL:', full)
  console.log('查询参数:', query)
  
  const eventSource = new EventSource(full)

  eventSource.onopen = () => {
    console.log('SSE连接已建立')
  }

  eventSource.onmessage = (event) => {
    console.log('收到SSE消息:', event.data)
    // Spring SseEmitter sends plain text lines; append as-is
    const data = event.data || ''
    onChunk(data)
  }

  eventSource.onerror = (e) => {
    console.error('SSE连接错误:', e)
    console.error('EventSource状态:', eventSource.readyState)
    try { eventSource.close() } catch {}
    onError(e)
  }

  // No explicit close from server; caller closes when appropriate
  return () => {
    console.log('关闭SSE连接')
    try { eventSource.close() } catch {}
    onDone()
  }
}






