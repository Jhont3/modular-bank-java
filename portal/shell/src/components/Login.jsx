import React, { useState } from 'react'

const API = '/api'

// Login/registro contra el monolito (módulo auth) a través del gateway.
// Solo el shell conoce el flujo de credenciales; los MFEs únicamente leen
// el token ya emitido desde window.__FINBANK_SESSION__.
export default function Login() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [name, setName] = useState('')
  const [mode, setMode] = useState('login')
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  const submit = async e => {
    e.preventDefault()
    setBusy(true)
    setError(null)
    const path = mode === 'login' ? '/auth/login' : '/auth/register'
    const body = mode === 'login' ? { email, password } : { email, password, name }
    try {
      const res = await fetch(`${API}${path}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      })
      const data = await res.json()
      if (!res.ok) {
        throw new Error(data.message || `Error ${res.status}`)
      }
      window.__FINBANK_SESSION__.update({ token: data.accessToken, email })
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  const input = { display: 'block', width: '100%', padding: 10, marginBottom: 12, border: '1px solid #cbd5e0', borderRadius: 6, boxSizing: 'border-box' }

  return (
    <div style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', background: '#1a2332', fontFamily: 'system-ui, sans-serif' }}>
      <form onSubmit={submit} style={{ background: '#fff', padding: '2.5rem', borderRadius: 12, width: 340 }}>
        <h1 style={{ marginTop: 0, color: '#1a2332' }}>🏦 FinBank</h1>
        <p style={{ color: '#718096' }}>{mode === 'login' ? 'Inicia sesión en tu portal' : 'Crea tu cuenta'}</p>
        {mode === 'register' && (
          <input style={input} placeholder="Nombre" value={name} onChange={e => setName(e.target.value)} required />
        )}
        <input style={input} type="email" placeholder="Email" value={email} onChange={e => setEmail(e.target.value)} required />
        <input style={input} type="password" placeholder="Contraseña" value={password} onChange={e => setPassword(e.target.value)} required />
        {error && <div style={{ color: '#c53030', marginBottom: 12 }}>{error}</div>}
        <button disabled={busy} style={{ width: '100%', padding: 12, background: '#2b6cb0', color: '#fff', border: 'none', borderRadius: 6, cursor: 'pointer', fontSize: 16 }}>
          {busy ? '...' : mode === 'login' ? 'Entrar' : 'Registrarme'}
        </button>
        <p style={{ textAlign: 'center', marginBottom: 0 }}>
          <a href="#" onClick={e => { e.preventDefault(); setMode(mode === 'login' ? 'register' : 'login'); setError(null) }} style={{ color: '#2b6cb0' }}>
            {mode === 'login' ? '¿Sin cuenta? Regístrate' : 'Ya tengo cuenta'}
          </a>
        </p>
      </form>
    </div>
  )
}
