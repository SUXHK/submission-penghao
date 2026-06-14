import { Eye, EyeOff } from 'lucide-react'
import { createFileRoute, useNavigate } from '@tanstack/react-router'
import { useState, useEffect } from 'react'
import { useAuth } from '#/contexts/AuthContext'
import { useToast } from '#/contexts/ToastContext'
import type { RegisterRequest } from '#/types/user'

export const Route = createFileRoute('/register')({ component: RegisterPage })

const inputCls = "w-full border border-slate-200 rounded-[3px] px-3 py-2 text-[13px] focus:outline-none focus:border-[#0052CC]"

function RegisterPage() {
  const { register, user } = useAuth()
  const { toast } = useToast()
  const navigate = useNavigate()
  const [form, setForm] = useState<RegisterRequest>({ username:'', password:'', displayName:'', role:'SUBMITTER' })
  const [confirmPwd, setConfirmPwd] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const [showPwd, setShowPwd] = useState(false)

  useEffect(() => {
    if (user) navigate({ to: '/' })
  }, [user, navigate])

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault(); setError('')
    if (form.password.length < 6) { setError('密码至少6位'); toast('密码至少6位', 'error'); return }
    if (form.password !== confirmPwd) { setError('两次密码不一致'); toast('两次密码不一致', 'error'); return }
    setLoading(true)
    try {
      await register(form)
      toast('注册成功，请登录', 'success')
      navigate({ to: '/login' })
    }
    catch (err) {
      const msg = err instanceof Error ? err.message : '注册失败'
      setError(msg); toast(msg, 'error')
    }
    finally { setLoading(false) }
  }

  return (
    <div className="min-h-screen bg-[#F4F5F7] flex items-center justify-center relative overflow-hidden">
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
          <h1 className="text-xl font-bold text-[#0747A6] tracking-tight">DefectTriage</h1>
          <p className="text-[12px] text-slate-400 mt-1">注册新账号</p>
        </div>
        <form onSubmit={handleSubmit} className="space-y-3">
          {error && <div className="bg-red-50 text-red-600 text-[12px] p-2 rounded-[3px] border border-red-200">{error}</div>}
          <div>
            <label className="block text-[12px] font-medium text-slate-600 mb-1">用户名</label>
            <input className={inputCls} placeholder="请输入用户名" value={form.username} onChange={e => setForm({...form, username:e.target.value})} required />
          </div>
          <div>
            <label className="block text-[12px] font-medium text-slate-600 mb-1">显示名称</label>
            <input className={inputCls} placeholder="请输入显示名称" value={form.displayName} onChange={e => setForm({...form, displayName:e.target.value})} required />
          </div>
          <div>
            <label className="block text-[12px] font-medium text-slate-600 mb-1">角色</label>
            <select className={inputCls} value={form.role} onChange={e => setForm({...form, role: e.target.value as RegisterRequest['role']})}>
              <option value="SUBMITTER">提交人 (Submitter)</option>
              <option value="ENGINEER">工程师 (Engineer)</option>
              <option value="QA">质量保证 (QA)</option>
            </select>
          </div>
          <div>
            <label className="block text-[12px] font-medium text-slate-600 mb-1">密码 (≥6位)</label>
            <div className="relative">
              <input type={showPwd ? 'text' : 'password'} className={inputCls + ' pr-10'} placeholder="请输入密码（至少6位）" value={form.password} onChange={e => setForm({...form, password:e.target.value})} required />
              <button type="button" onClick={() => setShowPwd(!showPwd)}
                className="absolute right-2 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600">
                {showPwd ? <EyeOff size={14} /> : <Eye size={14} />}
              </button>
            </div>
          </div>
          <div>
            <label className="block text-[12px] font-medium text-slate-600 mb-1">确认密码</label>
            <div className="relative">
              <input type={showPwd ? 'text' : 'password'} className={inputCls + ' pr-10'} placeholder="请再次输入密码" value={confirmPwd} onChange={e => setConfirmPwd(e.target.value)} required />
              <button type="button" onClick={() => setShowPwd(!showPwd)}
                className="absolute right-2 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600">
                {showPwd ? <EyeOff size={14} /> : <Eye size={14} />}
              </button>
            </div>
          </div>
          <button type="submit" disabled={loading}
            className="w-full bg-[#0052CC] hover:bg-[#0747A6] text-white text-[13px] py-2 rounded-[3px] font-medium disabled:opacity-50 transition-colors">
            {loading ? '注册中…' : '注册'}
          </button>
          <p className="text-center text-[12px] text-slate-400">
            已有账号？<a href="/login" className="text-[#0052CC] hover:underline">登录</a>
          </p>
        </form>
      </div>
    </div>
  )
}
