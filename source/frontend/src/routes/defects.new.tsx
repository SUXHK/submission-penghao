import { createFileRoute, useNavigate } from '@tanstack/react-router'
import { useEffect } from 'react'

export const Route = createFileRoute('/defects/new')({ component: RedirectHome })
function RedirectHome() {
  const navigate = useNavigate()
  useEffect(() => { navigate({ to: '/', replace: true }) }, [])
  return null
}
