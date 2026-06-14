import type { ClassValue } from "clsx"

import { clsx } from "clsx"
import { twMerge } from "tailwind-merge"

export function cn(...inputs: ClassValue[]) {
    return twMerge(clsx(inputs))
}

export function fmtDate(ts: string | null | undefined): string {
  if (!ts) return ''
  return ts.slice(0, 10)
}

export function fmtDateTime(ts: string | null | undefined): string {
  if (!ts) return ''
  return ts.slice(0, 10) + ' ' + ts.slice(11, 19)
}
