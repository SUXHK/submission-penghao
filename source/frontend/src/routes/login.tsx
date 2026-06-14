import { createFileRoute, useNavigate } from '@tanstack/react-router'
import { useState, useEffect } from 'react'
import { useAuth } from '#/contexts/AuthContext'
import { useToast } from '#/contexts/ToastContext'
import { LogIn, User, Lock, ChevronRight, Eye, EyeOff } from 'lucide-react'
import { Logo } from '#/components/Logo'

export const Route = createFileRoute('/login')({ component: LoginPage })

const inputCls = "w-full border border-slate-200 rounded-[3px] px-3 py-2 text-[13px] focus:outline-none focus:border-[#0052CC]"

function LoginPage() {
  const { login, user } = useAuth()
  const { toast } = useToast()
  const navigate = useNavigate()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const [showPwd, setShowPwd] = useState(false)

  useEffect(() => {
    if (user) navigate({ to: '/' })
  }, [user, navigate])

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault(); setError(''); setLoading(true)
    try {
      await login({ username, password })
      toast('登录成功', 'success')
      navigate({ to: '/' })
    }
    catch (err) {
      const msg = err instanceof Error ? err.message : '登录失败'
      setError(msg); toast(msg, 'error')
    }
    finally { setLoading(false) }
  }

  const fillCreds = (u: string, p: string) => { setUsername(u); setPassword(p) }
  const DEMOS = [
    { role: '提交人', user: 'submitter', pwd: 'admin123', color: 'bg-blue-500', perm: '创建缺陷 · 填写复现信息 · 提交分诊' },
    { role: '工程师', user: 'engineer', pwd: 'admin123', color: 'bg-amber-500', perm: '分诊评估 · 根因分析 · 修复执行 · 审核AI' },
    { role: 'QA', user: 'qa', pwd: 'admin123', color: 'bg-green-500', perm: '验证修复 · 回归测试 · 关闭缺陷' },
  ]

  return (
    <div className="min-h-screen bg-[#F4F5F7] flex items-center justify-center relative overflow-hidden">
      {/* Jira-style background */}
      <div className="absolute inset-0 bg-gradient-to-br from-[#F4F5F7] via-[#E8ECF1] to-[#DEE3EA] pointer-events-none" />
      <div className="absolute inset-0 pointer-events-none">
        <div className="absolute inset-0 opacity-[0.12]" style={{ backgroundImage: 'radial-gradient(circle, #0747A6 1.5px, transparent 1.5px)', backgroundSize: '20px 20px' }} />
        <div className="absolute inset-0 opacity-[0.06]" style={{ backgroundImage: 'linear-gradient(45deg, #0052CC 1px, transparent 1px), linear-gradient(-45deg, #0052CC 1px, transparent 1px)', backgroundSize: '60px 60px' }} />
      </div>
      <div className="absolute top-[10%] left-[5%] w-24 h-24 border-[12px] border-[#0052CC]/[0.08] rounded-lg rotate-12 pointer-events-none" />
      <div className="absolute bottom-[15%] right-[8%] w-32 h-32 border-[16px] border-[#0747A6]/[0.06] rounded-full pointer-events-none" />
      <div className="absolute top-[30%] right-[15%] w-16 h-16 bg-[#0052CC]/[0.06] rounded-lg -rotate-6 pointer-events-none" />
      <div className="absolute top-[-10%] right-[-5%] w-[500px] h-[500px] rounded-full bg-[#0052CC]/[0.08] blur-3xl pointer-events-none" />
      <div className="absolute bottom-[-10%] left-[-5%] w-[400px] h-[400px] rounded-full bg-[#0747A6]/[0.06] blur-3xl pointer-events-none" />

      <div className="bg-white/95 backdrop-blur rounded-xl shadow-xl border border-slate-200/60 p-8 w-96 relative z-10">
        <div className="text-center mb-6">
          <div className="flex items-center justify-center gap-2 mb-1">
            <Logo size={28} className="text-[#0747A6]" />
            <h1 className="text-xl font-bold text-[#0747A6] tracking-tight">DefectTriage</h1>
          </div>
          <p className="text-[12px] text-slate-400 mt-1">缺陷分诊与修复闭环系统</p>
        </div>
        <form onSubmit={handleSubmit} className="space-y-3">
          {error && <div className="bg-red-50 text-red-600 text-[12px] p-2 rounded-[3px] border border-red-200">{error}</div>}
          <div>
            <label className="block text-[12px] font-medium text-slate-600 mb-1">用户名</label>
            <div className="relative">
              <User size={14} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-slate-400" />
              <input className={inputCls + ' pl-8'} placeholder="请输入用户名" value={username} onChange={e => setUsername(e.target.value)} required autoFocus />
            </div>
          </div>
          <div>
            <label className="block text-[12px] font-medium text-slate-600 mb-1">密码</label>
            <div className="relative">
              <Lock size={14} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-slate-400" />
              <input type={showPwd ? 'text' : 'password'} className={inputCls + ' pl-8 pr-10'} placeholder="请输入密码" value={password} onChange={e => setPassword(e.target.value)} required />
              <button type="button" onClick={() => setShowPwd(!showPwd)}
                className="absolute right-2 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600">
                {showPwd ? <EyeOff size={14} /> : <Eye size={14} />}
              </button>
            </div>
          </div>
          <button type="submit" disabled={loading}
            className="w-full bg-[#0052CC] hover:bg-[#0747A6] text-white text-[13px] py-2 rounded-[3px] font-medium disabled:opacity-50 transition-colors flex items-center justify-center gap-1.5">
            <LogIn size={14} />{loading ? '登录中…' : '登录'}
          </button>
          <p className="text-center text-[12px] text-slate-400">
            没有账号？<a href="/register" className="text-[#0052CC] hover:underline ml-1">注册</a>
          </p>
        </form>
        <div className="mt-5 pt-4 border-t border-slate-100">
          <p className="text-[11px] font-medium text-slate-400 mb-2">快速填入演示账号</p>
          <div className="flex gap-1.5">
            {DEMOS.map(d => (
              <div key={d.user} className="flex-1 group relative">
                <button
                  onClick={() => fillCreds(d.user, d.pwd)}
                  className="w-full text-[10px] font-medium px-2 py-1.5 rounded-md border border-slate-200 hover:border-slate-300 hover:bg-slate-50 transition-colors text-slate-600 flex items-center justify-center gap-1">
                  <span className={`w-1.5 h-1.5 rounded-full ${d.color}`} />
                  {d.role}
                  <ChevronRight size={10} className="text-slate-300" />
                </button>
                <div className="pointer-events-none absolute bottom-full left-1/2 -translate-x-1/2 mb-1.5 w-44 bg-slate-800 text-white text-[10px] rounded-md px-2.5 py-1.5 leading-relaxed opacity-0 group-hover:opacity-100 transition-opacity z-20 text-center">
                  {d.perm}
                  <div className="absolute top-full left-1/2 -translate-x-1/2 w-2 h-2 rotate-45 bg-slate-800" />
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  )
}
