<template>
  <div class="transfers">
    <h2>💸 Transferencias</h2>

    <div class="grid">
      <!-- Formulario -->
      <form class="card" @submit.prevent="submit">
        <h3>Nueva transferencia</h3>
        <label>Cuenta origen</label>
        <select v-model="form.sourceAccountId" required>
          <option v-for="a in accounts" :key="a.id" :value="a.id">
            …{{ a.id.slice(-12) }} — ${{ a.balance }}
          </option>
        </select>
        <label>Cuenta destino</label>
        <input v-model="form.targetAccountId" placeholder="UUID de la cuenta destino" required />
        <label>Monto</label>
        <input v-model.number="form.amount" type="number" min="0.01" step="0.01" required />
        <label>Referencia</label>
        <input v-model="form.reference" placeholder="opcional" />
        <button :disabled="busy">{{ busy ? 'Procesando…' : 'Transferir' }}</button>
        <p v-if="error" class="error">{{ error }}</p>
        <p class="hint">
          <a href="#" @click.prevent="createAccount">+ crear otra cuenta</a>
        </p>
      </form>

      <!-- Stream en tiempo real (SSE) -->
      <div class="card">
        <h3>
          Estado en tiempo real
          <span class="dot" :class="{ on: sseConnected }"></span>
          <small>{{ sseConnected ? 'SSE conectado' : 'reconectando…' }}</small>
        </h3>
        <p v-if="liveEvents.length === 0" class="hint">
          Las transiciones de la saga (PENDING → COMPLETED / FAILED / COMPENSATED)
          aparecen aquí al instante vía Server-Sent Events.
        </p>
        <div v-for="(e, i) in liveEvents" :key="i" class="event" :class="e.status.toLowerCase()">
          <strong>{{ e.status }}</strong> — ${{ e.amount }}
          <span v-if="e.failureReason"> · {{ e.failureReason }}</span>
          <small> {{ e.at }}</small>
        </div>
      </div>
    </div>

    <!-- Historial -->
    <div class="card" v-if="form.sourceAccountId">
      <h3>Historial <button class="link" @click="loadHistory">refrescar</button></h3>
      <table v-if="history.length">
        <thead><tr><th>Estado</th><th>Monto</th><th>Destino</th><th>Fecha</th></tr></thead>
        <tbody>
          <tr v-for="t in history" :key="t.id">
            <td><span class="badge" :class="t.status.toLowerCase()">{{ t.status }}</span></td>
            <td>${{ t.amount }}</td>
            <td>…{{ t.targetAccountId.slice(-12) }}</td>
            <td>{{ new Date(t.createdAt).toLocaleString() }}</td>
          </tr>
        </tbody>
      </table>
      <p v-else class="hint">Sin transferencias en esta cuenta.</p>
    </div>
  </div>
</template>

<script>
const API = '/api'

// MFE del dominio transfers (Vue 3): consume ms-transfers vía gateway.
// Tiempo real: EventSource sobre GET /transfers/stream (criterio del punto extra).
export default {
  name: 'TransfersApp',
  data() {
    return {
      accounts: [],
      form: { sourceAccountId: '', targetAccountId: '', amount: null, reference: '' },
      history: [],
      liveEvents: [],
      error: null,
      busy: false,
      sseConnected: false,
      es: null,
    }
  },
  computed: {
    token() {
      return window.__FINBANK_SESSION__?.token
    },
  },
  async mounted() {
    await this.loadAccounts()
    this.connectSse()
  },
  beforeUnmount() {
    if (this.es) this.es.close()
  },
  methods: {
    authHeaders() {
      return { Authorization: `Bearer ${this.token}`, 'Content-Type': 'application/json' }
    },
    async loadAccounts() {
      const res = await fetch(`${API}/accounts`, { headers: this.authHeaders() })
      if (res.ok) {
        this.accounts = await res.json()
        if (!this.form.sourceAccountId && this.accounts.length) {
          this.form.sourceAccountId = this.accounts[0].id
          this.loadHistory()
        }
      }
    },
    async createAccount() {
      await fetch(`${API}/accounts`, { method: 'POST', headers: this.authHeaders() })
      await this.loadAccounts()
    },
    async loadHistory() {
      if (!this.form.sourceAccountId) return
      const res = await fetch(`${API}/transfers?accountId=${this.form.sourceAccountId}`, { headers: this.authHeaders() })
      if (res.ok) this.history = await res.json()
    },
    async submit() {
      this.busy = true
      this.error = null
      try {
        const res = await fetch(`${API}/transfers`, {
          method: 'POST',
          headers: this.authHeaders(),
          body: JSON.stringify(this.form),
        })
        const data = await res.json()
        if (!res.ok) throw new Error(data.message || `Error ${res.status}`)
        this.form.amount = null
        await Promise.all([this.loadHistory(), this.loadAccounts()])
      } catch (err) {
        this.error = err.message
      } finally {
        this.busy = false
      }
    },
    connectSse() {
      // EventSource no permite headers: el JWT viaja como query param
      // (aceptado solo por este endpoint; implicaciones en ADR-012).
      this.es = new EventSource(`${API}/transfers/stream?access_token=${this.token}`)
      this.es.onopen = () => { this.sseConnected = true }
      this.es.onerror = () => { this.sseConnected = false } // EventSource reconecta solo
      this.es.addEventListener('transfer-status', evt => {
        const data = JSON.parse(evt.data)
        this.liveEvents.unshift({ ...data, at: new Date().toLocaleTimeString() })
        if (this.liveEvents.length > 12) this.liveEvents.pop()
        this.loadHistory()
        this.loadAccounts()
        // Comunicación MFE → MFE/Shell sin imports (mismo patrón del ejemplo
        // de referencia): notifications refresca y el navbar muestra el estado.
        window.dispatchEvent(new CustomEvent('finbank:transfer-update', { detail: data }))
      })
    },
  },
}
</script>

<style scoped>
.transfers { font-family: system-ui, sans-serif; color: #1a2332; }
.grid { display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; }
.card { background: #fff; border: 1px solid #e2e8f0; border-radius: 10px; padding: 1.2rem; margin-bottom: 1rem; }
label { display: block; font-size: 13px; color: #4a5568; margin-top: 10px; }
input, select { width: 100%; padding: 8px; border: 1px solid #cbd5e0; border-radius: 6px; box-sizing: border-box; margin-top: 4px; }
button { margin-top: 14px; padding: 10px 18px; background: #2b6cb0; color: #fff; border: none; border-radius: 6px; cursor: pointer; }
button.link { background: none; color: #2b6cb0; padding: 0; margin: 0; font-size: 13px; }
.error { color: #c53030; }
.hint { color: #718096; font-size: 14px; }
.dot { display: inline-block; width: 10px; height: 10px; border-radius: 50%; background: #c53030; margin: 0 6px; }
.dot.on { background: #2f855a; }
.event { border-left: 4px solid #718096; padding: 6px 10px; margin-bottom: 6px; background: #f7fafc; border-radius: 4px; }
.event.completed { border-color: #2f855a; }
.event.failed { border-color: #c53030; }
.event.pending { border-color: #975a16; }
.event.compensated { border-color: #6b46c1; }
table { width: 100%; border-collapse: collapse; font-size: 14px; }
th, td { text-align: left; padding: 6px 8px; border-bottom: 1px solid #e2e8f0; }
.badge { padding: 2px 8px; border-radius: 10px; font-size: 12px; color: #fff; background: #718096; }
.badge.completed { background: #2f855a; }
.badge.failed { background: #c53030; }
.badge.pending { background: #975a16; }
.badge.compensated { background: #6b46c1; }
</style>
