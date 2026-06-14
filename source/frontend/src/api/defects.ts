import { api } from './client'
import type {
  CreateDefectRequest, Defect, DefectListItem, DefectUpdateRequest, StateTransition,
} from '#/types/defect'

export const defectApi = {
  list: (status?: string, keyword?: string) => {
    const params = new URLSearchParams()
    if (status) params.set('status', status)
    if (keyword) params.set('keyword', keyword)
    const qs = params.toString()
    return api.get<DefectListItem[]>(`/defects${qs ? `?${qs}` : ''}`)
  },
  get: (id: number) => api.get<Defect>(`/defects/${id}`),
  create: (data: CreateDefectRequest) => api.post<Defect>('/defects', data),
  update: (id: number, data: DefectUpdateRequest) => api.put<Defect>(`/defects/${id}`, data),
  delete: (id: number) => api.delete<void>(`/defects/${id}`),
  transition: (id: number, to: string, note?: string) =>
    api.patch<Defect>(`/defects/${id}/transition?to=${to}`, { note }),
  getTransitions: (id: number) => api.get<StateTransition[]>(`/defects/${id}/transitions`),
}
