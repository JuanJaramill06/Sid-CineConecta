import { useEffect, useState } from 'react'
import { Navigate } from 'react-router-dom'
import { ApiError } from '../../api/client.js'
import { useApiClient } from '../../api/useApiClient.js'
import { useAuth } from '../../context/AuthContext.jsx'

const currency = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 })

export default function SubscriptionPage() {
  const { isClient } = useAuth()
  const api = useApiClient()
  const [subscriptions, setSubscriptions] = useState([])
  const [payments, setPayments] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
    if (!isClient) return
    let active = true
    Promise.all([api.get('/api/subscriptions'), api.get('/api/payments')])
      .then(([subs, pays]) => {
        if (!active) return
        setSubscriptions(subs)
        setPayments(pays)
      })
      .catch((err) => active && setError(err instanceof ApiError ? err.message : 'No se pudo cargar la información'))
      .finally(() => active && setLoading(false))
    return () => {
      active = false
    }
  }, [api, isClient])

  if (!isClient) {
    return <Navigate to="/login" replace />
  }
  if (loading) return <p>Cargando…</p>
  if (error) return <p className="error-text">{error}</p>

  return (
    <section>
      <h1>Mi suscripción</h1>

      <h2>Historial de suscripciones</h2>
      {subscriptions.length === 0 ? (
        <p>
          Todavía no has contratado ningún plan. <a href="/plans">Ver planes</a>.
        </p>
      ) : (
        <table className="table">
          <thead>
            <tr>
              <th>Plan</th>
              <th>Inicio</th>
              <th>Fin</th>
              <th>Estado</th>
            </tr>
          </thead>
          <tbody>
            {subscriptions.map((subscription) => (
              <tr key={subscription.subscriptionId}>
                <td>{subscription.planName}</td>
                <td>{subscription.startsAt.slice(0, 10)}</td>
                <td>{subscription.endsAt.slice(0, 10)}</td>
                <td>{subscription.active ? 'Vigente' : 'Vencida'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      <h2>Pagos</h2>
      {payments.length === 0 ? (
        <p>No hay pagos registrados.</p>
      ) : (
        <table className="table">
          <thead>
            <tr>
              <th>Plan</th>
              <th>Fecha</th>
              <th>Monto</th>
              <th>Referencia</th>
            </tr>
          </thead>
          <tbody>
            {payments.map((payment) => (
              <tr key={payment.paymentId}>
                <td>{payment.planName}</td>
                <td>{payment.paidAt.slice(0, 10)}</td>
                <td>{currency.format(payment.amount)}</td>
                <td>{payment.reference}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  )
}
