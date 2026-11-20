import { useEffect, useState } from 'react'

type OperationalOrder = { id: string; status: string; total: number; createdAt: string; itemCount: number }
type Refund = { attemptId: string; orderId: string; method: string; status: string; orderTotal: number; createdAt: string }
type Props = { apiUrl: string; requestCsrf: () => Promise<void>; csrf: () => string; onNotice: (notice: string) => void }
function money(value: number) { return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(value) }

export function AdminOperations({ apiUrl, requestCsrf, csrf, onNotice }: Props) {
  const [roles, setRoles] = useState<string[]>([])
  const canOperateOrders = roles.includes('ROLE_SUPPORT') || roles.includes('ROLE_GENERAL_ADMINISTRATOR')
  const canOperateRefunds = roles.includes('ROLE_FINANCE') || roles.includes('ROLE_GENERAL_ADMINISTRATOR')
  const [orders, setOrders] = useState<OperationalOrder[]>([])
  const [refunds, setRefunds] = useState<Refund[]>([])
  const [justifications, setJustifications] = useState<Record<string, string>>({})
  const [notice, setNotice] = useState('')
  useEffect(() => {
    const refreshSession = () => fetch(`${apiUrl}/api/v1/auth/session`, { credentials: 'include' }).then(response => response.ok ? response.json() : null).then(session => setRoles(session?.roles ?? []))
    refreshSession()
    const timer = window.setInterval(refreshSession, 5000)
    return () => window.clearInterval(timer)
  }, [apiUrl])
  async function loadOrders() { const response = await fetch(`${apiUrl}/api/v1/admin/orders/operations`, { credentials: 'include' }); if (response.ok) setOrders(await response.json()) }
  async function loadRefunds() { const response = await fetch(`${apiUrl}/api/v1/admin/finance/refunds`, { credentials: 'include' }); if (response.ok) setRefunds(await response.json()) }
  useEffect(() => { if (canOperateOrders) loadOrders(); if (canOperateRefunds) loadRefunds() }, [canOperateOrders, canOperateRefunds])
  async function retry(refund: Refund) {
    const justification = justifications[refund.attemptId]?.trim()
    if (!justification) { setNotice('Informe a justificativa da intervenção financeira.'); onNotice('Informe a justificativa da intervenção financeira.'); return }
    await requestCsrf()
    const response = await fetch(`${apiUrl}/api/v1/admin/finance/refunds/${refund.attemptId}/retry`, { method: 'POST', credentials: 'include', headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': csrf() }, body: JSON.stringify({ justification }) })
    if (!response.ok) { setNotice('Não foi possível reprocessar o estorno.'); onNotice('Não foi possível reprocessar o estorno.'); return }
    setNotice('Estorno reenviado para processamento e intervenção auditada.')
    onNotice('Estorno reenviado para processamento e intervenção auditada.')
    await loadRefunds()
  }
  if (!canOperateOrders && !canOperateRefunds) return null
  return <section className="admin-panel" aria-label="Operação administrativa"><h2>Operação administrativa</h2>{notice && <p role="status">{notice}</p>}
    {canOperateOrders && <section><h3>Pedidos operacionais</h3>{orders.length === 0 ? <p>Nenhum pedido para exibir.</p> : <ul>{orders.map(order => <li key={order.id}><strong>Pedido {order.id.slice(0, 8)}</strong><span>{order.status} · {order.itemCount} item(ns) · {money(order.total)}</span></li>)}</ul>}</section>}
    {canOperateRefunds && <section><h3>Pendências de estorno</h3>{refunds.length === 0 ? <p>Nenhuma pendência de estorno.</p> : <ul>{refunds.map(refund => <li key={refund.attemptId}><div><strong>Pedido {refund.orderId.slice(0, 8)}</strong><span>{refund.status} · {refund.method} · {money(refund.orderTotal)}</span></div>{refund.status === 'REFUND_FAILED' && <div className="refund-retry"><label>Justificativa<input maxLength={300} value={justifications[refund.attemptId] ?? ''} onChange={event => setJustifications(current => ({ ...current, [refund.attemptId]: event.target.value }))} /></label><button onClick={() => retry(refund)}>Reprocessar</button></div>}</li>)}</ul>}</section>}
  </section>
}
