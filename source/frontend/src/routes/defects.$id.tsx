import { createFileRoute, useNavigate } from '@tanstack/react-router'
import { useEffect } from 'react'

export const Route = createFileRoute('/defects/$id')({
  component: () => {
    const { id } = Route.useParams()
    const navigate = useNavigate()
    useEffect(() => {
      navigate({ to: '/', search: { defect: Number(id) }, replace: true })
    }, [])
    return null
  },
})
