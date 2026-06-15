import { createApp } from 'vue'
import TransfersApp from './TransfersApp.vue'

// Contrato expuesto por Module Federation: el shell (React) monta/desmonta
// esta app Vue sin conocer su framework.
export function mount(el) {
  const app = createApp(TransfersApp)
  app.mount(el)
  return app
}

export function unmount(app) {
  app.unmount()
}
