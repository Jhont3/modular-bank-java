import React, { useRef, useEffect } from 'react'

// Bridge React → Vue: el MFE de transfers (Vue 3) expone mount/unmount y el
// shell lo monta en un div propio. Mismo patrón que el ejemplo de referencia.
export default function TransfersWrapper() {
  const containerRef = useRef(null)

  useEffect(() => {
    let cancelled = false
    let vueApp = null

    import('transfers/TransfersApp').then(({ mount }) => {
      if (!cancelled && containerRef.current) {
        vueApp = mount(containerRef.current)
      }
    })

    return () => {
      cancelled = true
      if (vueApp) vueApp.unmount()
    }
  }, [])

  return <div ref={containerRef} style={{ minHeight: '400px' }} />
}
