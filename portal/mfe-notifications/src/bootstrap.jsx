import React from 'react'
import ReactDOM from 'react-dom/client'
import NotificationsApp from './NotificationsApp'

// Modo standalone (desarrollo del equipo de notifications, sin shell).
const root = document.getElementById('root')
if (root) {
  ReactDOM.createRoot(root).render(<NotificationsApp />)
}
