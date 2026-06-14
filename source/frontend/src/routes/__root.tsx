import { createRootRoute, Link, Outlet, useRouter, useLocation } from '@tanstack/react-router'
import { AuthProvider, useAuth } from '#/contexts/AuthContext'
import { ToastProvider, useToast } from '#/contexts/ToastContext'
import { useEffect, useCallback } from 'react'
import { Bug, BookOpen, LogOut, User, CircleHelp, X } from 'lucide-react'
import { Logo } from '#/components/Logo'
import { useState } from 'react'
import '../styles.css'

export const Route = createRootRoute({
  component: RootComponent,
  notFoundComponent: () => (
    <main className="flex items-center justify-center h-screen bg-[#F4F5F7]">
      <div className="text-center"><h1 className="text-2xl font-bold text-slate-700">404</h1><p className="text-sm text-slate-400 mt-1">页面不存在</p></div>
    </main>
  ),
})

function RootComponent() {
  return <ToastProvider><AuthProvider><AppShell /></AuthProvider></ToastProvider>
}

function AppShell() {
  const { user, logout, isLoading } = useAuth()
  const { toast } = useToast()
  const router = useRouter()

  const handleLogout = useCallback(() => {
    logout()
    toast('已退出登录', 'success')
  }, [logout, toast])
  const { pathname } = useLocation()
  const isAuthPage = pathname === '/login' || pathname === '/register'
  const [showPerms, setShowPerms] = useState(false)

  useEffect(() => {
    if (!isLoading && !user && !isAuthPage) {
      router.navigate({ to: '/login' })
    }
  }, [isLoading, user, isAuthPage])

  if (isLoading) {
    return <div className="flex items-center justify-center h-screen bg-[#F4F5F7]"><div className="w-6 h-6 border-2 border-[#0052CC] border-t-transparent rounded-full animate-spin" /></div>
  }

  if (!user) return <Outlet />

  return (
    <div className="h-screen flex flex-col bg-[#F4F5F7]">
      <header className="h-10 bg-[#0747A6] flex items-center px-3 shrink-0 z-30">
        <Link to="/" className="flex items-center gap-1.5 text-white font-bold text-sm tracking-wide mr-5">
          <Logo size={18} className="text-white" />
          DefectTriage
        </Link>
        <div className="flex items-center gap-0.5 text-[13px]">
          <Link to="/" className={`flex items-center gap-1 px-2 py-0.5 rounded ${pathname === '/' || pathname.startsWith('/defects') ? 'bg-white/20 text-white' : 'text-white/70 hover:bg-white/10'}`}><Bug size={14} />缺陷</Link>
          <Link to="/knowledge" className={`flex items-center gap-1 px-2 py-0.5 rounded ${pathname.startsWith('/knowledge') ? 'bg-white/20 text-white' : 'text-white/70 hover:bg-white/10'}`}><BookOpen size={14} />知识库</Link>
        </div>
        <div className="ml-auto flex items-center gap-3 text-[12px] text-white/80">
          <User size={14} />
          <span>{user.displayName}</span>
          <span className="bg-white/15 px-1.5 py-0.5 rounded-[3px] text-[11px]">{user.role}</span>
          <div className="relative">
            <button onClick={() => setShowPerms(!showPerms)} className="text-white/60 hover:text-white p-0.5 rounded transition-colors">
              <CircleHelp size={15} />
            </button>
            {showPerms && (
              <div className="absolute right-0 top-7 w-64 bg-white rounded-lg shadow-2xl border border-slate-200 p-4 z-50 text-slate-700">
                <div className="flex items-center justify-between mb-3">
                  <span className="text-[13px] font-bold">角色权限说明</span>
                  <button onClick={() => setShowPerms(false)} className="text-slate-400 hover:text-slate-600"><X size={14} /></button>
                </div>
                <div className="space-y-2.5 text-[12px]">
                  <div className="p-2 rounded-md bg-blue-50 border border-blue-100">
                    <div className="font-semibold text-blue-700">提交人 (Submitter)</div>
                    <div className="text-blue-600 mt-0.5">创建缺陷 · 填写复现信息 · 提交分诊</div>
                  </div>
                  <div className="p-2 rounded-md bg-amber-50 border border-amber-100">
                    <div className="font-semibold text-amber-700">工程师 (Engineer)</div>
                    <div className="text-amber-600 mt-0.5">分诊评估 · 根因分析 · 修复计划 · 执行修复 · 审核AI建议</div>
                  </div>
                  <div className="p-2 rounded-md bg-green-50 border border-green-100">
                    <div className="font-semibold text-green-700">QA</div>
                    <div className="text-green-600 mt-0.5">验证修复 · 回归测试 · 关闭缺陷</div>
                  </div>
                </div>
              </div>
            )}
          </div>
          <button onClick={handleLogout} className="text-white/70 hover:text-white hover:bg-white/10 px-2 py-0.5 rounded transition-colors flex items-center gap-1"><LogOut size={14} />退出</button>
        </div>
      </header>
      <main className="flex-1 overflow-y-auto overflow-x-hidden">
        <Outlet />
      </main>
    </div>
  )
}
