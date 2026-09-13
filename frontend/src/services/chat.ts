import { request } from './http'
import type { ChatRequest, ChatResponse, ConversationVO, MessageVO } from '../types/api'

export const chatApi = {
  /** 提问：不传 conversationId 则新建会话。 */
  ask: (projectId: number, payload: ChatRequest) =>
    request<ChatResponse>({
      url: `/api/v1/projects/${projectId}/chat`,
      method: 'POST',
      data: payload,
    }),

  conversations: (projectId: number) =>
    request<ConversationVO[]>({
      url: `/api/v1/projects/${projectId}/conversations`,
      method: 'GET',
    }),

  messages: (conversationId: number) =>
    request<MessageVO[]>({
      url: `/api/v1/conversations/${conversationId}/messages`,
      method: 'GET',
    }),

  removeConversation: (conversationId: number) =>
    request<void>({ url: `/api/v1/conversations/${conversationId}`, method: 'DELETE' }),
}
