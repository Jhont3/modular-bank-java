import React, { Suspense, useEffect, useState } from 'react'
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import Navbar from './components/Navbar'
import Login from './components/Login'
import TransfersWrapper from './remotes/TransfersWrapper'

// React.lazy + import(): el código del MFE se descarga en runtime vía
// remoteEntry.js — el shell no se recompila cuando un MFE cambia.
const NotificationsApp = React.lazy(() => import('notifications/NotificationsApp'))

const Loading = () => (
  <div style={{ padding: '2rem', textAlign: 'center', color: '#999' }}>
    Cargando microfrontend...
  </div>
)

// Si un MFE está caído, el shell y el otro MFE siguen funcionando
// (criterio: despliegue independiente sin afectar al Shell).
class MfeBoundary extends React.Component {
  constructor(props) {
    super(props)
    this.state = { failed: false }
  }
  static getDerivedStateFromError() {
    return { failed: true }
  }
  render() {
    if (this.state.failed) {
      return (
        <div style={{ padding: '2rem', background: '#fff5f5', border: '1px solid #feb2b2', borderRadius: 8 }}>
          <strong>El microfrontend "{this.props.name}" no está disponible.</strong>
          <p>El resto del portal sigue operativo — modo degradado.</p>
        </div>
      )
    }
    return this.props.children
  }
}

export default function App() {
  const [session, setSession] = useState({ token: window.__FINBANK_SESSION__.token })

  useEffect(() => window.__FINBANK_SESSION__.subscribe(s => setSession({ token: s.token })), [])

  if (!session.token) {
    return <Login />
  }

  return (
    <BrowserRouter>
      <Navbar />
      <main style={{ padding: '2rem', maxWidth: 960, margin: '0 auto', fontFamily: 'system-ui, sans-serif' }}>
        <Suspense fallback={<Loading />}>
          <Routes>
            <Route path="/" element={<Navigate to="/transfers" replace />} />
            <Route
              path="/notifications/*"
              element={
                <MfeBoundary name="notifications">
                  <NotificationsApp />
                </MfeBoundary>
              }
            />
            <Route
              path="/transfers/*"
              element={
                <MfeBoundary name="transfers">
                  <TransfersWrapper />
                </MfeBoundary>
              }
            />
          </Routes>
        </Suspense>
      </main>
    </BrowserRouter>
  )
}
