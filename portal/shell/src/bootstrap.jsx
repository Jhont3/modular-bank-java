import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App'

// Sesión bancaria compartida entre Shell y MFEs (criterio del punto extra:
// "el token JWT se comparte sin exponer credenciales"). El token vive SOLO en
// memoria (nunca localStorage/cookies legibles): un refresh exige re-login,
// pero un XSS no encuentra credenciales persistidas. Contrato mínimo:
// { token, email, subscribe(fn), update(data), clear() }
window.__FINBANK_SESSION__ = window.__FINBANK_SESSION__ ?? {
  token: null,
  email: null,
  listeners: [],
  subscribe(fn) {
    this.listeners.push(fn)
    return () => {
      this.listeners = this.listeners.filter(l => l !== fn)
    }
  },
  update(data) {
    Object.assign(this, data)
    this.listeners.forEach(fn => fn(this))
  },
  clear() {
    this.update({ token: null, email: null })
  },
}

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
)
