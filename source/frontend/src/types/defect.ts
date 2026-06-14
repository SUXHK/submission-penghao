export type DefectStatus =
  | 'DRAFT' | 'REPORTED' | 'TRIAGING' | 'ANALYZED'
  | 'PLANNED' | 'IN_REPAIR' | 'FIXED' | 'VERIFIED' | 'CLOSED' | 'REOPENED'

export type Priority = 'P0' | 'P1' | 'P2' | 'P3' | 'P4'

export interface DefectListItem {
  id: number
  title: string
  status: DefectStatus
  priority: Priority | null
  severity: number | null
  reporterName: string | null
  assigneeName: string | null
  createdAt: string
}

export interface Defect {
  id: number
  title: string
  description: string | null
  phenomenon: string | null
  environment: string | null
  reproductionSteps: string | null
  expectedResult: string | null
  actualResult: string | null
  severity: number | null
  priority: Priority | null
  userImpact: number | null
  businessImpact: number | null
  frequency: number | null
  workaround: number | null
  releaseWindow: number | null
  rootCauseHypothesis: string | null
  fixPlan: string | null
  fixContent: string | null
  affectedModules: string | null
  fixDuration: number | null
  verificationResult: string | null
  regressionScope: string | null
  verificationConclusion: string | null
  status: DefectStatus
  reporterId: number | null
  reporterName: string | null
  assigneeId: number | null
  assigneeName: string | null
  verifierId: number | null
  verifierName: string | null
  createdAt: string
  updatedAt: string
  closedAt: string | null
}

export interface CreateDefectRequest {
  title: string
  description?: string
  phenomenon?: string
  environment?: string
  reproductionSteps?: string
  expectedResult?: string
  actualResult?: string
}

export interface DefectUpdateRequest {
  title?: string
  description?: string
  phenomenon?: string
  environment?: string
  reproductionSteps?: string
  expectedResult?: string
  actualResult?: string
  severity?: number
  priority?: Priority
  userImpact?: number
  businessImpact?: number
  frequency?: number
  workaround?: number
  releaseWindow?: number
  rootCauseHypothesis?: string
  fixPlan?: string
  fixContent?: string
  affectedModules?: string
  fixDuration?: number
  verificationResult?: string
  regressionScope?: string
  verificationConclusion?: string
}

export interface StateTransition {
  id: number
  fromStatus: DefectStatus
  toStatus: DefectStatus
  operatorName: string | null
  note: string | null
  createdAt: string
}
