import { useEffect, useState, useCallback, type ReactNode } from 'react'

interface SheetProps {
  open: boolean
  onClose: () => void
  children: ReactNode
  title?: string
}

export function Sheet({ open, onClose, children, title }: SheetProps) {
  const [phase, setPhase] = useState<'hidden' | 'enter' | 'idle' | 'leave'>('hidden')

  useEffect(() => {
    if (open) {
      setPhase('enter')
      document.body.style.overflow = 'hidden'
      const raf = requestAnimationFrame(() => {
        requestAnimationFrame(() => setPhase('idle'))
      })
      return () => cancelAnimationFrame(raf)
    }
  }, [open])

  const doClose = useCallback(() => {
    setPhase('leave')
    const timer = setTimeout(() => {
      setPhase('hidden')
      document.body.style.overflow = ''
      onClose()
    }, 250)
    return () => clearTimeout(timer)
  }, [onClose])

  if (phase === 'hidden') return null

  const panelClass = phase === 'idle' ? 'translate-x-0'
    : phase === 'leave' ? 'translate-x-[105%]'
    : 'translate-x-[105%]'

  const overlayClass = phase === 'idle' ? 'opacity-100'
    : 'opacity-0'

  return (
    <div className="fixed inset-0 z-50 flex justify-end p-6">
      <div className={`absolute inset-0 bg-black/30 transition-opacity duration-250 ${overlayClass}`} onClick={doClose} />
      <div className={`relative bg-white shadow-2xl w-4/5 h-full overflow-hidden flex flex-col rounded-xl border border-slate-200
        transition-transform duration-250 ease-in-out ${panelClass}`}>
        <div className="shrink-0 flex items-center justify-between px-5 py-3 border-b border-slate-100">
          <h2 className="text-[15px] font-semibold text-slate-800 truncate">{title || '详情'}</h2>
          <button onClick={doClose}
            className="text-slate-500 hover:text-slate-800 hover:bg-slate-100 text-2xl font-bold leading-none w-9 h-9 flex items-center justify-center rounded-lg transition-colors">
            &times;
          </button>
        </div>
        <div className="flex-1 overflow-y-auto">
          {children}
        </div>
      </div>
    </div>
  )
}
