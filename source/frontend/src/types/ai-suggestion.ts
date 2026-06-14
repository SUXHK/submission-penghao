export type AISuggestionType =
  | 'INVESTIGATION_PATH' | 'ROOT_CAUSE' | 'FIX_PLAN' | 'FIX_CONTENT'
  | 'TEST_SUGGESTION' | 'RETROSPECTIVE'

export type ReviewStatus = 'PENDING_REVIEW' | 'ACCEPTED' | 'REJECTED' | 'MODIFIED'

export interface AISuggestion {
  id: number
  defectId: number
  type: AISuggestionType
  content: string
  status: ReviewStatus
  modifiedContent: string | null
  reviewNote: string | null
  triggeredByName: string | null
  reviewedByName: string | null
  createdAt: string
  reviewedAt: string | null
}

export interface ReviewRequest {
  status: ReviewStatus
  modifiedContent?: string
  reviewNote?: string
}
