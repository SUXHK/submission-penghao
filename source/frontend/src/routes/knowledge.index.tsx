import { createFileRoute } from '@tanstack/react-router'
import { useQuery } from '@tanstack/react-query'
import { useState, useMemo } from 'react'
import {
  useReactTable, getCoreRowModel, getSortedRowModel, getPaginationRowModel,
  flexRender, createColumnHelper, type SortingState,
} from '@tanstack/react-table'
import { fmtDate } from '#/lib/utils'
import { Search, ShieldAlert, BookOpen, FlaskConical, ChevronRight, ChevronDown, Library } from 'lucide-react'
import { knowledgeApi } from '#/api/knowledge'
import { KnowledgeDetailSheet } from '#/components/knowledge/KnowledgeDetailSheet'
import type { KnowledgeType, KnowledgeItem } from '#/types/knowledge'

export const Route = createFileRoute('/knowledge/')({ component: KnowledgeListPage })

const TYPE_META: Record<string, { label: string; icon: any; color: string; bg: string }> = {
  REGRESSION_TEST: { label: '回归用例', icon: FlaskConical, color: '#12A150', bg: '#12A15008' },
  TROUBLESHOOTING: { label: '排查手册', icon: Search, color: '#2684FF', bg: '#2684FF08' },
  RISK_RULE: { label: '风险规则', icon: ShieldAlert, color: '#E5493A', bg: '#E5493A08' },
}
const STATUS_LABEL: Record<string, string> = { AUTO_GENERATED: '待审核', REVIEWED: '已审核', PUBLISHED: '已发布' }
const STATUS_COLOR: Record<string, string> = { AUTO_GENERATED: '#FFAB00', REVIEWED: '#2684FF', PUBLISHED: '#12A150' }

function KnowledgeListPage() {
  const [activeType, setActiveType] = useState<KnowledgeType | ''>('')
  const [keyword, setKeyword] = useState('')
  const [sorting, setSorting] = useState<SortingState>([{ id: 'createdAt', desc: true }])
  const [selectedId, setSelectedId] = useState<number | null>(null)

  const { data: items, isLoading } = useQuery({
    queryKey: ['knowledge', activeType, keyword],
    queryFn: () => knowledgeApi.list(activeType || undefined, keyword || undefined),
  })

  const { data: allItems } = useQuery({
    queryKey: ['knowledge', 'stats'],
    queryFn: () => knowledgeApi.list(undefined, undefined),
    staleTime: 30000,
  })

  const stats = useMemo(() => {
    const all = allItems || []
    const byType: Record<string, number> = {}
    all.forEach(i => { byType[i.type] = (byType[i.type] || 0) + 1 })
    return { total: all.length, byType }
  }, [allItems])

  const columnHelper = createColumnHelper<KnowledgeItem>()
  const columns = useMemo(() => [
    columnHelper.accessor('type', {
      id: 'type', header: '类型', size: 60,
      cell: info => {
        const meta = TYPE_META[info.getValue()]
        const Icon = meta?.icon || BookOpen
        return <Icon size={15} style={{ color: meta?.color || '#97A0AF' }} />
      },
    }),
    columnHelper.accessor('title', {
      id: 'title', header: '标题', size: 280, maxSize: 340,
      cell: info => <span className="font-medium text-slate-800 truncate block">{info.getValue()}</span>,
    }),
    columnHelper.accessor('status', {
      id: 'status', header: '状态', size: 100,
      cell: info => (
        <span className="inline-flex items-center gap-1.5 text-[11px] font-medium whitespace-nowrap"
          style={{ color: STATUS_COLOR[info.getValue()] }}>
          <span className="w-1.5 h-1.5 rounded-full" style={{ backgroundColor: STATUS_COLOR[info.getValue()] }} />
          {STATUS_LABEL[info.getValue()]}
        </span>
      ),
    }),
    columnHelper.accessor('defectTitle', {
      id: 'defectTitle', header: '来源', size: 260,
      cell: info => <span className="text-[12px] text-slate-500 truncate block">{info.getValue() || '—'}</span>,
    }),
    columnHelper.accessor('createdAt', {
      id: 'createdAt', header: '日期', size: 100,
      cell: info => <span className="text-[12px] text-slate-400 whitespace-nowrap">{fmtDate(info.getValue())}</span>,
    }),
  ], [])

  const data = useMemo(() => items || [], [items])
  const table = useReactTable({
    data,
    columns,
    state: { sorting },
    onSortingChange: setSorting,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
    getPaginationRowModel: getPaginationRowModel(),
    initialState: { pagination: { pageSize: 19 } },
  })

  return (
    <div className="flex flex-col h-full bg-white">
      {/* Jira-style header */}
      <div className="shrink-0 border-b border-slate-200 bg-white">
        <div className="px-6 pt-4 pb-3">
          <h1 className="text-xl font-semibold text-slate-900 mb-3">知识库</h1>
          <div className="flex items-center gap-3">
            <div className="flex items-center gap-1">
              <button onClick={() => setActiveType('')}
                className={`text-[12px] font-medium px-2.5 py-1 rounded-[3px] transition-colors inline-flex items-center gap-1.5 whitespace-nowrap
                  ${!activeType ? 'bg-[#0052CC] text-white' : 'text-slate-600 hover:bg-slate-100'}`}>
                <Library size={12} />
                全部 <span className="tabular-nums">{stats.total}</span>
              </button>
              {Object.entries(TYPE_META).map(([key, meta]) => {
                const Icon = meta.icon
                const active = activeType === key
                const count = stats.byType[key] || 0
                return (
                  <button key={key} onClick={() => setActiveType(active ? '' : key as KnowledgeType)}
                    className={`text-[12px] font-medium px-2.5 py-1 rounded-[3px] transition-colors inline-flex items-center gap-1.5 whitespace-nowrap
                      ${active ? 'text-white' : 'text-slate-600 hover:bg-slate-100'}`}
                    style={active ? { backgroundColor: meta.color } : undefined}>
                    <Icon size={12} />
                    {meta.label} <span className="tabular-nums">{count}</span>
                  </button>
                )
              })}
            </div>
            <div className="relative ml-auto w-56">
              <Search size={14} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-slate-400" />
              <input type="text" placeholder="搜索知识库…" value={keyword}
                onChange={e => setKeyword(e.target.value)}
                className="w-full text-[12px] border border-slate-300 rounded-[3px] pl-8 pr-3 py-1 bg-white focus:outline-none focus:border-[#0052CC] focus:ring-1 focus:ring-[#0052CC]/20" />
            </div>
          </div>
        </div>
      </div>

      {/* Table */}
      <div className="flex-1">
        {isLoading ? (
          <div className="flex items-center justify-center py-24 text-slate-400 text-[13px]">加载中…</div>
        ) : data.length === 0 ? (
          <div className="flex items-center justify-center py-24">
            <div className="text-center">
              <BookOpen size={28} className="text-slate-300 mx-auto mb-3" />
              <p className="text-[13px] font-medium text-slate-500">{keyword ? '无匹配结果' : '知识库为空'}</p>
              <p className="text-[11px] text-slate-400 mt-1">{keyword ? '尝试其他关键词' : '缺陷关闭后自动生成'}</p>
            </div>
          </div>
        ) : (
          <table className="w-full table-fixed">
            <thead className="sticky top-0 bg-slate-50 border-b border-slate-200 z-10">
              {table.getHeaderGroups().map(hg => (
                <tr key={hg.id} className="text-[11px] font-semibold text-slate-500 uppercase tracking-wide">
                  {hg.headers.map((header, i) => (
                    <th key={header.id} className={`py-2.5 select-none whitespace-nowrap ${i === 0 ? 'text-center px-3' : 'text-left px-3'}`}
                      style={{ width: header.getSize() !== 150 ? header.getSize() : undefined }}
                      onClick={header.column.getCanSort() ? header.column.getToggleSortingHandler() : undefined}>
                      <div className={`flex items-center gap-0.5 ${header.column.getCanSort() ? 'cursor-pointer hover:text-slate-700' : ''} ${i === 0 ? 'justify-center' : ''}`}>
                        {flexRender(header.column.columnDef.header, header.getContext())}
                        {{ asc: <ChevronDown size={10} className="rotate-180" />, desc: <ChevronDown size={10} /> }[header.column.getIsSorted() as string]}
                      </div>
                    </th>
                  ))}
                  <th className="w-8 pr-6 pl-2" />
                </tr>
              ))}
            </thead>
            <tbody className="text-[13px]">
              {table.getRowModel().rows.map(row => {
                const item = row.original
                const meta = TYPE_META[item.type]
                const Icon = meta?.icon || BookOpen
                return (
                  <tr key={row.id}
                    onClick={() => setSelectedId(item.id)}
                    className="border-b border-slate-100 hover:bg-[#F4F5F7] cursor-pointer transition-colors"
                    style={{ backgroundColor: meta?.bg || 'transparent' }}>
                    <td className="px-3 py-2.5">
                      <div className="flex justify-center">
                        <Icon size={15} style={{ color: meta?.color || '#97A0AF' }} />
                      </div>
                    </td>
                    <td className="px-3 py-2.5">
                      <span className="font-medium text-slate-800 hover:text-[#0052CC] transition-colors truncate block">{item.title}</span>
                    </td>
                    <td className="px-2 py-2.5 whitespace-nowrap">
                      <span className="inline-flex items-center gap-1.5 text-[11px] font-medium"
                        style={{ color: STATUS_COLOR[item.status] }}>
                        <span className="w-1.5 h-1.5 rounded-full" style={{ backgroundColor: STATUS_COLOR[item.status] }} />
                        {STATUS_LABEL[item.status]}
                      </span>
                    </td>
                    <td className="px-2 py-2.5 text-[12px] text-slate-500 whitespace-nowrap">
                      {item.defectTitle || '—'}
                    </td>
                    <td className="px-2 py-2.5 text-[12px] text-slate-400 whitespace-nowrap">
                      {fmtDate(item.createdAt)}
                    </td>
                    <td className="pr-6 pl-2 py-2.5 text-slate-300">
                      <ChevronRight size={14} />
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        )}
      </div>

      {/* Footer */}
      <div className="shrink-0 border-t border-slate-200 px-4 py-2 flex items-center justify-between text-[11px] text-slate-400">
        <span>{data.length} 条</span>
        <div className="flex items-center gap-3">
          <span>共 {table.getPageCount()} 页</span>
          <div className="flex gap-1">
            <button onClick={() => table.previousPage()} disabled={!table.getCanPreviousPage()}
              className="px-2 py-0.5 rounded-[3px] hover:bg-slate-100 disabled:opacity-30 disabled:cursor-default">上一页</button>
            <button onClick={() => table.nextPage()} disabled={!table.getCanNextPage()}
              className="px-2 py-0.5 rounded-[3px] hover:bg-slate-100 disabled:opacity-30 disabled:cursor-default">下一页</button>
          </div>
        </div>
      </div>

      <KnowledgeDetailSheet itemId={selectedId ?? 0} onClose={() => setSelectedId(null)} />
    </div>
  )
}
