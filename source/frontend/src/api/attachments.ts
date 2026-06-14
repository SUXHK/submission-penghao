import { api } from './client'
import type { Attachment } from '#/types/attachment'

export const attachmentApi = {
  list: (defectId: number) => api.get<Attachment[]>(`/defects/${defectId}/attachments`),
  upload: (defectId: number, file: File) => {
    const formData = new FormData()
    formData.append('file', file)
    return api.post<Attachment>(`/defects/${defectId}/attachments`, formData)
  },
  delete: (id: number) => api.delete<void>(`/attachments/${id}`),
}
