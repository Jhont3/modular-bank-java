import { mount } from './mount'

// Modo standalone (desarrollo del equipo de transfers, sin shell).
const root = document.getElementById('root')
if (root) {
  mount(root)
}
