export type KnowledgeType = 'REGRESSION_TEST' | 'TROUBLESHOOTING' | 'RISK_RULE'
export type KnowledgeStatus = 'AUTO_GENERATED' | 'REVIEWED' | 'PUBLISHED'

export interface KnowledgeItem {
  id: number
  defectId: number
  defectTitle: string | null
  type: KnowledgeType
  title: string
  content: string
  status: KnowledgeStatus
  createdAt: string
  publishedAt: string | null
}
