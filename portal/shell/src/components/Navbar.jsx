import React, { useEffect, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'

export default function Navbar() {
  const location = useLocation()
  const [email, setEmail] = useState(window.__FINBANK_SESSION__.email)
  const [lastEvent, setLastEvent] = useState(null)

  useEffect(() => {
    const unsub = window.__FINBANK_SESSION__.subscribe(s => setEmail(s.email))
    // Comunicación MFE → Shell sin imports: el MFE de transfers emite un
    // CustomEvent por cada actualización SSE y el navbar muestra el último estado.
    const onUpdate = e => setLastEvent(e.detail)
    window.addEventListener('finbank:transfer-update', onUpdate)
    return () => {
      unsub()
      window.removeEventListener('finbank:transfer-update', onUpdate)
    }
  }, [])

  const linkStyle = path => ({
    color: location.pathname.startsWith(path) ? '#90cdf4' : '#fff',
    textDecoration: 'none',
    marginRight: '1.5rem',
    fontWeight: location.pathname.startsWith(path) ? 700 : 400,
  })

  return (
    <nav style={{ background: '#1a2332', color: '#fff', padding: '1rem 2rem', display: 'flex', alignItems: 'center', fontFamily: 'system-ui, sans-serif' }}>
      <span style={{ fontWeight: 700, marginRight: '2rem' }}>🏦 FinBank</span>
      <Link to="/transfers" style={linkStyle('/transfers')}>Transferencias</Link>
      <Link to="/notifications" style={linkStyle('/notifications')}>Notificaciones</Link>
      <span style={{ marginLeft: 'auto', display: 'flex', alignItems: 'center', gap: '1rem' }}>
        {lastEvent && (
          <span style={{ fontSize: 12, background: lastEvent.status === 'COMPLETED' ? '#2f855a' : '#975a16', padding: '4px 10px', borderRadius: 12 }}>
            ⚡ {lastEvent.status} · ${lastEvent.amount}
          </span>
        )}
        <span style={{ color: '#a0aec0', fontSize: 14 }}>{email}</span>
        <button onClick={() => window.__FINBANK_SESSION__.clear()} style={{ background: 'transparent', color: '#90cdf4', border: '1px solid #90cdf4', borderRadius: 6, padding: '4px 12px', cursor: 'pointer' }}>
          Salir
        </button>
      </span>
    </nav>
  )
}
