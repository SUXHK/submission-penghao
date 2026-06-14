import { fmtDateTime } from "#/lib/utils"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { Loader2 } from "lucide-react"
import { useEffect, useState } from "react"
import { createPortal } from "react-dom"

import type { DefectStatus, DefectUpdateRequest } from "#/types/defect"

import { attachmentApi } from "#/api/attachments"
import { defectApi } from "#/api/defects"
import { Sheet } from "#/components/ui/sheet"
import { EditableField } from "#/components/defect/EditableField"
import { AIPanel } from "#/components/defect/AIPanel"
import { useAuth } from "#/contexts/AuthContext"
import { useToast } from "#/contexts/ToastContext"

const STATUS_LABEL: Record<string, string> = {
    DRAFT: "草稿",
    REPORTED: "已登记",
    TRIAGING: "分诊中",
    ANALYZED: "已分析",
    PLANNED: "已计划",
    IN_REPAIR: "修复中",
    FIXED: "已修复",
    VERIFIED: "已验证",
    CLOSED: "已关闭",
    REOPENED: "重新打开",
}
const STATUS_STYLE: Record<string, string> = {
    DRAFT: "bg-slate-200 text-slate-600",
    REPORTED: "bg-blue-100 text-blue-700",
    TRIAGING: "bg-amber-100 text-amber-800",
    ANALYZED: "bg-purple-100 text-purple-700",
    PLANNED: "bg-indigo-100 text-indigo-700",
    IN_REPAIR: "bg-orange-100 text-orange-800",
    FIXED: "bg-green-100 text-green-700",
    VERIFIED: "bg-teal-100 text-teal-700",
    CLOSED: "bg-slate-200 text-slate-500",
    REOPENED: "bg-red-100 text-red-700",
}
const TRANSITIONS: Record<DefectStatus, DefectStatus[]> = {
    DRAFT: ["REPORTED"],
    REPORTED: ["TRIAGING"],
    TRIAGING: ["ANALYZED"],
    ANALYZED: ["PLANNED"],
    PLANNED: ["IN_REPAIR"],
    IN_REPAIR: ["FIXED"],
    FIXED: ["VERIFIED", "IN_REPAIR"],
    VERIFIED: ["CLOSED"],
    CLOSED: ["REOPENED"],
    REOPENED: ["REPORTED"],
}
const inputCls =
    "w-full border border-slate-200 rounded-[3px] px-2 py-1 text-[13px] focus:outline-none focus:border-[#0052CC]"

interface Props {
    defectId: number
    onClose: () => void
}

export function DefectDetailSheet({ defectId, onClose }: Props) {
    const { user } = useAuth()
    const { toast } = useToast()
    const queryClient = useQueryClient()

    const { data: defect, isLoading } = useQuery({
        queryKey: ["defect", defectId],
        queryFn: () => defectApi.get(defectId),
        enabled: !!defectId,
    })
    const { data: transitions } = useQuery({
        queryKey: ["transitions", defectId],
        queryFn: () => defectApi.getTransitions(defectId),
        enabled: !!defectId,
    })
    const { data: attachments } = useQuery({
        queryKey: ["attachments", defectId],
        queryFn: () => attachmentApi.list(defectId),
        enabled: !!defectId,
    })

    const [editField, setEditField] = useState<string | null>(null)
    const [editValue, setEditValue] = useState("")
    const [transitionNote, setTransitionNote] = useState("")
    const [showTransition, setShowTransition] = useState<DefectStatus | null>(
        null,
    )
    const [popupVisible, setPopupVisible] = useState(false)
    const [transitioning, setTransitioning] = useState(false)
    const [pendingAIStatus, setPendingAIStatus] = useState<DefectStatus | null>(null)

    // Reset edit state when switching defects
    useEffect(() => {
        setEditField(null)
        setEditValue("")
        setShowTransition(null)
        setTransitionNote("")
        setPopupVisible(false)
    }, [defectId])

    if (!defectId) return null

    const fieldLabels: Record<string, string> = {
        title: "标题", description: "描述", phenomenon: "现象描述", environment: "运行环境",
        reproductionSteps: "复现步骤", expectedResult: "期望结果", actualResult: "实际结果",
        severity: "严重程度", userImpact: "用户影响", businessImpact: "业务影响",
        frequency: "频率", workaround: "规避方案", releaseWindow: "发布窗口",
        rootCauseHypothesis: "根因假设", fixPlan: "修复方案", fixContent: "修复内容",
        affectedModules: "影响模块", fixDuration: "修复耗时",
        verificationResult: "验证结果", regressionScope: "回归范围", verificationConclusion: "验证结论",
    }

    const handleUpdate = async (field: string, value: unknown) => {
        const req: DefectUpdateRequest = { [field]: value }
        await defectApi.update(defectId, req)
        queryClient.invalidateQueries({ queryKey: ["defect", defectId] })
        queryClient.invalidateQueries({ queryKey: ["defects"] })
        setEditField(null)
        toast(`${fieldLabels[field] || field} 已更新`, "success")
    }

    const handleTransition = async (to: DefectStatus) => {
        setTransitioning(true)
        try {
            await defectApi.transition(defectId, to, transitionNote)
            toast(`已流转至 ${STATUS_LABEL[to]}`, "success")
            queryClient.invalidateQueries({ queryKey: ["defect", defectId] })
            queryClient.invalidateQueries({ queryKey: ["defects"] })
            queryClient.invalidateQueries({ queryKey: ["transitions", defectId] })
            setPendingAIStatus(to)
            queryClient.invalidateQueries({ queryKey: ["ai-suggestions", defectId] })
            setPopupVisible(false)
            setShowTransition(null)
            setTransitionNote("")
            setTransitioning(false)
        } catch (err) {
            toast(err instanceof Error ? err.message : "流转失败", "error")
            setTransitioning(false)
        }
    }

    const handleUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
        const file = e.target.files?.[0]
        if (!file) return
        if (file.size > 5 * 1024 * 1024) {
            alert("≤5MB")
            return
        }
        await attachmentApi.upload(defectId, file)
        queryClient.invalidateQueries({ queryKey: ["attachments", defectId] })
    }

    const handleEditStart = (field: string, val: string) => {
        setEditField(field)
        setEditValue(val)
    }
    const numericFields = new Set(["severity", "userImpact", "businessImpact", "frequency", "workaround", "releaseWindow", "fixDuration"])
    const handleEditSave = (field: string, val: string, multiline: boolean, maxVal?: number) => {
        if (numericFields.has(field)) {
            const trimmed = val.trim()
            if (trimmed === "") {
                handleUpdate(field, null)
                return
            }
            const num = Number(trimmed)
            if (isNaN(num) || !Number.isInteger(num) || num < 0) {
                toast("请输入有效的非负整数", "error")
                return
            }
            if (maxVal !== undefined && num > maxVal) {
                toast(`最大值不能超过 ${maxVal}`, "error")
                return
            }
            handleUpdate(field, num)
            return
        }
        handleUpdate(field, multiline ? val : val)
    }

    const available = defect ? TRANSITIONS[defect.status] || [] : []

    return (
        <Sheet
            open={!!defectId}
            onClose={onClose}
            title={defect?.title || "加载中…"}
        >
            {isLoading ? (
                <div className="p-6 text-sm text-slate-400">加载中…</div>
            ) : defect ? (
                <div className="flex h-full">
                    {/* Column 1: Timeline */}
                    <div className="w-[164px] shrink-0 overflow-y-auto border-r border-slate-200 p-3">
                        <div className="mb-3 text-[11px] font-medium tracking-wide text-slate-400 uppercase">状态流转</div>
                        <div className="space-y-0">
                            {(() => {
                                const stateSeq: DefectStatus[] = ['DRAFT', 'REPORTED', 'TRIAGING', 'ANALYZED', 'PLANNED', 'IN_REPAIR', 'FIXED', 'VERIFIED', 'CLOSED']
                                let currentIdx = stateSeq.indexOf(defect.status)
                                const isReopened = currentIdx === -1 && defect.status === 'REOPENED'
                                if (isReopened) currentIdx = 1
                                const formatTime = (ts: string | null | undefined) => {
                                    if (!ts) return null
                                    const d = new Date(ts)
                                    return d.toLocaleString('zh-CN', { hour12: false, year: undefined, month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' })
                                }
                                const getTransition = (toStatus: string) => {
                                    if (!transitions) return null
                                    return transitions.find(t => t.toStatus === toStatus) ?? null
                                }
                                return <>
                                    {isReopened && (
                                        <div className="text-[10px] text-slate-500 mb-2">
                                            <span className="rounded-[3px] px-1 py-0.5 text-[9px] font-medium bg-red-100 text-red-700 mr-1">已重新打开</span>
                                        </div>
                                    )}
                                    {stateSeq.map((s, i) => {
                                        const isPast = i < currentIdx
                                        const isCurrent = i === currentIdx
                                        const isFuture = i > currentIdx
                                        const t = isPast || isCurrent ? getTransition(s) : null
                                        const time = t ? formatTime(t.createdAt) : null
                                        const dotCls = isPast ? 'bg-[#0052CC] border-[#0052CC]'
                                            : isCurrent ? 'bg-white border-[#0052CC] border-2 w-3 h-3 -ml-[2px]'
                                            : 'bg-white border-slate-300'
                                        const lineCls = isPast ? 'bg-[#0052CC]' : 'bg-slate-200'
                                        const badgeCls = isPast || isCurrent ? STATUS_STYLE[s] : 'bg-slate-100 text-slate-300'
                                        return (
                                            <div key={s} className="flex gap-2">
                                                <div className="flex flex-col items-center pt-0.5">
                                                    <div className={`rounded-full border shrink-0 ${dotCls} ${isCurrent ? '' : 'w-2.5 h-2.5'}`} />
                                                    {i < stateSeq.length - 1 && (
                                                        <div className={`w-0.5 flex-1 min-h-[28px] ${lineCls}`} />
                                                    )}
                                                </div>
                                                <div className={`pb-2.5 text-[11px] ${isFuture ? 'text-slate-300' : 'text-slate-700'}`}>
                                                    <span className={`rounded-[3px] px-1 py-0.5 text-[10px] font-medium ${badgeCls}`}>{STATUS_LABEL[s]}</span>
                                                    {isPast && time && <div className="text-[9px] text-slate-400 mt-0.5 leading-tight">{time}</div>}
                                                    {isPast && t?.operatorName && <div className="text-[9px] text-slate-500 leading-tight">{t.operatorName}</div>}
                                                    {isPast && t?.note && <div className="text-[9px] text-slate-400 mt-0.5 leading-tight break-all">备注: {t.note}</div>}
                                                    {isCurrent && (
                                                        <div className="mt-1 space-y-1">
                                                            <div className="text-[10px] text-[#0052CC] font-medium">● 当前</div>
                                                            {available.map((t) => {
                                                                const roleRequired: Record<string, string> = { REPORTED: "SUBMITTER", TRIAGING: "ENGINEER", ANALYZED: "ENGINEER", PLANNED: "ENGINEER", IN_REPAIR: "ENGINEER", FIXED: "ENGINEER", VERIFIED: "QA", CLOSED: "QA", REOPENED: "ENGINEER" }
                                                                // Source-specific role overrides
                                                                const needed = (defect.status === 'FIXED' && t === 'IN_REPAIR') ? 'QA'
                                                                             : (defect.status === 'REOPENED' && t === 'REPORTED') ? 'ENGINEER'
                                                                             : roleRequired[t]
                                                                const roleLabel: Record<string, string> = { SUBMITTER: "提交人", ENGINEER: "工程师", QA: "QA" }
                                                                const canClick = !needed || user?.role === needed
                                                                return (
                                                                    <div key={t} className="group relative">
                                                                        <button
                                                                            onClick={() => { if (!canClick) return; setShowTransition(t); setPopupVisible(false); requestAnimationFrame(() => requestAnimationFrame(() => setPopupVisible(true))) }}
                                                                            disabled={!canClick}
                                                                            className={`rounded-[3px] px-2 py-0.5 text-[10px] font-medium transition-colors ${canClick ? "cursor-pointer bg-[#0052CC] text-white hover:bg-[#0747A6]" : "cursor-not-allowed bg-slate-100 text-slate-400"}`}>
                                                                            → {STATUS_LABEL[t]}
                                                                        </button>
                                                                        {!canClick && (
                                                                            <div className="pointer-events-none absolute -top-7 left-1/2 z-10 -translate-x-1/2 rounded-md bg-slate-800 px-2 py-0.5 text-[10px] whitespace-nowrap text-white opacity-0 transition-opacity group-hover:opacity-100">
                                                                                需{roleLabel[needed]}
                                                                                <div className="absolute -bottom-1 left-1/2 h-1.5 w-1.5 -translate-x-1/2 rotate-45 bg-slate-800" />
                                                                            </div>
                                                                        )}
                                                                    </div>
                                                                )
                                                            })}
                                                        </div>
                                                    )}
                                                    {isFuture && <div className="text-[10px] text-slate-300 mt-0.5">待到达</div>}
                                                </div>
                                            </div>
                                        )
                                    })}
                                </>
                            })()}
                        </div>
                    </div>

                    {/* Column 2: Defect detail */}
                    <div className="flex flex-1 flex-col overflow-hidden border-r border-slate-200">
                        <div className="shrink-0 px-4 pt-4 pb-2">
                        <div className="mb-1 flex items-center gap-2">
                            <span
                                className={`rounded-[3px] px-1.5 py-0.5 text-[11px] font-medium ${STATUS_STYLE[defect.status]}`}
                            >
                                {STATUS_LABEL[defect.status]}
                            </span>
                            {defect.priority && (
                                <span className="text-[11px] font-bold text-slate-600">
                                    {defect.priority}
                                </span>
                            )}
                            <span className="ml-auto text-[11px] text-slate-400">
                                ID: {defect.id}
                            </span>
                        </div>
                        <p className="text-[13px] text-slate-500">
                            {defect.description || "无描述"}
                        </p>
                        <div className="flex gap-3 text-[12px] text-slate-400">
                            <span>报告人: {defect.reporterName || "-"}</span>
                            <span>负责人: {defect.assigneeName || "-"}</span>
                            <span>{fmtDateTime(defect.createdAt)}</span>
                        </div>
                        </div>

                        <div className="flex-1 min-h-0 overflow-y-auto px-4 pb-4 space-y-3">
                        {(() => {
                            // Progressive disclosure: only show sections the current state has reached
                            const stateOrder: DefectStatus[] = ['DRAFT', 'REPORTED', 'TRIAGING', 'ANALYZED', 'PLANNED', 'IN_REPAIR', 'FIXED', 'VERIFIED', 'CLOSED', 'REOPENED']
                            const currentOrder = stateOrder.indexOf(defect.status)
                            const visible = (threshold: string) => currentOrder >= stateOrder.indexOf(threshold as DefectStatus)
                            const allSections = [
                            {
                                key: "reproduction",
                                title: "复现信息",
                                showAfter: 'DRAFT' as DefectStatus,
                                fields: [
                                    {
                                        label: "现象描述",
                                        field: "phenomenon",
                                        ml: true,
                                        required: true,
                                    },
                                    { label: "运行环境", field: "environment", required: true },
                                    {
                                        label: "复现步骤",
                                        field: "reproductionSteps",
                                        ml: true,
                                        required: true,
                                    },
                                    {
                                        label: "期望结果",
                                        field: "expectedResult",
                                        ml: true,
                                        required: true,
                                    },
                                    {
                                        label: "实际结果",
                                        field: "actualResult",
                                        ml: true,
                                        required: true,
                                    },
                                ],
                            },
                            {
                                key: "assessment",
                                title: "影响评估 & 优先级",
                                showAfter: 'TRIAGING' as DefectStatus,
                                fields: [
                                    { label: "用户影响", field: "userImpact", numeric: true, required: true },
                                    { label: "业务影响", field: "businessImpact", numeric: true, required: true },
                                    { label: "频率", field: "frequency", numeric: true, required: true },
                                    { label: "规避方案", field: "workaround", numeric: true, required: true },
                                    { label: "发布窗口", field: "releaseWindow", numeric: true, required: true },
                                ],
                                extra: (
                                    <div className="mt-2 mb-2 px-1 text-[13px]">
                                        <span className="text-slate-500">
                                            严重程度:{" "}
                                        </span>
                                        <b>{defect.severity ?? "-"}</b>
                                        <span className="ml-6 text-slate-500">
                                            优先级:{" "}
                                        </span>
                                        <b
                                            className={
                                                defect.priority === "P0" ||
                                                defect.priority === "P1"
                                                    ? "text-red-600"
                                                    : "text-slate-700"
                                            }
                                        >
                                            {defect.priority ?? "-"}
                                        </b>
                                    </div>
                                ),
                            },
                            {
                                key: "fix",
                                title: "根因分析与修复",
                                sectionColor: "border-l-purple-500",
                                showAfter: 'ANALYZED' as DefectStatus,
                                fields: [
                                    {
                                        label: "根因假设",
                                        field: "rootCauseHypothesis",
                                        ml: true,
                                        required: true,
                                    },
                                    {
                                        label: "修复方案",
                                        field: "fixPlan",
                                        ml: true,
                                        required: true,
                                    },
                                    {
                                        label: "修复内容",
                                        field: "fixContent",
                                        ml: true,
                                        required: true,
                                    },
                                    {
                                        label: "影响模块",
                                        field: "affectedModules",
                                    },
                                    {
                                        label: "修复耗时(分钟)",
                                        field: "fixDuration",
                                        numeric: true,
                                        maxVal: 99999,
                                    },
                                ],
                            },
                            {
                                key: "verification",
                                title: "验证记录",
                                sectionColor: "border-l-green-500",
                                showAfter: 'FIXED' as DefectStatus,
                                fields: [
                                    {
                                        label: "验证结果",
                                        field: "verificationResult",
                                        ml: true,
                                        required: true,
                                    },
                                    {
                                        label: "回归范围",
                                        field: "regressionScope",
                                        ml: true,
                                        required: true,
                                    },
                                    {
                                        label: "验证结论",
                                        field: "verificationConclusion",
                                        ml: true,
                                        required: true,
                                    },
                                ],
                            },
                        ];
                        return allSections
                            .filter(s => visible((s as any).showAfter ?? 'DRAFT'))
                            .map((section) => (
                            <div
                                key={section.key}
                                className={`overflow-hidden rounded-[4px] border border-slate-200 bg-white shadow-sm ${(section as any).sectionColor ? `border-l-[3px] ${(section as any).sectionColor}` : ''}`}
                            >
                                <div className={`border-b border-slate-200 bg-[#F4F5F7] px-3 py-1.5 text-[11px] font-medium tracking-wide uppercase ${(section as any).sectionColor ? (section as any).sectionColor.replace('border-l-', 'text-').replace('-500', '-700') : 'text-slate-500'}`}>
                                    {section.title}
                                </div>
                                <div className="grid grid-cols-2 gap-x-3 px-3 py-1">
                                    {section.fields.map((f: any) => (
                                        <div
                                            key={f.field}
                                            className={f.ml ? "col-span-2" : ""}
                                        >
                                            <EditableField
                                                label={f.label}
                                                field={f.field}
                                                value={(defect as any)[f.field]}
                                                multiline={f.ml}
                                                numeric={f.numeric}
                                                maxVal={f.maxVal}
                                                required={f.required}
                                                editField={editField}
                                                editValue={editValue}
                                                onEditStart={handleEditStart}
                                                onEditChange={setEditValue}
                                                onEditSave={handleEditSave}
                                                onEditCancel={() => setEditField(null)}
                                            />
                                        </div>
                                    ))}
                                </div>
                                {section.extra}
                            </div>
                        ))})()}

                            {showTransition && createPortal(
                                <div className="fixed inset-0 z-[60] flex items-center justify-center">
                                    <div
                                        className={`absolute inset-0 bg-black/50 transition-opacity duration-200 ${popupVisible ? 'opacity-100' : 'opacity-0'}`}
                                        onClick={() => { setPopupVisible(false); setTransitioning(false); setTimeout(() => { setShowTransition(null); setTransitionNote('') }, 200) }}
                                    />
                                    <div className={`relative w-80 rounded-xl border border-slate-200 bg-white p-5 shadow-2xl transition-all duration-200 ease-out ${popupVisible ? 'opacity-100 scale-100 translate-y-0' : 'opacity-0 scale-95 translate-y-1'}`}>
                                        <p className="mb-1 text-[14px] font-semibold text-slate-800">
                                            确认状态流转
                                        </p>
                                        <p className="mb-3 text-[13px] text-slate-500">
                                            将缺陷流转至{" "}
                                            <span
                                                className={`inline-block rounded-[3px] px-1.5 py-0.5 text-[11px] font-medium ${STATUS_STYLE[showTransition]}`}
                                            >
                                                {STATUS_LABEL[showTransition]}
                                            </span>
                                        </p>
                                        <input
                                            className={`${inputCls} mb-3`}
                                            placeholder="备注（可选）"
                                            value={transitionNote}
                                            onChange={(e) => setTransitionNote(e.target.value)}
                                            autoFocus
                                        />
                                        <div className="flex justify-end gap-2">
                                            <button
                                                onClick={() => { setPopupVisible(false); setTransitioning(false); setTimeout(() => { setShowTransition(null); setTransitionNote('') }, 200) }}
                                                className="rounded-[3px] border border-slate-200 px-3 py-1.5 text-[12px] hover:bg-slate-50"
                                            >
                                                取消
                                            </button>
                                            <button
                                                onClick={() => handleTransition(showTransition)}
                                                disabled={transitioning}
                                                className="rounded-[3px] bg-[#0052CC] px-3 py-1.5 text-[12px] font-medium text-white hover:bg-[#0747A6] disabled:opacity-50 inline-flex items-center gap-1"
                                            >
                                                {transitioning && <Loader2 size={12} className="animate-spin" />}
                                                {transitioning ? '流转中...' : '确认流转'}
                                            </button>
                                        </div>
                                    </div>
                                </div>
                            , document.body)}

                        <div className="rounded-[4px] border border-slate-200 bg-white p-3 shadow-sm">
                            <h3 className="mb-2 text-[13px] font-semibold text-slate-700">
                                附件 ({attachments?.length || 0}/5)
                            </h3>
                            {attachments?.map((a) => (
                                <div
                                    key={a.id}
                                    className="flex items-center justify-between py-0.5 text-[13px]"
                                >
                                    <span>
                                        {a.originalFilename}{" "}
                                        <span className="text-[11px] text-slate-400">
                                            ({(a.fileSize / 1024).toFixed(1)}KB)
                                        </span>
                                    </span>
                                    <div className="flex gap-2 text-[11px]">
                                        <a
                                            href={`/api/attachments/${a.id}/download`}
                                            className="text-[#0052CC] hover:underline"
                                        >
                                            下载
                                        </a>
                                        <button
                                            onClick={async () => {
                                                await attachmentApi.delete(a.id)
                                                queryClient.invalidateQueries({
                                                    queryKey: [
                                                        "attachments",
                                                        defectId,
                                                    ],
                                                })
                                            }}
                                            className="text-red-500 hover:underline"
                                        >
                                            删除
                                        </button>
                                    </div>
                                </div>
                            ))}
                            <label className="mt-2 inline-block cursor-pointer text-[12px] text-[#0052CC] hover:underline">
                                + 上传附件
                                <input
                                    type="file"
                                    className="hidden"
                                    onChange={handleUpload}
                                    accept="image/png,image/jpeg,image/gif,image/webp,text/plain,application/pdf"
                                />
                            </label>
                        </div>
                        </div>
                    </div>

                    {/* Column 3: AI Panel */}
                    <AIPanel defectId={defectId} defectStatus={defect.status} pendingStatus={pendingAIStatus} onStatusSync={() => setPendingAIStatus(null)} onAutoFill={async (s) => {
                        const content = s.status === "PENDING_REVIEW" ? s.content : s.modifiedContent || s.content
                        const fieldMap: Record<string, string> = { ROOT_CAUSE: "rootCauseHypothesis", FIX_PLAN: "fixPlan", FIX_CONTENT: "fixContent", TEST_SUGGESTION: "verificationResult", RETROSPECTIVE: "verificationConclusion" }
                        const field = fieldMap[s.type]
                        if (field) {
                            await defectApi.update(defectId, { [field]: content } as DefectUpdateRequest)
                            queryClient.invalidateQueries({ queryKey: ["defect", defectId] })
                        }
                    }} onAccept={() => {
                        queryClient.invalidateQueries({ queryKey: ["defect", defectId] })
                    }} />
                </div>
            ) : null}
        </Sheet>
    )
}
