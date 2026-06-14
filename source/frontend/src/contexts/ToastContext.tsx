import { createContext, useCallback, useContext, useState, type ReactNode } from 'react'
import { CheckCircle, XCircle } from 'lucide-react'

interface Toast { id: number; message: string; type: 'success' | 'error' }
interface ToastCtx { toast: (message: string, type?: 'success' | 'error') => void }

const ToastContext = createContext<ToastCtx | null>(null)

let nextId = 0

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([])

  const toast = useCallback((message: string, type: 'success' | 'error' = 'success') => {
    const id = nextId++
    setToasts(prev => [...prev, { id, message, type }])
    setTimeout(() => setToasts(prev => prev.filter(t => t.id !== id)), 2500)
  }, [])

  return (
    <ToastContext.Provider value={{ toast }}>
      {children}
      <div className="fixed top-4 left-1/2 -translate-x-1/2 z-[100] flex flex-col items-center gap-1.5">
        {toasts.map(t => (
          <div key={t.id}
            className={`flex items-center gap-2 px-3 py-2 rounded-lg shadow-lg text-[13px] animate-[slideUp_0.2s_ease-out] ${
              t.type === 'success' ? 'bg-green-600 text-white' : 'bg-red-600 text-white'
            }`}>
            {t.type === 'success' ? <CheckCircle size={14} /> : <XCircle size={14} />}
            {t.message}
          </div>
        ))}
      </div>
      <style>{`@keyframes slideUp { from { opacity: 0; transform: translateY(10px); } to { opacity: 1; transform: translateY(0); } }`}</style>
    </ToastContext.Provider>
  )
}

export function useToast() {
  const ctx = useContext(ToastContext)
  if (!ctx) throw new Error('useToast must be inside ToastProvider')
  return ctx
}
