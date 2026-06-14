import { useQuery, useQueryClient } from "@tanstack/react-query"
import { Sparkles, RefreshCw, Loader2, ChevronDown, Search, Crosshair, FlaskConical, FileText, Lightbulb, CheckCircle2, Clock, XCircle, PenLine, ClipboardList, Wrench } from "lucide-react"
import { useState, useEffect } from "react"
import type { AISuggestion } from "#/types/ai-suggestion"
import type { DefectStatus } from "#/types/defect"
import { aiSuggestionApi } from "#/api/ai-suggestions"
import { useAuth } from "#/contexts/AuthContext"
import { useToast } from "#/contexts/ToastContext"

const iconProps = "size-3.5"
const TYPE_META: Record<string, any> = {
  INVESTIGATION_PATH: { label: "排查路径", color: "border-l-slate-400", bg: "bg-slate-50", icon: <Search className={`${iconProps} text-slate-400`} />, target: null, targetField: null },
  ROOT_CAUSE: { label: "根因假设", color: "border-l-purple-500", bg: "bg-purple-50", icon: <Crosshair className={`${iconProps} text-purple-500`} />, target: "根因分析与修复", targetField: "根因假设" },
  FIX_PLAN: { label: "修复方案", color: "border-l-indigo-500", bg: "bg-indigo-50", icon: <ClipboardList className={`${iconProps} text-indigo-500`} />, target: "根因分析与修复", targetField: "修复方案" },
  FIX_CONTENT: { label: "修复内容", color: "border-l-cyan-500", bg: "bg-cyan-50", icon: <Wrench className={`${iconProps} text-cyan-500`} />, target: "根因分析与修复", targetField: "修复内容" },
  TEST_SUGGESTION: { label: "测试建议", color: "border-l-green-500", bg: "bg-green-50", icon: <FlaskConical className={`${iconProps} text-green-500`} />, target: "验证记录", targetField: "验证结果" },
  RETROSPECTIVE: { label: "复盘草稿", color: "border-l-orange-500", bg: "bg-orange-50", icon: <FileText className={`${iconProps} text-orange-500`} />, target: "验证记录", targetField: "验证结论" },
}
const FALLBACK_META = { label: "", color: "border-l-slate-400", bg: "bg-slate-50", icon: <Lightbulb className={`${iconProps} text-slate-400`} />, target: null, targetField: null }

interface Props {
  defectId: number
  defectStatus: DefectStatus
  pendingStatus?: DefectStatus | null
  onStatusSync?: () => void
  onAutoFill: (s: AISuggestion) => void
  onAccept: (s: AISuggestion) => void
}

const aiStates: DefectStatus[] = ['TRIAGING', 'ANALYZED', 'PLANNED', 'IN_REPAIR', 'FIXED', 'CLOSED']

export function AIPanel({ defectId, defectStatus, pendingStatus, onStatusSync, onAutoFill, onAccept }: Props) {
  const { user } = useAuth()
  const { toast } = useToast()
  const queryClient = useQueryClient()

  const expectsAI = aiStates.includes(defectStatus) || (!!pendingStatus && aiStates.includes(pendingStatus))

  // Once defect status catches up with pending, clear it
  useEffect(() => {
    if (pendingStatus && defectStatus === pendingStatus && onStatusSync) {
      onStatusSync()
    }
  }, [defectStatus, pendingStatus, onStatusSync])

  const { data: suggestions, isFetching: aiFetching } = useQuery({
    queryKey: ["ai-suggestions", defectId],
    queryFn: () => aiSuggestionApi.list(defectId),
    enabled: !!defectId && expectsAI,
    staleTime: Infinity,
    gcTime: Infinity,
  })

  const [modifyingId, setModifyingId] = useState<number | null>(null)
  const [modifyContent, setModifyContent] = useState("")
  const [rejectingId, setRejectingId] = useState<number | null>(null)
  const [rejectReason, setRejectReason] = useState("")
  const [regeneratingId, setRegeneratingId] = useState<number | null>(null)
  const [collapsedIds, setCollapsedIds] = useState<Set<number>>(new Set())

  const hasData = suggestions && suggestions.length > 0

  const toggleCollapse = (id: number) => setCollapsedIds(prev => {
    const next = new Set(prev)
    next.has(id) ? next.delete(id) : next.add(id)
    return next
  })

  useEffect(() => {
    if (suggestions && suggestions.length > 0) {
      setCollapsedIds(prev => {
        const next = new Set(prev)
        suggestions.forEach(s => {
          if (s.status !== 'PENDING_REVIEW' && !prev.has(s.id)) next.add(s.id)
        })
        return next
      })
    }
  }, [suggestions])

  const handleAccept = async (s: AISuggestion) => {
    await aiSuggestionApi.review(s.id, { status: "ACCEPTED", reviewNote: undefined })
    if (suggestions) {
      setCollapsedIds(prev => {
        const next = new Set(prev)
        suggestions.forEach(other => {
          if (other.id !== s.id && other.type === s.type && other.status === 'PENDING_REVIEW') next.add(other.id)
        })
        return next
      })
    }
    queryClient.invalidateQueries({ queryKey: ["ai-suggestions", defectId] })
    const fieldMap: Record<string, string> = { ROOT_CAUSE: "rootCauseHypothesis", FIX_PLAN: "fixPlan", FIX_CONTENT: "fixContent", TEST_SUGGESTION: "verificationResult", RETROSPECTIVE: "verificationConclusion" }
    const field = fieldMap[s.type]
    if (field) {
      await onAutoFill(s)
      const fieldNames: Record<string, string> = { ROOT_CAUSE: '根因假设', FIX_PLAN: '修复方案', FIX_CONTENT: '修复内容', TEST_SUGGESTION: '验证结果', RETROSPECTIVE: '验证结论' }
      toast(`已采纳并回填至「${fieldNames[s.type] || '对应字段'}」`, "success")
    } else {
      toast(`已采纳排查路径，可作为调查参考`, "success")
    }
    onAccept(s)
  }

  const handleReject = async (s: AISuggestion) => { setRejectingId(s.id); setRejectReason("") }
  const handleRejectConfirm = async () => {
    if (!rejectReason.trim() || !rejectingId) return
    await aiSuggestionApi.review(rejectingId, { status: "REJECTED", reviewNote: rejectReason })
    queryClient.invalidateQueries({ queryKey: ["ai-suggestions", defectId] })
    setRejectingId(null); setRejectReason("")
  }
  const handleModify = async (s: AISuggestion) => {
    await aiSuggestionApi.review(s.id, { status: "MODIFIED", modifiedContent: modifyContent })
    queryClient.invalidateQueries({ queryKey: ["ai-suggestions", defectId] })
    const fieldMap: Record<string, string> = { ROOT_CAUSE: "rootCauseHypothesis", FIX_PLAN: "fixPlan", FIX_CONTENT: "fixContent", TEST_SUGGESTION: "verificationResult", RETROSPECTIVE: "verificationConclusion" }
    if (fieldMap[s.type]) await onAutoFill(s)
    setModifyingId(null); setModifyContent("")
  }

  const handleManualRefresh = () => {
    queryClient.invalidateQueries({ queryKey: ["ai-suggestions", defectId] })
    toast("AI 建议已刷新")
  }

  return (
    <div className="flex flex-1 flex-col px-4 overflow-hidden">
      <div className="flex min-h-0 flex-1 flex-col rounded-[4px] border border-slate-200 bg-white shadow-sm overflow-hidden">
        <div className="shrink-0 border-b border-slate-200 bg-[#F4F5F7] px-3 py-1.5 text-[11px] font-medium tracking-wide text-slate-500 uppercase flex items-center justify-between">
          <span><Sparkles size={12} className="mr-1.5 inline" />AI 建议</span>
          {hasData && (
            <button onClick={handleManualRefresh} className="text-slate-400 hover:text-slate-600 transition-colors" title="手动刷新 AI 建议">
              <RefreshCw size={12} />
            </button>
          )}
        </div>
        <div className="flex min-h-0 flex-1 flex-col">
          {!expectsAI ? (
            <div className="py-8 text-center">
              <Sparkles size={16} className="text-slate-200 mx-auto mb-2" />
              <p className="text-[12px] text-slate-300">流转后可生成 AI 建议</p>
            </div>
          ) : !hasData ? (
            <div className="py-8 text-center">
              {aiFetching ? (
                <>
                  <Loader2 size={20} className="animate-spin text-[#0052CC] mx-auto mb-3" />
                  <p className="text-[13px] text-[#0052CC] font-medium">AI 建议生成中...</p>
                  <p className="text-[11px] text-slate-400 mt-1">正在调用 DeepSeek，请耐心等待</p>
                </>
              ) : (
                <>
                  <Sparkles size={16} className="text-slate-300 mx-auto mb-2" />
                  <p className="text-[12px] text-slate-400">暂无 AI 建议</p>
                  <button onClick={handleManualRefresh}
                    className="text-[11px] text-[#0052CC] hover:underline mt-1">点击重试</button>
                </>
              )}
            </div>
          ) : (
            <div className="flex min-h-0 flex-1 flex-col gap-2 overflow-y-auto">
              {aiFetching && (
                <div className="shrink-0 flex items-center gap-2 px-3 py-1.5 bg-blue-50 border border-blue-100 rounded-[4px] animate-pulse">
                  <Loader2 size={12} className="animate-spin text-[#0052CC]" />
                  <span className="text-[11px] text-[#0052CC]">AI 建议生成中，正在调用 DeepSeek...</span>
                </div>
              )}
              {suggestions.map((s: AISuggestion) => {
                const meta = TYPE_META[s.type] || FALLBACK_META
                const isPending = s.status === "PENDING_REVIEW"
                const isCollapsed = collapsedIds.has(s.id)
                return (
                  <div key={s.id}
                    className={`border-b border-l-[4px] border-slate-100 last:border-b-0 ${meta.color} ${isPending ? "bg-amber-50" : meta.bg} flex min-h-0 flex-col ${isCollapsed ? "shrink-0" : "flex-1"}`}>
                    <div className="flex shrink-0 items-center gap-1.5 px-2.5 py-2 cursor-pointer hover:brightness-95 select-none"
                      onClick={() => toggleCollapse(s.id)}>
                      {meta.icon}
                      <span className="text-[12px] font-semibold text-slate-700 select-none">{meta.label}</span>
                      <span className={`ml-auto shrink-0 rounded-[2px] px-1.5 py-0.5 text-[11px] inline-flex items-center gap-1 select-none ${
                        isPending ? "bg-amber-200 text-amber-800" : s.status === "ACCEPTED" ? "bg-green-200 text-green-800" : s.status === "REJECTED" ? "bg-red-200 text-red-800" : "bg-blue-200 text-blue-800"}`}>
                        {isPending && <Clock size={10} />}
                        {s.status === "ACCEPTED" && <CheckCircle2 size={10} />}
                        {s.status === "REJECTED" && <XCircle size={10} />}
                        {s.status === "MODIFIED" && <PenLine size={10} />}
                        {{ PENDING_REVIEW: "待审", ACCEPTED: "采纳", REJECTED: "拒绝", MODIFIED: "已改" }[s.status]}
                      </span>
                      <button onClick={(e) => {
                        e.stopPropagation(); setRegeneratingId(s.id)
                        aiSuggestionApi.refresh(defectId, s.type)
                          .then(() => { queryClient.invalidateQueries({ queryKey: ["ai-suggestions", defectId] }); toast(`${meta.label} 已重新生成`, "success") })
                          .catch(() => toast(`${meta.label} 刷新失败`, "error"))
                          .finally(() => setRegeneratingId(null))
                      }} disabled={regeneratingId === s.id}
                        className="text-slate-300 hover:text-[#0052CC] transition-colors disabled:opacity-50 shrink-0" title="重新生成">
                        <RefreshCw size={11} className={regeneratingId === s.id ? "animate-spin text-[#0052CC]" : ""} />
                      </button>
                      <ChevronDown size={12} className={`shrink-0 text-slate-400 transition-transform ${isCollapsed ? "" : "rotate-180"}`} />
                    </div>
                    {!isCollapsed && (
                      <div className="flex min-h-0 flex-1 flex-col px-2.5 pb-2.5">
                        {modifyingId === s.id ? (
                          <div className="flex min-h-0 flex-1 flex-col space-y-2">
                            <textarea className="w-full flex-1 rounded-[3px] border border-slate-200 px-2 py-1 text-[13px] focus:border-[#0052CC] focus:outline-none"
                              value={modifyContent} onChange={(e) => setModifyContent(e.target.value)} autoFocus />
                            <div className="flex shrink-0 gap-1.5">
                              <button onClick={() => handleModify(s)} className="rounded-[3px] bg-blue-600 px-2.5 py-0.5 text-[12px] text-white">保存修改</button>
                              <button onClick={() => { setModifyingId(null); setModifyContent("") }} className="rounded-[3px] border px-2.5 py-0.5 text-[12px]">取消</button>
                            </div>
                          </div>
                        ) : (
                          <>
                            <div className="min-h-0 flex-1 overflow-y-auto text-[13px] leading-snug whitespace-pre-wrap text-slate-700">
                              {s.modifiedContent || s.content}
                            </div>
                            {s.reviewNote && <div className="mt-1.5 shrink-0 text-[11px] text-red-500">原因: {s.reviewNote}</div>}
                            {(s.status === "ACCEPTED" || s.status === "MODIFIED") && (
                              <div className="mt-1.5 text-[10px] text-slate-400 flex items-center gap-1">
                                <CheckCircle2 size={10} className="text-green-500" />
                                {meta.target ? <span>已回填至 <span className="font-medium text-slate-500">{meta.target} › {meta.targetField}</span></span> : <span>已保存为参考指南</span>}
                              </div>
                            )}
                            {isPending && user?.role === "ENGINEER" && (
                              <div className="mt-2 shrink-0">
                                {meta.target ? (
                                  <div className="text-[10px] text-slate-400 mb-1.5 flex items-center gap-1">
                                    <span className="text-slate-300">采纳后回填至</span>
                                    <span className="font-medium text-slate-500">{meta.target} › {meta.targetField}</span>
                                  </div>
                                ) : (
                                  <div className="text-[10px] text-slate-300 mb-1.5">采纳后保存为参考，不回填字段</div>
                                )}
                                <div className="flex gap-1.5">
                                  <button onClick={() => handleAccept(s)} className="rounded-[3px] bg-green-600 px-2.5 py-0.5 text-[12px] text-white">采纳</button>
                                  <button onClick={() => { setModifyingId(s.id); setModifyContent(s.content) }} className="rounded-[3px] bg-blue-500 px-2.5 py-0.5 text-[12px] text-white">修改</button>
                                  <button onClick={() => handleReject(s)} className="rounded-[3px] bg-red-500 px-2.5 py-0.5 text-[12px] text-white">拒绝</button>
                                </div>
                              </div>
                            )}
                            {rejectingId === s.id && (
                              <div className="mt-2 space-y-2 shrink-0">
                                <textarea className="w-full rounded-[3px] border border-red-300 px-2 py-1 text-[13px] focus:border-red-500 focus:outline-none"
                                  rows={2} placeholder="请输入拒绝原因..." value={rejectReason}
                                  onChange={e => setRejectReason(e.target.value)} autoFocus />
                                <div className="flex gap-1.5">
                                  <button onClick={handleRejectConfirm} className="rounded-[3px] bg-red-500 px-2.5 py-0.5 text-[12px] text-white">确认拒绝</button>
                                  <button onClick={() => { setRejectingId(null); setRejectReason("") }} className="rounded-[3px] border px-2.5 py-0.5 text-[12px]">取消</button>
                                </div>
                              </div>
                            )}
                          </>
                        )}
                      </div>
                    )}
                  </div>
                )
              })}
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
