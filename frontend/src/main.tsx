import { FormEvent, StrictMode, useState } from 'react'
import { createRoot } from 'react-dom/client'
import './styles.css'

const API_URL = import.meta.env.VITE_API_URL ?? 'http://localhost:8080'
type Notice = { kind: 'success' | 'error'; text: string } | null

async function csrfToken() {
  const response = await fetch(`${API_URL}/api/v1/auth/csrf`, { credentials: 'include' })
  if (!response.ok) throw new Error('Não foi possível iniciar a sessão segura.')
  return (await response.json()).token as string
}

function App() {
  const [mode, setMode] = useState<'login' | 'register'>('login')
  const [notice, setNotice] = useState<Notice>(null)
  const [loading, setLoading] = useState(false)
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setLoading(true); setNotice(null)
    const form = new FormData(event.currentTarget)
    try {
      const csrf = await csrfToken()
      const payload = mode === 'login' ? { email: form.get('email'), password: form.get('password') }
        : { name: form.get('name'), email: form.get('email'), password: form.get('password') }
      const response = await fetch(`${API_URL}/api/v1/auth/${mode === 'login' ? 'login' : 'register'}`, {
        method: 'POST', credentials: 'include', headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': csrf }, body: JSON.stringify(payload),
      })
      const body = await response.json()
      if (!response.ok) throw new Error(body.message ?? 'Não foi possível concluir a solicitação.')
      setNotice({ kind: 'success', text: mode === 'login' ? `Sessão iniciada para ${body.email}.` : 'Cadastro realizado. Agora faça login.' })
      if (mode === 'register') setMode('login')
    } catch (error) { setNotice({ kind: 'error', text: error instanceof Error ? error.message : 'Ocorreu um erro inesperado.' })
    } finally { setLoading(false) }
  }
  return <main className="access-page"><section className="access-card" aria-labelledby="access-title">
    <p className="eyebrow">Livraria Online</p><h1 id="access-title">{mode === 'login' ? 'Acesse sua conta' : 'Crie sua conta'}</h1>
    <p className="subtitle">Use seu e-mail e uma senha com pelo menos 12 caracteres.</p>
    {notice && <p role="alert" className={`notice ${notice.kind}`}>{notice.text}</p>}
    <form onSubmit={submit}>
      {mode === 'register' && <label>Nome<input name="name" autoComplete="name" required maxLength={120} /></label>}
      <label>E-mail<input name="email" type="email" autoComplete="email" required /></label>
      <label>Senha<input name="password" type="password" autoComplete={mode === 'login' ? 'current-password' : 'new-password'} minLength={12} required /></label>
      <button disabled={loading} type="submit">{loading ? 'Aguarde…' : mode === 'login' ? 'Entrar' : 'Cadastrar'}</button>
    </form>
    <button className="link-button" type="button" onClick={() => { setMode(mode === 'login' ? 'register' : 'login'); setNotice(null) }}>
      {mode === 'login' ? 'Ainda não tenho uma conta' : 'Já tenho uma conta'}
    </button>
  </section></main>
}
createRoot(document.getElementById('root')!).render(<StrictMode><App /></StrictMode>)
