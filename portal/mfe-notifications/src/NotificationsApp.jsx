import React, { useCallback, useEffect, useState } from 'react'

const API = '/api'

const LABELS = {
  TRANSFER_SENT: { text: 'Transferencia enviada', color: '#2f855a', icon: '✅' },
  TRANSFER_FAILED: { text: 'Transferencia rechazada', color: '#c53030', icon: '❌' },
  TRANSFER_COMPENSATED: { text: 'Transferencia reversada', color: '#975a16', icon: '↩️' },
}

// MFE del dominio notifications: consume el microservicio ms-notifications a
// través del gateway. El token se lee de la sesión compartida del shell.
export default function NotificationsApp() {
  const [items, setItems] = useState([])
  const [error, setError] = useState(null)
  const [updatedAt, setUpdatedAt] = useState(null)

  const load = useCallback(async () => {
    const token = window.__FINBANK_SESSION__?.token
    if (!token) return
    try {
      const res = await fetch(`${API}/notifications`, {
        headers: { Authorization: `Bearer ${token}` },
      })
      if (!res.ok) throw new Error(`Error ${res.status}`)
      setItems(await res.json())
      setError(null)
      setUpdatedAt(new Date())
    } catch (err) {
      setError(err.message)
    }
  }, [])

  useEffect(() => {
    load()
    // Comunicación MFE → MFE sin imports: cuando el MFE de transfers (Vue)
    // recibe un evento SSE, emite 'finbank:transfer-update'; este MFE refresca
    // dejando tiempo a que el evento viaje por Kafka hasta ms-notifications.
    const onTransferUpdate = () => setTimeout(load, 3000)
    window.addEventListener('finbank:transfer-update', onTransferUpdate)
    const poll = setInterval(load, 15000)
    return () => {
      window.removeEventListener('finbank:transfer-update', onTransferUpdate)
      clearInterval(poll)
    }
  }, [load])

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'baseline', gap: '1rem' }}>
        <h2 style={{ color: '#1a2332' }}>🔔 Notificaciones</h2>
        <small style={{ color: '#a0aec0' }}>
          {updatedAt ? `actualizado ${updatedAt.toLocaleTimeString()}` : ''}
        </small>
        <button onClick={load} style={{ marginLeft: 'auto', border: '1px solid #cbd5e0', background: '#fff', borderRadius: 6, padding: '4px 12px', cursor: 'pointer' }}>
          Refrescar
        </button>
      </div>
      {error && <div style={{ color: '#c53030' }}>{error}</div>}
      {items.length === 0 && !error && <p style={{ color: '#718096' }}>Sin notificaciones todavía. Haz una transferencia.</p>}
      {items.map(n => {
        const meta = LABELS[n.type] || { text: n.type, color: '#4a5568', icon: '📩' }
        return (
          <div key={n.id} style={{ border: '1px solid #e2e8f0', borderLeft: `4px solid ${meta.color}`, borderRadius: 8, padding: '0.8rem 1rem', marginBottom: 10, background: '#fff' }}>
            <strong style={{ color: meta.color }}>{meta.icon} {meta.text}</strong>
            <div style={{ color: '#4a5568', fontSize: 14, marginTop: 4 }}>
              Monto: ${n.payload?.amount} · Destino: …{(n.payload?.targetAccountId || '').slice(-12)}
              {n.payload?.reason ? ` · Motivo: ${n.payload.reason}` : ''}
            </div>
            <small style={{ color: '#a0aec0' }}>{new Date(n.createdAt).toLocaleString()}</small>
          </div>
        )
      })}
    </div>
  )
}
