import { useState, useEffect, useMemo } from 'react'
import {
  Phone, RefreshCw, Filter, Search, Download,
  ChevronLeft, ChevronRight, PhoneCall, PhoneOff,
  PhoneMissed, Clock, CheckCircle
} from 'lucide-react'
import { useAppSelector } from '@/app/hooks'
import { agentsApi } from '@/services/endpoints'
import { api } from '@/services/api'
import Spinner    from '@/components/ui/Spinner'
import PhoneLink  from '@/components/ui/PhoneLink'
import { formatDateTime, formatPhone } from '@/utils'
import type { AgentResponse, CallHistoryResponse, CallHistoryPage as CallHistoryPageType } from '@/types'
import clsx from 'clsx'

// ── Tipos ─────────────────────────────────────────────────────────────────────
type StatusFilter = 'ALL' | 'CALLING' | 'ANSWER' | 'HANGUP' | 'BUSY' | 'NOANSWER'

interface Filters {
  search:    string
  status:    StatusFilter
  agentExt:  string
  from:      string
  to:        string
}

const PAGE_SIZES = [25, 50, 100]

// ── Configuración de estados ──────────────────────────────────────────────────
const STATUS_CONFIG: Record<string, { label: string; color: string; bg: string; icon: typeof Phone }> = {
  CALLING:   { label: 'Llamando',    color: 'text-blue-700 dark:text-blue-400',   bg: 'bg-blue-100 dark:bg-blue-900/30',   icon: PhoneCall },
  ANSWER:    { label: 'Contestada',  color: 'text-green-700 dark:text-green-400', bg: 'bg-green-100 dark:bg-green-900/30', icon: PhoneCall },
  HANGUP:    { label: 'Finalizada',  color: 'text-gray-600 dark:text-gray-400',   bg: 'bg-gray-100 dark:bg-gray-700',      icon: PhoneOff },
  BUSY:      { label: 'Ocupado',     color: 'text-orange-700 dark:text-orange-400',bg: 'bg-orange-100 dark:bg-orange-900/30',icon: PhoneMissed },
  NOANSWER:  { label: 'No contestó', color: 'text-red-700 dark:text-red-400',     bg: 'bg-red-100 dark:bg-red-900/30',     icon: PhoneMissed },
  CANCEL:    { label: 'Cancelada',   color: 'text-gray-500 dark:text-gray-400',   bg: 'bg-gray-100 dark:bg-gray-700',      icon: PhoneOff },
}

const RESULT_LABELS: Record<string, string> = {
  SALE:                   '✅ Venta',
  INTERESTED:             '👍 Interesado',
  NOT_INTERESTED:         '👎 No interesado',
  CALLBACK:               '📅 Callback',
  WRONG_NUMBER:           '❌ Número errado',
  NO_ANSWER:              '📵 No contestó',
  VOICEMAIL:              '📬 Buzón de voz',
  APPOINTMENT:            '📆 Cita agendada',
  APPOINTMENT_RESCHEDULE: '🔄 Cita reagendada',
  APPOINTMENT_CANCEL:     '❌ Cita cancelada',
  OTHER:                  '📝 Otro',
}

// ── Página ────────────────────────────────────────────────────────────────────
export default function CallHistoryPage() {
  const { roles } = useAppSelector(s => s.auth)
  const isAdmin   = roles.includes('ADMIN')

  const [data, setData]       = useState<CallHistoryPageType | null>(null)
  const [agents, setAgents]   = useState<AgentResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [showFilters, setShowFilters] = useState(false)
  const [page, setPage]       = useState(0)
  const [pageSize, setPageSize] = useState(25)

  const [filters, setFilters] = useState<Filters>({
    search:   '',
    status:   'ALL',
    agentExt: '',
    from:     '',
    to:       '',
  })

  const load = async () => {
    try {
      setLoading(true)
      const params = new URLSearchParams()
      params.set('page', String(page))
      params.set('size', String(pageSize))
      if (filters.status !== 'ALL') params.set('status', filters.status)
      if (filters.agentExt)         params.set('extension', filters.agentExt)
      if (filters.from)             params.set('from', new Date(filters.from).toISOString())
      if (filters.to)               params.set('to', new Date(filters.to + 'T23:59:59').toISOString())

      const res = await api.get(`/calls/history?${params.toString()}`)
      setData(res.data)
    } catch { setData(null) }
    finally { setLoading(false) }
  }

  useEffect(() => { load() }, [page, pageSize])
  useEffect(() => { setPage(0); load() }, [filters])
  useEffect(() => {
    if (isAdmin) agentsApi.list().then(d => setAgents(d ?? [])).catch(() => {})
  }, [])

  const setFilter = (key: keyof Filters, value: string) =>
    setFilters(f => ({ ...f, [key]: value }))

  // Filtro de búsqueda local (nombre, teléfono)
  const filtered = useMemo(() => {
    if (!data?.content) return []
    if (!filters.search) return data.content
    const q = filters.search.toLowerCase()
    return data.content.filter(c =>
      c.calledNumber?.includes(q) ||
      c.callerIdNum?.includes(q) ||
      c.leadContactName?.toLowerCase().includes(q) ||
      c.leadContactPhone?.includes(q) ||
      c.agentName?.toLowerCase().includes(q)
    )
  }, [data, filters.search])

  const exportCSV = () => {
    if (!filtered.length) return
    const headers = ['Fecha','Agente','Extensión','Número contacto','Flujo','Estado','Tipificación','Notas','Lead']
    const rows = filtered.map(c => [
      formatDateTime(c.createdAt),
      c.agentName ?? '',
      c.agentExtension ?? '',
      c.callFlow === 'out' ? (c.calledNumber ?? '') : (c.callerIdNum ?? ''),
      c.callFlow === 'out' ? 'Saliente' : 'Entrante',
      STATUS_CONFIG[c.callStatus ?? '']?.label ?? c.callStatus ?? '',
      RESULT_LABELS[c.typificationResult ?? ''] ?? c.typificationResult ?? 'Sin tipificar',
      c.typificationNotes ?? '',
      c.leadContactName ?? '',
    ])
    const csv = [headers, ...rows].map(r => r.map(v => `"${v}"`).join(',')).join('\n')
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' })
    const url  = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `historial_llamadas_${new Date().toISOString().split('T')[0]}.csv`
    link.click()
    URL.revokeObjectURL(url)
  }

  const activeFilters = [
    filters.status !== 'ALL', !!filters.agentExt,
    !!filters.from, !!filters.to,
  ].filter(Boolean).length

  const totalPages = data?.totalPages ?? 0

  return (
    <div className="p-4 md:p-6 space-y-4 animate-fade-in">

      {/* Header */}
      <div className="flex items-center justify-between flex-wrap gap-3">
        <div>
          <h1 className="text-2xl font-bold text-gray-900 dark:text-white flex items-center gap-2">
            <Phone className="w-6 h-6 text-primary-700 dark:text-primary-400" />
            Historial de llamadas
          </h1>
          <p className="text-sm text-gray-500 dark:text-gray-400 mt-0.5">
            {data ? `${data.totalElements.toLocaleString()} llamadas registradas` : '—'}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <button onClick={exportCSV} disabled={!filtered.length}
            className="btn-secondary flex items-center gap-1.5 text-sm">
            <Download className="w-4 h-4" /> Exportar CSV
          </button>
          <button
            onClick={() => setShowFilters(v => !v)}
            className={clsx(
              'flex items-center gap-1.5 px-3 py-2 rounded-lg text-sm font-medium transition-colors',
              activeFilters > 0
                ? 'bg-primary-100 text-primary-700 dark:bg-primary-900/30 dark:text-primary-400'
                : 'btn-secondary'
            )}
          >
            <Filter className="w-4 h-4" />
            Filtros
            {activeFilters > 0 && (
              <span className="w-5 h-5 rounded-full bg-primary-700 text-white text-xs
                               flex items-center justify-center">
                {activeFilters}
              </span>
            )}
          </button>
          <button onClick={load} className="btn-secondary p-2">
            <RefreshCw className={clsx('w-4 h-4', loading && 'animate-spin')} />
          </button>
        </div>
      </div>

      {/* Filtros */}
      {showFilters && (
        <div className="card p-4 space-y-3 animate-fade-in">
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
            {/* Búsqueda local */}
            <div className="relative">
              <Search className="absolute left-3 top-2.5 w-4 h-4 text-gray-400" />
              <input type="text" placeholder="Buscar número, nombre..."
                className="input pl-9 text-sm" value={filters.search}
                onChange={e => setFilter('search', e.target.value)} />
            </div>

            {/* Estado */}
            <select className="input text-sm" value={filters.status}
              onChange={e => setFilter('status', e.target.value as StatusFilter)}>
              <option value="ALL">Todos los estados</option>
              <option value="HANGUP">Finalizadas</option>
              <option value="ANSWER">Contestadas</option>
              <option value="BUSY">Ocupado</option>
              <option value="NOANSWER">No contestó</option>
              <option value="CALLING">Llamando</option>
            </select>

            {/* Agente — solo admin */}
            {isAdmin && (
              <select className="input text-sm" value={filters.agentExt}
                onChange={e => setFilter('agentExt', e.target.value)}>
                <option value="">Todos los agentes</option>
                {agents.map(a => (
                  <option key={a.id} value={a.extension}>
                    {a.name} — {a.extension}
                  </option>
                ))}
              </select>
            )}

            {/* Fecha desde */}
            <input type="date" className="input text-sm" value={filters.from}
              onChange={e => setFilter('from', e.target.value)}
              placeholder="Desde" />
          </div>

          <div className="flex items-center gap-3">
            <input type="date" className="input text-sm w-48" value={filters.to}
              onChange={e => setFilter('to', e.target.value)}
              placeholder="Hasta" />
            {activeFilters > 0 && (
              <button
                onClick={() => setFilters({ search: '', status: 'ALL', agentExt: '', from: '', to: '' })}
                className="text-xs text-red-500 hover:text-red-700 transition-colors">
                Limpiar filtros
              </button>
            )}
          </div>
        </div>
      )}

      {loading && (
        <div className="flex justify-center py-12">
          <Spinner size="lg" className="text-primary-700" />
        </div>
      )}

      {!loading && (
        <div className="card overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 dark:bg-navy-700/50 border-b border-gray-100 dark:border-gray-700">
                <tr>
                  {['Fecha', 'Agente', 'Número contacto', 'Flujo', 'Estado', 'Tipificación', 'Lead'].map(h => (
                    <th key={h} className="text-left py-3 px-3 text-xs font-semibold
                                           text-gray-500 dark:text-gray-400 uppercase tracking-wide whitespace-nowrap">
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-50 dark:divide-gray-700/50">
                {filtered.length === 0 ? (
                  <tr>
                    <td colSpan={7} className="py-16 text-center text-gray-400 text-sm">
                      Sin llamadas registradas
                    </td>
                  </tr>
                ) : filtered.map(call => {
                  const cfg = STATUS_CONFIG[call.callStatus ?? ''] ?? STATUS_CONFIG.HANGUP
                  const Icon = cfg.icon
                  const contactPhone = call.callFlow === 'out'
                    ? call.calledNumber
                    : call.callerIdNum

                  return (
                    <tr key={call.id} className="hover:bg-gray-50 dark:hover:bg-navy-700/30 transition-colors">
                      {/* Fecha */}
                      <td className="py-3 px-3 whitespace-nowrap">
                        <p className="text-xs text-gray-900 dark:text-white font-medium">
                          {formatDateTime(call.createdAt)}
                        </p>
                      </td>

                      {/* Agente */}
                      <td className="py-3 px-3">
                        <p className="text-xs font-medium text-gray-900 dark:text-white">
                          {call.agentName}
                        </p>
                        <p className="text-xs text-gray-400 font-mono">{call.agentExtension}</p>
                      </td>

                      {/* Número contacto */}
                      <td className="py-3 px-3">
                        {contactPhone
                          ? <PhoneLink phone={contactPhone} className="text-xs" />
                          : <span className="text-gray-300 text-xs">—</span>
                        }
                      </td>

                      {/* Flujo */}
                      <td className="py-3 px-3">
                        <span className={clsx(
                          'text-xs px-2 py-0.5 rounded-full font-medium',
                          call.callFlow === 'out'
                            ? 'bg-blue-50 text-blue-700 dark:bg-blue-900/20 dark:text-blue-400'
                            : 'bg-green-50 text-green-700 dark:bg-green-900/20 dark:text-green-400'
                        )}>
                          {call.callFlow === 'out' ? '↑ Saliente' : '↓ Entrante'}
                        </span>
                      </td>

                      {/* Estado */}
                      <td className="py-3 px-3">
                        <span className={clsx(
                          'inline-flex items-center gap-1 text-xs px-2 py-0.5 rounded-full font-medium',
                          cfg.bg, cfg.color
                        )}>
                          <Icon className="w-3 h-3" />
                          {cfg.label}
                        </span>
                      </td>

                      {/* Tipificación */}
                      <td className="py-3 px-3">
                        {call.typificationResult ? (
                          <div>
                            <p className="text-xs font-medium text-gray-900 dark:text-white">
                              {RESULT_LABELS[call.typificationResult] ?? call.typificationResult}
                            </p>
                            {call.typificationNotes && (
                              <p className="text-xs text-gray-400 truncate max-w-[150px]">
                                {call.typificationNotes}
                              </p>
                            )}
                          </div>
                        ) : (
                          <span className="text-xs text-gray-300 dark:text-gray-600 italic">
                            Sin tipificar
                          </span>
                        )}
                      </td>

                      {/* Lead */}
                      <td className="py-3 px-3">
                        {call.leadContactName ? (
                          <a href={`/leads/${call.leadId}`}
                            className="text-xs text-primary-700 dark:text-primary-400 hover:underline font-medium">
                            {call.leadContactName}
                          </a>
                        ) : (
                          <span className="text-xs text-gray-300 dark:text-gray-600">—</span>
                        )}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>

          {/* Paginación */}
          <div className="flex items-center justify-between px-4 py-3
                          border-t border-gray-100 dark:border-gray-700
                          bg-gray-50 dark:bg-navy-700/30 flex-wrap gap-3">
            <div className="flex items-center gap-3 text-xs text-gray-500 dark:text-gray-400">
              <span>
                {data && data.totalElements > 0
                  ? `${page * pageSize + 1}–${Math.min((page + 1) * pageSize, data.totalElements)} de ${data.totalElements.toLocaleString()}`
                  : '0 resultados'}
              </span>
              <div className="flex items-center gap-1.5">
                <span>Mostrar</span>
                <select className="border border-gray-200 dark:border-gray-700 rounded px-1.5 py-0.5
                                   bg-white dark:bg-navy-700 text-xs"
                  value={pageSize} onChange={e => { setPageSize(Number(e.target.value)); setPage(0) }}>
                  {PAGE_SIZES.map(s => <option key={s} value={s}>{s}</option>)}
                </select>
              </div>
            </div>

            <div className="flex items-center gap-1">
              <button onClick={() => setPage(0)} disabled={page === 0}
                className="px-2 py-1 rounded text-xs text-gray-500 hover:bg-gray-200
                           dark:hover:bg-navy-600 disabled:opacity-40 transition-colors">«</button>
              <button onClick={() => setPage(p => Math.max(0, p - 1))} disabled={page === 0}
                className="p-1 rounded text-gray-500 hover:bg-gray-200
                           dark:hover:bg-navy-600 disabled:opacity-40 transition-colors">
                <ChevronLeft className="w-4 h-4" />
              </button>
              <span className="text-xs text-gray-500 px-2">
                {page + 1} / {totalPages}
              </span>
              <button onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))}
                disabled={page >= totalPages - 1}
                className="p-1 rounded text-gray-500 hover:bg-gray-200
                           dark:hover:bg-navy-600 disabled:opacity-40 transition-colors">
                <ChevronRight className="w-4 h-4" />
              </button>
              <button onClick={() => setPage(totalPages - 1)}
                disabled={page >= totalPages - 1}
                className="px-2 py-1 rounded text-xs text-gray-500 hover:bg-gray-200
                           dark:hover:bg-navy-600 disabled:opacity-40 transition-colors">»</button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
