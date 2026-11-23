import { FormEvent, useEffect, useState } from 'react'
import './AdminSettings.css'

type Sales = { ordersCount: number; merchandiseTotal: number; shippingTotal: number; total: number }
type BestSeller = { bookId: string; title: string; isbn13: string; quantitySold: number; revenue: number }
type LowStock = { bookId: string; title: string; isbn13: string; availableQuantity: number }
type Props = { apiUrl: string }
function money(value: number) { return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(value) }
function today() { return new Date().toISOString().slice(0, 10) }
function monthStart() { const date = new Date(); return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-01` }

export function AdminReports({ apiUrl }: Props) {
  const [roles, setRoles] = useState<string[]>([]); const [from, setFrom] = useState(monthStart()); const [to, setTo] = useState(today()); const [threshold, setThreshold] = useState('5')
  const [sales, setSales] = useState<Sales | null>(null); const [bestSellers, setBestSellers] = useState<BestSeller[]>([]); const [lowStock, setLowStock] = useState<LowStock[]>([]); const [notice, setNotice] = useState('')
  const financial = roles.includes('ROLE_FINANCE') || roles.includes('ROLE_GENERAL_ADMINISTRATOR'); const catalog = roles.includes('ROLE_CATALOG_MANAGER') || roles.includes('ROLE_GENERAL_ADMINISTRATOR')
  useEffect(() => { fetch(`${apiUrl}/api/v1/auth/session`, { credentials: 'include' }).then(response => response.ok ? response.json() : null).then(session => setRoles(session?.roles ?? [])) }, [apiUrl])
  async function loadFinancial() { const query = new URLSearchParams({ from, to }); const [salesResponse, bestResponse] = await Promise.all([fetch(`${apiUrl}/api/v1/admin/reports/sales?${query}`, { credentials: 'include' }), fetch(`${apiUrl}/api/v1/admin/reports/best-sellers?${query}`, { credentials: 'include' })]); if (!salesResponse.ok || !bestResponse.ok) { setNotice('Não foi possível carregar os relatórios financeiros.'); return }; setSales(await salesResponse.json()); setBestSellers(await bestResponse.json()); setNotice('') }
  async function loadStock() { const response = await fetch(`${apiUrl}/api/v1/admin/reports/low-stock?threshold=${encodeURIComponent(threshold)}`, { credentials: 'include' }); if (!response.ok) { setNotice('Não foi possível carregar o estoque baixo.'); return }; setLowStock(await response.json()); setNotice('') }
  useEffect(() => { if (financial) loadFinancial() }, [financial])
  useEffect(() => { if (catalog) loadStock() }, [catalog])
  function period(event: FormEvent) { event.preventDefault(); if (financial) loadFinancial() }
  function stock(event: FormEvent) { event.preventDefault(); if (catalog) loadStock() }
  if (!financial && !catalog) return null
  return <section className="admin-panel" aria-label="Relatórios"><h2>Relatórios</h2>{notice && <p role="alert">{notice}</p>}
    {financial && <section><h3>Vendas e mais vendidos</h3><form className="admin-form" onSubmit={period}><label>De<input type="date" required value={from} onChange={event => setFrom(event.target.value)} /></label><label>Até<input type="date" required value={to} onChange={event => setTo(event.target.value)} /></label><button type="submit">Atualizar período</button></form>{sales && <div><p><strong>{sales.ordersCount}</strong> pedido(s) confirmado(s) · Mercadorias: <strong>{money(sales.merchandiseTotal)}</strong> · Frete: <strong>{money(sales.shippingTotal)}</strong> · Total: <strong>{money(sales.total)}</strong></p></div>}<h4>Mais vendidos</h4>{bestSellers.length === 0 ? <p>Nenhuma venda no período.</p> : <ol>{bestSellers.map(book => <li key={`${book.bookId}-${book.isbn13}`}><strong>{book.title}</strong><span>ISBN {book.isbn13} · {book.quantitySold} unidade(s) · {money(book.revenue)}</span></li>)}</ol>}</section>}
    {catalog && <section><h3>Estoque baixo</h3><form className="admin-form" onSubmit={stock}><label>Quantidade máxima<input type="number" min="0" max="10000" required value={threshold} onChange={event => setThreshold(event.target.value)} /></label><button type="submit">Atualizar estoque</button></form>{lowStock.length === 0 ? <p>Nenhum livro dentro desse limite.</p> : <ul>{lowStock.map(book => <li key={book.bookId}><strong>{book.title}</strong><span>ISBN {book.isbn13} · {book.availableQuantity} unidade(s)</span></li>)}</ul>}</section>}
  </section>
}
