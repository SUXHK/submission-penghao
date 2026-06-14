import { createFileRoute, useNavigate, useSearch } from '@tanstack/react-router'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, useMemo, useEffect } from 'react'
import { z } from 'zod/v4'
import {
  useReactTable, getCoreRowModel, getSortedRowModel, getPaginationRowModel,
  flexRender, createColumnHelper, type SortingState,
} from '@tanstack/react-table'
import { fmtDate } from '#/lib/utils'
import { AlertCircle, AlertTriangle, Info, Flag, Minus, Plus } from 'lucide-react'
import { defectApi } from '#/api/defects'
import { DefectDetailSheet } from '#/components/defect/DefectDetailSheet'
import type { DefectStatus, DefectListItem } from '#/types/defect'

const searchSchema = z.object({ defect: z.number().optional().catch(undefined) })
export const Route = createFileRoute('/')({
  component: DefectListPage,
  validateSearch: (s) => searchSchema.parse(s),
})

const ALL_STATUSES: DefectStatus[] = ['DRAFT','REPORTED','TRIAGING','ANALYZED','PLANNED','IN_REPAIR','FIXED','VERIFIED','CLOSED']

const STATUS_LABEL: Record<string, string> = {
  DRAFT: '草稿', REPORTED: '已登记', TRIAGING: '分诊中', ANALYZED: '已分析',
  PLANNED: '已计划', IN_REPAIR: '修复中', FIXED: '已修复', VERIFIED: '已验证',
  CLOSED: '已关闭', REOPENED: '重新打开',
}
const STATUS_STYLE: Record<string, string> = {
  DRAFT: 'bg-slate-200 text-slate-600', REPORTED: 'bg-blue-100 text-blue-700',
  TRIAGING: 'bg-amber-100 text-amber-800', ANALYZED: 'bg-purple-100 text-purple-700',
  PLANNED: 'bg-indigo-100 text-indigo-700', IN_REPAIR: 'bg-orange-100 text-orange-800',
  FIXED: 'bg-green-100 text-green-700', VERIFIED: 'bg-teal-100 text-teal-700',
  CLOSED: 'bg-slate-200 text-slate-500', REOPENED: 'bg-red-100 text-red-700',
}
const PRIORITY_STYLE: Record<string, string> = {
  P0: 'bg-red-600 text-white', P1: 'bg-orange-500 text-white',
  P2: 'bg-amber-500 text-white', P3: 'bg-blue-400 text-white', P4: 'bg-slate-300 text-slate-600',
}
const PRIORITY_ICON_CLASS: Record<string, string> = {
  P0: 'text-red-500', P1: 'text-orange-500', P2: 'text-amber-500', P3: 'text-blue-500', P4: 'text-slate-400',
}
const PRIORITY_ICON_COMPONENT: Record<string, typeof AlertCircle> = {
  P0: AlertCircle, P1: AlertTriangle, P2: Info, P3: Flag, P4: Minus,
}

const inputCls = "w-full border border-slate-200 rounded-[3px] px-2.5 py-1.5 text-[13px] focus:outline-none focus:border-[#0052CC]"

function StatsBar({ defects }: { defects: any[] }) {
  const total = defects.length
  if (total === 0) return null
  const sc: Record<string, number> = {}
  defects.forEach(d => { sc[d.status] = (sc[d.status] || 0) + 1 })
  const segs = [
    { k: 'DRAFT', c: '#cbd5e1', l: '草稿' }, { k: 'REPORTED', c: '#3b82f6', l: '已登记' },
    { k: 'TRIAGING', c: '#f59e0b', l: '分诊中' }, { k: 'ANALYZED', c: '#8b5cf6', l: '已分析' },
    { k: 'PLANNED', c: '#6366f1', l: '已计划' }, { k: 'IN_REPAIR', c: '#f97316', l: '修复中' },
    { k: 'FIXED', c: '#22c55e', l: '已修复' }, { k: 'VERIFIED', c: '#14b8a6', l: '已验证' },
    { k: 'CLOSED', c: '#94a3b8', l: '已关闭' },
  ].filter(s => (sc[s.k] || 0) > 0)

  const urgent = defects.filter(d => d.priority === 'P0' || d.priority === 'P1').length
  const closed = sc['CLOSED'] || 0
  const pct = total > 0 ? Math.round((closed / total) * 100) : 0
  const r = 22; const circ = 2 * Math.PI * r; const dash = (pct / 100) * circ

  return (
    <div className="mb-3">
      {/* Big numbers + progress ring */}
      <div className="flex items-center gap-8 mb-2">
        <div className="text-left">
          <div className="text-[28px] font-bold text-slate-800 leading-none">{total}</div>
          <div className="text-[11px] text-slate-400 mt-0.5">全部缺陷</div>
        </div>
        <div>
          <div className="text-[28px] font-bold leading-none" style={{ color: urgent > 0 ? '#ef4444' : '#94a3b8' }}>{urgent}</div>
          <div className="text-[11px] text-slate-400 mt-0.5">紧急 P0/P1</div>
        </div>
        <div>
          <div className="text-[28px] font-bold text-green-500 leading-none">{closed}</div>
          <div className="text-[11px] text-slate-400 mt-0.5">已关闭</div>
        </div>
        <div className="flex items-center gap-3 ml-auto">
          <div className="group relative cursor-help">
            <svg width="52" height="52" viewBox="0 0 56 56" className="shrink-0 -rotate-90">
              <circle cx="28" cy="28" r={r} fill="none" stroke="#e2e8f0" strokeWidth="4" />
              <circle cx="28" cy="28" r={r} fill="none" stroke="#0052CC" strokeWidth="4"
                strokeDasharray={circ} strokeDashoffset={circ - dash} strokeLinecap="round"
                style={{ transition: 'stroke-dashoffset 0.8s ease' }} />
              <text x="28" y="28" textAnchor="middle" dy=".35em" className="text-[11px] font-bold"
                fill="#0052CC" transform="rotate(90 28 28)">{pct}%</text>
            </svg>
            <div className="absolute top-1/2 -translate-y-1/2 right-full mr-2 bg-slate-800 text-white text-[10px] px-2.5 py-1 rounded-md whitespace-nowrap opacity-0 group-hover:opacity-100 transition-opacity pointer-events-none z-50">
              已关闭率 {closed}/{total} = {pct}%
              <div className="absolute top-1/2 -translate-y-1/2 -right-1 w-2 h-2 bg-slate-800 rotate-45" />
            </div>
          </div>
        </div>
      </div>
      {/* Color bar — display only, hover shows tooltip */}
      <div className="flex h-3 rounded-full shadow-sm gap-px bg-slate-200 overflow-visible">
        {segs.map((s, i, arr) => {
          const isFirst = i === 0; const isLast = i === arr.length - 1
          return (
          <div key={s.k} className="group relative cursor-pointer"
            style={{ width: `${((sc[s.k] || 0) / total) * 100}%` }}>
            <div className={`w-full h-full ${isFirst ? 'rounded-l-full' : ''} ${isLast ? 'rounded-r-full' : ''}`}
              style={{ backgroundColor: s.c }} />
            <div className="absolute -top-10 left-1/2 -translate-x-1/2 bg-slate-800 text-white text-[11px] px-2.5 py-1.5 rounded-lg whitespace-nowrap opacity-0 group-hover:opacity-100 transition-opacity pointer-events-none z-50 shadow-lg">
              {s.l} · {sc[s.k]} 条 · {Math.round(((sc[s.k] || 0) / total) * 100)}%
              <div className="absolute -bottom-1.5 left-1/2 -translate-x-1/2 w-3 h-3 bg-slate-800 rotate-45" />
            </div>
          </div>
        )})}
      </div>
    </div>
  )
}

function DefectListPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const search = useSearch({ from: '/' }) as { defect?: number }
  const [statusFilter, setStatusFilter] = useState('')
  const [keyword, setKeyword] = useState('')
  const [showCreate, _setShowCreate] = useState(false)
  const [modalVisible, setModalVisible] = useState(false)

  const openCreate = () => { _setShowCreate(true)
    requestAnimationFrame(() => requestAnimationFrame(() => setModalVisible(true)))
  }
  const closeCreate = () => {
    setModalVisible(false)
    setTimeout(() => { _setShowCreate(false) }, 200)
  }
  const [selectedDefectId, setSelectedDefectId] = useState<number | null>(null)

  // Restore Sheet from URL on page load
  useEffect(() => {
    if (search.defect) setSelectedDefectId(search.defect)
  }, [])

  // Sync Sheet open state to URL
  const selectDefect = (id: number | null) => {
    setSelectedDefectId(id)
    if (id) {
      navigate({ to: '.', search: { defect: id }, replace: true })
    } else {
      navigate({ to: '.', search: {}, replace: true })
    }
  }
  const [form, setForm] = useState({ title:'', description:'', phenomenon:'', environment:'', reproductionSteps:'', expectedResult:'', actualResult:'' })
  const [createError, setCreateError] = useState('')
  const [creating, setCreating] = useState(false)

  // Single query for all defects
  const { data: allDefects, isLoading } = useQuery({
    queryKey: ['defects', 'all'],
    queryFn: () => defectApi.list(),
    staleTime: 30_000,
  })

  // Client-side filtering — instant, no network delay
  const filtered = useMemo(() => {
    let list = allDefects || []
    if (statusFilter) list = list.filter(d => d.status === statusFilter)
    if (keyword) {
      const kw = keyword.toLowerCase()
      list = list.filter(d => d.title.toLowerCase().includes(kw))
    }
    return list
  }, [allDefects, statusFilter, keyword])
  const [sorting, setSorting] = useState<SortingState>([])

  const columnHelper = createColumnHelper<DefectListItem>()
  const columns = useMemo(() => [
    columnHelper.accessor('priority', {
      id: 'priorityIcon', header: '', size: 28,
      cell: info => {
        const p = info.getValue()
        if (p && PRIORITY_ICON_COMPONENT[p]) {
          const Icon = PRIORITY_ICON_COMPONENT[p]
          return <div className="flex justify-center"><Icon size={14} className={PRIORITY_ICON_CLASS[p]} /></div>
        }
        return <div className="flex justify-center"><Minus size={14} className="text-slate-300" /></div>
      },
      enableSorting: false,
    }),
    columnHelper.accessor('title', {
      header: '标题', size: 320,
      cell: info => <span className="text-slate-800 font-medium">{info.getValue()}</span>,
    }),
    columnHelper.accessor('status', {
      header: '状态', size: 90,
      cell: info => <span className={`text-[11px] px-1.5 py-0.5 rounded-[3px] font-medium whitespace-nowrap ${STATUS_STYLE[info.getValue()] || ''}`}>{STATUS_LABEL[info.getValue()] || info.getValue()}</span>,
    }),
    columnHelper.accessor('priority', {
      id: 'priorityLabel', header: '优先级', size: 72,
      cell: info => info.getValue()
        ? <span className={`text-[11px] px-1.5 py-0.5 rounded-[3px] font-bold ${PRIORITY_STYLE[info.getValue()!]}`}>{info.getValue()}</span>
        : <span className="text-slate-300">-</span>,
    }),
    columnHelper.accessor('severity', {
      header: '严重度', size: 60,
      cell: info => <span className="text-slate-500">{info.getValue() ?? '-'}</span>,
    }),
    columnHelper.accessor('reporterName', {
      header: '报告人', size: 100,
      cell: info => <span className="text-slate-600 text-[12px]">{info.getValue() ?? '-'}</span>,
    }),
    columnHelper.accessor('assigneeName', {
      header: '负责人', size: 100,
      cell: info => <span className="text-slate-600 text-[12px]">{info.getValue() ?? '-'}</span>,
    }),
    columnHelper.accessor('createdAt', {
      header: '创建', size: 100,
      cell: info => <span className="text-slate-400 text-[11px] whitespace-nowrap">{fmtDate(info.getValue())}</span>,
    }),
  ], [])

  const table = useReactTable({
    data: filtered,
    columns,
    state: { sorting },
    onSortingChange: setSorting,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
    getPaginationRowModel: getPaginationRowModel(),
    initialState: { pagination: { pageSize: 17 } },
  })

  const handleCreate = async (asReported: boolean) => {
    setCreateError('')
    if (!form.title.trim()) { setCreateError('请输入标题'); return }
    setCreating(true)
    let createdId: number | null = null
    try {
      const created = await defectApi.create(form)
      createdId = created.id
      if (asReported) await defectApi.transition(created.id, 'REPORTED')
      closeCreate()
      setForm({ title:'', description:'', phenomenon:'', environment:'', reproductionSteps:'', expectedResult:'', actualResult:'' })
      queryClient.invalidateQueries({ queryKey: ['defects'] })
      selectDefect(created.id)
    } catch (err) {
      const msg = err instanceof Error ? err.message : '创建失败'
      if (createdId) {
        try { await defectApi.delete(createdId) } catch (_) { /* best effort */ }
        setCreateError('提交失败：' + msg + '（草稿已清除）')
      } else {
        setCreateError(msg)
      }
    } finally { setCreating(false) }
  }

  const update = (f: string, v: string) => setForm(prev => ({ ...prev, [f]: v }))

  return (
    <div className="h-[calc(100vh-40px)] flex flex-col p-4">
      <div className="flex items-center justify-between mb-3 shrink-0">
        <h1 className="text-base font-semibold text-slate-800">缺陷列表</h1>
        <button onClick={() => openCreate()}
          className="bg-[#0052CC] hover:bg-[#0747A6] text-white text-[13px] px-3 py-1.5 rounded-[3px] font-medium inline-flex items-center whitespace-nowrap">
          <Plus size={14} className="mr-1" />创建缺陷
        </button>
      </div>

      {/* Create modal — centered dialog with animation */}
      {showCreate && (
        <div className="fixed inset-0 z-50 flex items-center justify-center">
          <div className={`absolute inset-0 bg-black/40 transition-opacity duration-200 ${modalVisible ? 'opacity-100' : 'opacity-0'}`} onClick={closeCreate} />
          <div className={`relative bg-white shadow-2xl w-[600px] max-h-[85vh] rounded-xl overflow-hidden transition-all duration-200 ease-out ${modalVisible ? 'opacity-100 scale-100 translate-y-0' : 'opacity-0 scale-95 translate-y-2'}`}>
            <div className="flex items-center justify-between px-5 py-3 border-b border-slate-200">
              <h2 className="text-[15px] font-semibold text-slate-800">创建缺陷</h2>
              <button onClick={() => closeCreate()} className="text-slate-400 hover:text-slate-600 text-xl leading-none w-7 h-7 flex items-center justify-center rounded hover:bg-slate-100">&times;</button>
            </div>
            <div className="overflow-y-auto max-h-[calc(85vh-120px)]">
            <div className="px-5 py-4 space-y-4">
              {createError && <div className="bg-red-50 text-red-600 text-[12px] p-2 rounded-[3px] border border-red-200">{createError}</div>}
              <div>
                <label className="block text-[12px] font-medium text-slate-600 mb-1">标题 <span className="text-red-500">*</span></label>
                <input className={inputCls} value={form.title} onChange={e => update('title', e.target.value)} placeholder="缺陷标题" />
              </div>
              <div>
                <label className="block text-[12px] font-medium text-slate-600 mb-1">描述</label>
                <textarea className={inputCls} rows={2} value={form.description} onChange={e => update('description', e.target.value)} placeholder="详细描述" />
              </div>
              <div className="border-t pt-4">
                <h3 className="text-[11px] font-medium text-slate-400 uppercase tracking-wide mb-3">复现信息 (提交分诊时必填)</h3>
                <div className="space-y-3">
                  <div>
                    <label className="block text-[12px] text-slate-500 mb-1">现象描述</label>
                    <textarea className={inputCls} rows={2} value={form.phenomenon} onChange={e => update('phenomenon', e.target.value)} placeholder="缺陷具体现象" />
                  </div>
                  <div>
                    <label className="block text-[12px] text-slate-500 mb-1">运行环境</label>
                    <input className={inputCls} value={form.environment} onChange={e => update('environment', e.target.value)} placeholder="Chrome 120, Windows 11" />
                  </div>
                  <div>
                    <label className="block text-[12px] text-slate-500 mb-1">复现步骤</label>
                    <textarea className={inputCls} rows={3} value={form.reproductionSteps} onChange={e => update('reproductionSteps', e.target.value)} placeholder="1. 步骤一&#10;2. 步骤二" />
                  </div>
                  <div className="grid grid-cols-2 gap-3">
                    <div>
                      <label className="block text-[12px] text-slate-500 mb-1">期望结果</label>
                      <textarea className={inputCls} rows={2} value={form.expectedResult} onChange={e => update('expectedResult', e.target.value)} placeholder="正常行为" />
                    </div>
                    <div>
                      <label className="block text-[12px] text-slate-500 mb-1">实际结果</label>
                      <textarea className={inputCls} rows={2} value={form.actualResult} onChange={e => update('actualResult', e.target.value)} placeholder="异常行为" />
                    </div>
                  </div>
                </div>
              </div>
            </div>
            </div>
            <div className="bg-slate-50 px-5 py-3 border-t border-slate-200 flex gap-2 justify-end rounded-b-xl">
              <button onClick={() => handleCreate(false)} disabled={creating}
                className="text-[13px] border border-slate-200 bg-white px-4 py-1.5 rounded-[3px] hover:bg-slate-100 disabled:opacity-50">保存草稿</button>
              <button onClick={() => handleCreate(true)} disabled={creating}
                className="text-[13px] bg-[#0052CC] hover:bg-[#0747A6] text-white px-4 py-1.5 rounded-[3px] font-medium disabled:opacity-50">提交分诊</button>
            </div>
          </div>
        </div>
      )}

      {/* Stats bar */}
      <div className="shrink-0"><StatsBar defects={allDefects || []} /></div>

      {/* Filters */}
      <div className="flex gap-2 mb-2 shrink-0">
        <div className="flex gap-0.5 flex-wrap">
          <button onClick={() => setStatusFilter('')}
            className={`text-[12px] px-2.5 py-1 rounded-[3px] font-medium transition-colors ${!statusFilter ? 'bg-[#0052CC] text-white' : 'bg-white text-slate-600 hover:bg-slate-100 border border-slate-200'}`}>
            全部
          </button>
          {ALL_STATUSES.map(s => (
            <button key={s} onClick={() => setStatusFilter(statusFilter === s ? '' : s)}
              className={`text-[12px] px-2.5 py-1 rounded-[3px] transition-colors flex items-center gap-1.5 ${statusFilter === s ? 'bg-[#0052CC] text-white' : 'bg-white text-slate-600 hover:bg-slate-100 border border-slate-200'}`}>
              <span className={`w-2 h-2 rounded-full shrink-0 ${statusFilter === s ? 'bg-white' : { DRAFT:'bg-slate-400', REPORTED:'bg-blue-500', TRIAGING:'bg-amber-500', ANALYZED:'bg-purple-500', PLANNED:'bg-indigo-500', IN_REPAIR:'bg-orange-500', FIXED:'bg-green-500', VERIFIED:'bg-teal-500', CLOSED:'bg-slate-400', REOPENED:'bg-red-500' }[s]}`} />
              {STATUS_LABEL[s]} <span className="font-medium">{allDefects?.filter(d => d.status === s).length || 0}</span>
            </button>
          ))}
        </div>
        <input type="text" placeholder="搜索…" value={keyword}
          onChange={e => setKeyword(e.target.value)}
          className="text-[12px] border border-slate-200 rounded-[3px] px-2.5 py-1 w-52 bg-white focus:outline-none focus:border-[#0052CC] ml-auto" />
      </div>

      {isLoading ? (
        <div className="text-center py-20 text-slate-400 text-sm">加载中…</div>
      ) : (
        <div className="flex-1 min-h-0 bg-white rounded-[4px] border border-slate-200 shadow-sm flex flex-col overflow-hidden">
          <div className="overflow-auto flex-1">
          <table className="w-full">
            <thead className="sticky top-0 z-10">
              {table.getHeaderGroups().map(hg => (
                <tr key={hg.id} className="bg-[#F4F5F7] border-b border-slate-200 text-[11px] text-slate-500 font-medium uppercase tracking-wide">
                  {hg.headers.map(header => (
                    <th key={header.id} className="text-left py-2 select-none px-2"
                      style={{ width: header.getSize() }}
                      onClick={header.column.getCanSort() ? header.column.getToggleSortingHandler() : undefined}>
                      <div className="flex items-center gap-1 cursor-pointer hover:text-slate-700">
                        {flexRender(header.column.columnDef.header, header.getContext())}
                        {{ asc: ' ▲', desc: ' ▼' }[header.column.getIsSorted() as string] ?? ''}
                      </div>
                    </th>
                  ))}
                </tr>
              ))}
            </thead>
            <tbody className="text-[13px]">
              {table.getRowModel().rows.map(row => {
                  const defId = (row.original as DefectListItem).id
                  const isSelected = defId === selectedDefectId
                  return (
                <tr key={row.id}
                  onClick={() => selectDefect(defId)}
                  className={`border-b border-slate-100 cursor-pointer transition-colors ${isSelected ? 'bg-[#DEEBFF] shadow-[inset_3px_0_0_#0052CC]' : 'hover:bg-[#F4F5F7]'}`}>
                  {row.getVisibleCells().map(cell => (
                    <td key={cell.id} className="py-2.5 px-2">
                      {flexRender(cell.column.columnDef.cell, cell.getContext())}
                    </td>
                  ))}
                </tr>
                  )
              })}
              {filtered.length === 0 && (
                <tr><td colSpan={8} className="text-center py-16 text-slate-400 text-sm">暂无匹配的缺陷</td></tr>
              )}
            </tbody>
          </table>
          </div>
        </div>
      )}

      {table.getPageCount() > 1 && (
        <div className="flex items-center justify-center gap-2 py-2 border-t border-slate-100 text-[13px] bg-white">
          <button disabled={!table.getCanPreviousPage()} onClick={() => table.previousPage()}
            className="px-2 py-1 border border-slate-200 rounded-[3px] disabled:opacity-30 bg-white hover:bg-slate-50">上一页</button>
          <span className="text-slate-500">{table.getState().pagination.pageIndex + 1} / {table.getPageCount()}</span>
          <button disabled={!table.getCanNextPage()} onClick={() => table.nextPage()}
            className="px-2 py-1 border border-slate-200 rounded-[3px] disabled:opacity-30 bg-white hover:bg-slate-50">下一页</button>
        </div>
      )}

      <DefectDetailSheet defectId={selectedDefectId ?? 0} onClose={() => { selectDefect(null); queryClient.invalidateQueries({ queryKey: ['defects'] }) }} />
    </div>
  )
}
