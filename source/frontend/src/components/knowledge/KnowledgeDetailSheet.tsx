import { useQuery, useQueryClient } from "@tanstack/react-query"
import { useState } from "react"
import { fmtDateTime } from "#/lib/utils"
import { FlaskConical, Search, ShieldAlert, BookOpen } from "lucide-react"
import { useAuth } from "#/contexts/AuthContext"
import { useToast } from "#/contexts/ToastContext"
import { knowledgeApi } from "#/api/knowledge"
import { Sheet } from "#/components/ui/sheet"

const TYPE_META: Record<string, { label: string; icon: any; color: string }> = {
  REGRESSION_TEST: { label: '回归用例', icon: FlaskConical, color: '#12A150' },
  TROUBLESHOOTING: { label: '排查手册', icon: Search, color: '#2684FF' },
  RISK_RULE: { label: '风险规则', icon: ShieldAlert, color: '#E5493A' },
}
const STATUS_LABEL: Record<string, string> = { AUTO_GENERATED: '待审核', REVIEWED: '已审核', PUBLISHED: '已发布' }
const STATUS_COLOR: Record<string, string> = { AUTO_GENERATED: '#FFAB00', REVIEWED: '#2684FF', PUBLISHED: '#12A150' }

const inputCls = "w-full border border-slate-200 rounded-[3px] px-2 py-1 text-[13px] focus:outline-none focus:border-[#0052CC]"

interface Props {
  itemId: number
  onClose: () => void
}

export function KnowledgeDetailSheet({ itemId, onClose }: Props) {
  const { user } = useAuth()
  const { toast } = useToast()
  const queryClient = useQueryClient()

  const { data: item, isLoading } = useQuery({
    queryKey: ['knowledge', itemId],
    queryFn: () => knowledgeApi.get(itemId),
    enabled: !!itemId,
  })

  const [editing, setEditing] = useState(false)
  const [title, setTitle] = useState('')
  const [content, setContent] = useState('')

  if (!itemId) return null

  const meta = item ? TYPE_META[item.type] : null
  const Icon = meta?.icon || BookOpen

  return (
    <Sheet open={!!itemId} onClose={onClose} title={item?.title || "加载中…"}>
      {isLoading ? (
        <div className="p-6 text-sm text-slate-400">加载中…</div>
      ) : item ? (
        <div className="flex flex-col h-full">
          {/* Sticky header: badges + source */}
          <div className="shrink-0 px-5 pt-4 pb-3 border-b border-slate-100">
            <div className="flex items-center gap-2 mb-2">
              <span className="inline-flex items-center gap-1.5 text-[11px] font-medium px-2 py-1 rounded-[3px]"
                style={{ color: meta?.color, backgroundColor: meta?.color + '18' }}>
                <Icon size={12} />
                {meta?.label}
              </span>
              <span className="inline-flex items-center gap-1.5 text-[11px] font-medium px-2 py-1 rounded-[3px]"
                style={{ color: STATUS_COLOR[item.status], backgroundColor: STATUS_COLOR[item.status] + '18' }}>
                <span className="w-1.5 h-1.5 rounded-full" style={{ backgroundColor: STATUS_COLOR[item.status] }} />
                {STATUS_LABEL[item.status]}
              </span>
            </div>
            {item.defectId && (
              <p className="text-[12px] text-slate-400">
                来源缺陷: <a href={`/defects/${item.defectId}`} className="text-[#0052CC] hover:underline">{item.defectTitle || `#${item.defectId}`}</a>
              </p>
            )}
          </div>

          {/* Scrollable content */}
          <div className="flex-1 min-h-0 overflow-y-auto px-5 py-4">
            {editing ? (
              <div className="flex flex-col gap-3 h-full">
                <input className={`${inputCls} font-semibold shrink-0`} value={title} onChange={e => setTitle(e.target.value)} placeholder="标题" />
                <textarea className={`${inputCls} flex-1 min-h-0 resize-none`} value={content} onChange={e => setContent(e.target.value)} placeholder="内容" />
              </div>
            ) : (
              <div className="text-[13px] text-slate-700 whitespace-pre-wrap leading-relaxed">{item.content}</div>
            )}
          </div>

          {/* Sticky footer: buttons + timestamps */}
          <div className="shrink-0 px-5 pb-4 pt-3 border-t border-slate-100">
            {editing ? (
              <div className="flex gap-2">
                <button onClick={async () => {
                  await knowledgeApi.update(itemId, { title, content })
                  queryClient.invalidateQueries({ queryKey: ['knowledge', itemId] })
                  queryClient.invalidateQueries({ queryKey: ['knowledge'] })
                  setEditing(false)
                  toast('知识条目已更新', 'success')
                }} className="text-[12px] bg-[#0052CC] text-white px-3 py-1 rounded-[3px]">保存</button>
                <button onClick={() => setEditing(false)} className="text-[12px] border px-3 py-1 rounded-[3px]">取消</button>
              </div>
            ) : (
              <>
                <div className="flex gap-2 mb-3">
                  {user?.role === 'ENGINEER' && (
                    <>
                      <button onClick={() => { setTitle(item.title); setContent(item.content); setEditing(true) }}
                        className="text-[12px] border border-slate-200 px-3 py-1 rounded-[3px] hover:bg-slate-50">编辑</button>
                      {item.status !== 'PUBLISHED' && (
                        <button onClick={async () => {
                          await knowledgeApi.publish(itemId)
                          queryClient.invalidateQueries({ queryKey: ['knowledge', itemId] })
                          queryClient.invalidateQueries({ queryKey: ['knowledge'] })
                          toast('知识条目已发布', 'success')
                        }} className="text-[12px] bg-green-600 text-white px-3 py-1 rounded-[3px] hover:bg-green-700">发布</button>
                      )}
                    </>
                  )}
                </div>
                <div className="text-[11px] text-slate-400">
                  创建: {fmtDateTime(item.createdAt)}
                  {item.publishedAt && <> · 发布: {fmtDateTime(item.publishedAt)}</>}
                </div>
              </>
            )}
          </div>
        </div>
      ) : (
        <div className="p-6 text-slate-400 text-sm">不存在</div>
      )}
    </Sheet>
  )
}
