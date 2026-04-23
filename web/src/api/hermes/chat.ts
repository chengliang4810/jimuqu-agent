export interface ChatMessage {
  role: 'user' | 'assistant' | 'system'
  content: string
}

export interface StartRunRequest {
  input: string | ChatMessage[]
  instructions?: string
  conversation_history?: ChatMessage[]
  session_id?: string
  model?: string
}

export interface StartRunResponse {
  run_id: string
  status: string
}

export interface RunEvent {
  event: string
  run_id?: string
  delta?: string
  tool?: string
  name?: string
  preview?: string
  timestamp?: number
  error?: string
  usage?: {
    input_tokens: number
    output_tokens: number
    total_tokens: number
  }
}

export async function startRun(_body: StartRunRequest): Promise<StartRunResponse> {
  throw new Error('当前后端未开放 dashboard chat run')
}

export function streamRunEvents(
  _runId: string,
  _onEvent: (event: RunEvent) => void,
  _onDone: () => void,
  onError: (err: Error) => void,
) {
  queueMicrotask(() => {
    onError(new Error('当前后端未开放 dashboard chat run'))
  })

  return {
    abort: () => {},
  } as unknown as AbortController
}

export async function fetchModels(): Promise<{ data: Array<{ id: string }> }> {
  return { data: [] }
}
