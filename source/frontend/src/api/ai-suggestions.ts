import { api } from './client'
import type { AISuggestion, AISuggestionType, ReviewRequest } from '#/types/ai-suggestion'

export const aiSuggestionApi = {
  list: (defectId: number) => api.get<AISuggestion[]>(`/defects/${defectId}/ai-suggestions`),
  refresh: (defectId: number, type: AISuggestionType) =>
    api.post<AISuggestion>(`/defects/${defectId}/ai-suggestions?type=${type}`),
  review: (id: number, data: ReviewRequest) => api.put<AISuggestion>(`/ai-suggestions/${id}/review`, data),
}
