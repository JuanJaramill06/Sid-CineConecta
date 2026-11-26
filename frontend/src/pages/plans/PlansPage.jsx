import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ApiError } from '../../api/client.js'
import { useApiClient } from '../../api/useApiClient.js'
import { useAuth } from '../../context/AuthContext.jsx'

const currency = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 })

export default function PlansPage() {
  const api = useApiClient()
  const { isClient } = useAuth()
  const navigate = useNavigate()

  const [plans, setPlans] = useState([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState(null)

  const [contractingPlanId, setContractingPlanId] = useState(null)
  const [approvePayment, setApprovePayment] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState(null)
  const [success, setSuccess] = useState(null)

  useEffect(() => {
    let active = true
    api
      .get('/api/plans')
      .then((data) => active && setPlans(data))
      .catch((err) => active && setLoadError(err instanceof ApiError ? err.message : 'No se pudieron cargar los planes'))
      .finally(() => active && setLoading(false))
    return () => {
      active = false
    }
  }, [api])

  async function handleContract(planId) {
    setSubmitting(true)
    setFormError(null)
    try {
      // RF18: approvePayment simulates the payment gateway; rejected stores nothing.
      const subscription = await api.post('/api/subscriptions', { planId, approvePayment })
      setSuccess(`Suscripción activa: ${subscription.planName} (vence ${subscription.endsAt.slice(0, 10)}).`)
      setContractingPlanId(null)
    } catch (err) {
      setFormError(err instanceof ApiError ? err.message : 'No se pudo contratar el plan')
    } finally {
      setSubmitting(false)
    }
  }

  if (loading) return <p>Cargando planes…</p>
  if (loadError) return <p className="error-text">{loadError}</p>

  return (
    <section>
      <h1>Planes</h1>
      {success && <p className="card">{success}</p>}
      <div className="grid">
        {plans.map((plan) => (
          <article key={plan.planId} className="card">
            <h2>{plan.name}</h2>
            <p>{currency.format(plan.price)} / mes</p>
            <p>Hasta {plan.maxProfiles} perfiles</p>

            {!isClient && (
              <button type="button" className="button secondary" onClick={() => navigate('/login')}>
                Inicia sesión para contratar
              </button>
            )}

            {isClient && contractingPlanId !== plan.planId && (
              <button type="button" className="button" onClick={() => setContractingPlanId(plan.planId)}>
                Contratar
              </button>
            )}

            {isClient && contractingPlanId === plan.planId && (
              <div className="form">
                <label>
                  <input
                    type="checkbox"
                    checked={approvePayment}
                    onChange={(event) => setApprovePayment(event.target.checked)}
                  />
                  Simular pago aprobado
                </label>
                {formError && <p className="error-text">{formError}</p>}
                <button
                  type="button"
                  className="button"
                  disabled={submitting}
                  onClick={() => handleContract(plan.planId)}
                >
                  {submitting ? 'Procesando…' : 'Confirmar'}
                </button>
                <button
                  type="button"
                  className="button secondary"
                  disabled={submitting}
                  onClick={() => setContractingPlanId(null)}
                >
                  Cancelar
                </button>
              </div>
            )}
          </article>
        ))}
      </div>
    </section>
  )
}
