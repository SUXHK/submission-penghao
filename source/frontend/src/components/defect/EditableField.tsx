import { memo } from "react"

const inputCls = "w-full border border-slate-200 rounded-[3px] px-2 py-1 text-[13px] focus:outline-none focus:border-[#0052CC]"

interface EditableFieldProps {
  label: string
  field: string
  value: string | number | null | undefined
  multiline?: boolean
  numeric?: boolean
  maxVal?: number
  required?: boolean
  editField: string | null
  editValue: string
  onEditStart: (field: string, value: string) => void
  onEditChange: (value: string) => void
  onEditSave: (field: string, editValue: string, multiline: boolean, maxVal?: number) => void
  onEditCancel: () => void
}

export const EditableField = memo(function EditableField({
  label, field, value, multiline, numeric, maxVal, required,
  editField, editValue, onEditStart, onEditChange, onEditSave, onEditCancel,
}: EditableFieldProps) {
  const isEditing = editField === field
  return (
    <div className="border-b border-slate-50 py-1.5">
      <span className="mb-0.5 block text-[11px] font-medium tracking-wide text-slate-400 uppercase">
        {label}{required && <span className="text-red-500 ml-0.5">*</span>}
      </span>
      {isEditing ? (
        <div>
          {multiline ? (
            <textarea className={`${inputCls} mb-1.5`} rows={3} value={editValue}
              onChange={(e) => onEditChange(e.target.value)} autoFocus />
          ) : numeric ? (
            <input type="number" min="0" max={maxVal ?? 999999999} className={`${inputCls} mb-1.5`}
              value={editValue} onChange={(e) => onEditChange(e.target.value)} autoFocus />
          ) : (
            <input className={`${inputCls} mb-1.5`} value={editValue}
              onChange={(e) => onEditChange(e.target.value)} autoFocus />
          )}
          <div className="mt-1.5 flex justify-end gap-1.5">
            <button onClick={() => onEditSave(field, editValue, !!multiline, maxVal)}
              className="rounded-[3px] bg-[#0052CC] px-2 py-0.5 text-[11px] text-white">保存</button>
            <button onClick={onEditCancel}
              className="rounded-[3px] border px-2 py-0.5 text-[11px]">取消</button>
          </div>
        </div>
      ) : (
        <div className="-mx-1 cursor-pointer rounded px-1 text-[13px] whitespace-pre-wrap text-slate-700 hover:bg-slate-50"
          onClick={() => onEditStart(field, value != null ? String(value) : "")}>
          {value || <span className="text-slate-300 italic">未填写</span>}
        </div>
      )}
    </div>
  )
})
