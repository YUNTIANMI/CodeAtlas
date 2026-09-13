import { request } from './http'
import type { AgentRequest, AgentResponse, ToolCallVO } from '../types/api'

export const agentApi = {
  /** 执行 Agent 任务（自动调用只读工具后给出结论）。 */
  run: (projectId: number, payload: AgentRequest) =>
    request<AgentResponse>({
      url: `/api/v1/projects/${projectId}/agent/run`,
      method: 'POST',
      data: payload,
    }),

  /** 查询历史工具调用记录。 */
  toolCalls: (projectId: number) =>
    request<ToolCallVO[]>({
      url: `/api/v1/projects/${projectId}/agent/tool-calls`,
      method: 'GET',
    }),
}
