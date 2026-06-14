import { fmtDateTime } from '#/lib/utils'
import { createFileRoute, useNavigate } from '@tanstack/react-router'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useAuth } from '#/contexts/AuthContext'
import { knowledgeApi } from '#/api/knowledge'

export const Route = createFileRoute('/knowledge/$id')({ component: KnowledgeDetailPage })

const TYPE_STYLE: Record<string, string> = { REGRESSION_TEST:'bg-green-100 text-green-700', TROUBLESHOOTING:'bg-blue-100 text-blue-700', RISK_RULE:'bg-red-100 text-red-700' }
const STATUS_STYLE: Record<string, string> = { AUTO_GENERATED:'bg-amber-100 text-amber-800', REVIEWED:'bg-blue-100 text-blue-700', PUBLISHED:'bg-green-100 text-green-700' }
const TYPE_LABEL: Record<string, string> = { REGRESSION_TEST:'回归用例', TROUBLESHOOTING:'排查手册', RISK_RULE:'风险规则' }
const STATUS_LABEL: Record<string, string> = { AUTO_GENERATED:'待审核', REVIEWED:'已审核', PUBLISHED:'已发布' }

const inputCls = "w-full border border-slate-200 rounded-[3px] px-2 py-1 text-[13px] focus:outline-none focus:border-[#0052CC]"

function KnowledgeDetailPage() {
  const { id } = Route.useParams()
  const { user } = useAuth()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const itemId = Number(id)

  const { data: item, isLoading } = useQuery({
    queryKey: ['knowledge', itemId], queryFn: () => knowledgeApi.get(itemId),
  })

  const [editing, setEditing] = useState(false)
  const [title, setTitle] = useState('')
  const [content, setContent] = useState('')

  if (isLoading) return <div className="p-6 text-slate-400 text-sm">加载中…</div>
  if (!item) return <div className="p-6 text-slate-400 text-sm">不存在</div>

  return (
    <div className="p-4 max-w-2xl">
      <button onClick={() => navigate({ to: '/knowledge' })} className="text-[12px] text-[#0052CC] hover:underline mb-2 inline-block">&larr; 返回知识库</button>

      <div className="bg-white rounded-[4px] border border-slate-200 shadow-sm p-5">
        <div className="flex items-center gap-1.5 mb-3">
          <span className={`text-[10px] px-1.5 py-0.5 rounded-[2px] font-medium ${TYPE_STYLE[item.type]}`}>{TYPE_LABEL[item.type]}</span>
          <span className={`text-[10px] px-1.5 py-0.5 rounded-[2px] ${STATUS_STYLE[item.status]}`}>{STATUS_LABEL[item.status]}</span>
        </div>

        {editing ? (
          <div className="space-y-3">
            <input className={inputCls + ' font-semibold'} value={title} onChange={e => setTitle(e.target.value)} />
            <textarea className={inputCls} rows={10} value={content} onChange={e => setContent(e.target.value)} />
            <div className="flex gap-2">
              <button onClick={async () => {
                await knowledgeApi.update(itemId, { title, content })
                queryClient.invalidateQueries({ queryKey: ['knowledge', itemId] })
                setEditing(false)
              }} className="text-[12px] bg-[#0052CC] text-white px-3 py-1 rounded-[3px]">保存</button>
              <button onClick={() => setEditing(false)} className="text-[12px] border px-3 py-1 rounded-[3px]">取消</button>
            </div>
          </div>
        ) : (
          <>
            <h1 className="text-[15px] font-semibold text-slate-800 mb-3">{item.title}</h1>
            <div className="text-[13px] text-slate-700 whitespace-pre-wrap leading-relaxed mb-4">{item.content}</div>
            {item.defectId && (
              <p className="text-[12px] text-slate-400 mb-4">
                来源: <a href={`/defects/${item.defectId}`} className="text-[#0052CC] hover:underline">{item.defectTitle || `#${item.defectId}`}</a>
              </p>
            )}
            <div className="flex gap-2">
              {user?.role === 'ENGINEER' && (
                <>
                  <button onClick={() => { setTitle(item.title); setContent(item.content); setEditing(true) }}
                    className="text-[12px] border border-slate-200 px-3 py-1 rounded-[3px] hover:bg-slate-50">编辑</button>
                  {item.status !== 'PUBLISHED' && (
                    <button onClick={async () => {
                      await knowledgeApi.publish(itemId)
                      queryClient.invalidateQueries({ queryKey: ['knowledge', itemId] })
                    }} className="text-[12px] bg-green-600 text-white px-3 py-1 rounded-[3px] hover:bg-green-700">发布</button>
                  )}
                </>
              )}
            </div>
          </>
        )}

        <div className="mt-5 pt-4 border-t border-slate-100 text-[11px] text-slate-400">
          创建: {fmtDateTime(item.createdAt)}
          {item.publishedAt && <> · 发布: {fmtDateTime(item.publishedAt)}</>}
        </div>
      </div>
    </div>
  )
}
