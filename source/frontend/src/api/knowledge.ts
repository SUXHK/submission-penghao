import { api } from './client'
import type { KnowledgeItem, KnowledgeType } from '#/types/knowledge'

export const knowledgeApi = {
  list: (type?: KnowledgeType, keyword?: string) => {
    const params = new URLSearchParams()
    if (type) params.set('type', type)
    if (keyword) params.set('keyword', keyword)
    const qs = params.toString()
    return api.get<KnowledgeItem[]>(`/knowledge${qs ? `?${qs}` : ''}`)
  },
  get: (id: number) => api.get<KnowledgeItem>(`/knowledge/${id}`),
  update: (id: number, data: { title?: string; content?: string }) =>
    api.put<KnowledgeItem>(`/knowledge/${id}`, data),
  publish: (id: number) => api.put<KnowledgeItem>(`/knowledge/${id}/publish`),
}
