import { useCallback, useEffect, useState } from 'react'
import { useApiClient } from '../../api/useApiClient.js'
import Icon from '../../components/Icon.jsx'
import Notice from '../../components/Notice.jsx'
import { TYPE_LABEL, currency, formatDate, isoDate, number } from '../../lib/format.js'

const DAY = 86_400_000

/**
 * RF66-RF71. Both reports share one date range (inclusive on both ends; the server turns it
 * into a half-open range). Each row has an inline magnitude bar; the numbers stay in text, so
 * the table is the accessible view and color never carries meaning on its own.
 */
export default function ReportsPage() {
  const api = useApiClient()
  const [range, setRange] = useState(() => ({ from: isoDate(Date.now() - 30 * DAY), to: isoDate(Date.now()) }))
  const [applied, setApplied] = useState(range)
  const [income, setIncome] = useState({ loading: true })
  const [consumption, setConsumption] = useState({ loading: true })
  const [downloadError, setDownloadError] = useState(null)

  const invalid = range.from > range.to

  useEffect(() => {
    let active = true
    setIncome((s) => ({ ...s, loading: true }))
    setConsumption((s) => ({ ...s, loading: true }))
    api
      .get('/api/admin/reports/income', { query: applied })
      .then((rows) => active && setIncome({ loading: false, rows }))
      .catch((error) => active && setIncome({ loading: false, error }))
    api
      .get('/api/admin/reports/consumption', { query: applied })
      .then((rows) => active && setConsumption({ loading: false, rows }))
      .catch((error) => active && setConsumption({ loading: false, error }))
    return () => {
      active = false
    }
  }, [api, applied])

  const download = useCallback(
    async (report) => {
      setDownloadError(null)
      try {
        const { blob, filename } = await api.download(`/api/admin/reports/${report}`, { query: { ...applied, format: 'csv' } })
        const url = URL.createObjectURL(blob)
        const link = document.createElement('a')
        link.href = url
        link.download = filename.replace('.csv', `_${applied.from}_${applied.to}.csv`)
        document.body.append(link)
        link.click()
        link.remove()
        URL.revokeObjectURL(url)
      } catch (error) {
        setDownloadError(error.message)
      }
    },
    [api, applied],
  )

  function setPreset(days) {
    const next = { from: isoDate(Date.now() - days * DAY), to: isoDate(Date.now()) }
    setRange(next)
    setApplied(next)
  }

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Informes</h1>
          <p>
            Del {formatDate(`${applied.from}T12:00`)} al {formatDate(`${applied.to}T12:00`)}, ambos días incluidos.
          </p>
        </div>
      </div>

      <form
        className="toolbar"
        onSubmit={(event) => {
          event.preventDefault()
          if (!invalid) setApplied(range)
        }}
      >
        <label className="field">
          <span className="hint">Desde</span>
          <input type="date" value={range.from} max={range.to} onChange={(e) => setRange((r) => ({ ...r, from: e.target.value }))} aria-invalid={invalid} />
        </label>
        <label className="field">
          <span className="hint">Hasta</span>
          <input type="date" value={range.to} min={range.from} onChange={(e) => setRange((r) => ({ ...r, to: e.target.value }))} aria-invalid={invalid} />
        </label>
        <button type="submit" className="button" disabled={invalid}>
          Consultar
        </button>
        <div className="segmented" role="group" aria-label="Rangos rápidos">
          {[
            [7, '7 días'],
            [30, '30 días'],
            [90, '90 días'],
            [365, '1 año'],
          ].map(([days, label]) => (
            <button
              key={days}
              type="button"
              aria-pressed={applied.to === isoDate(Date.now()) && applied.from === isoDate(Date.now() - days * DAY)}
              onClick={() => setPreset(days)}
            >
              {label}
            </button>
          ))}
        </div>
        {invalid && <p className="error-text">La fecha inicial debe ser anterior o igual a la final.</p>}
      </form>

      {downloadError && <Notice tone="error">{downloadError}</Notice>}

      <section className="section" aria-labelledby="income-title" style={{ marginTop: 'var(--s-6)' }}>
        <div className="section-head">
          <div>
            <h2 id="income-title">Ingresos por plan</h2>
            <p className="muted" style={{ margin: 0 }}>
              Pagos registrados en Oracle en el período: total recaudado y suscripciones adquiridas.
            </p>
          </div>
          <button type="button" className="button secondary small" onClick={() => download('income')} disabled={!income.rows}>
            <Icon name="download" /> Exportar CSV
          </button>
        </div>
        <ReportTable
          state={income}
          empty="No hubo pagos en este período."
          columns={[['Plan'], ['Suscripciones', 'num'], ['Total recaudado']]}
          rows={income.rows ?? []}
          measure={(row) => Number(row.totalIncome)}
          render={(row, bar) => (
            <>
              <td>{row.planName}</td>
              <td className="num">{number.format(row.subscriptionsCount)}</td>
              <td>{bar(currency.format(row.totalIncome))}</td>
            </>
          )}
          footer={(rows) => (
            <>
              <td>Total</td>
              <td className="num">{number.format(rows.reduce((sum, r) => sum + r.subscriptionsCount, 0))}</td>
              <td className="num">{currency.format(rows.reduce((sum, r) => sum + Number(r.totalIncome), 0))}</td>
            </>
          )}
        />
      </section>

      <section className="section" aria-labelledby="consumption-title">
        <div className="section-head">
          <div>
            <h2 id="consumption-title">Consumo por plan y tipo de contenido</h2>
            <p className="muted" style={{ margin: 0 }}>
              Sesiones de MongoDB iniciadas en el período, con el plan vigente al iniciar cada una; las series cuentan por
              episodio. Los nombres de plan vienen de Oracle.
            </p>
          </div>
          <button type="button" className="button secondary small" onClick={() => download('consumption')} disabled={!consumption.rows}>
            <Icon name="download" /> Exportar CSV
          </button>
        </div>
        <ReportTable
          state={consumption}
          empty="No hubo reproducciones en este período."
          columns={[['Plan'], ['Tipo'], ['Minutos vistos'], ['Perfiles distintos', 'num']]}
          rows={consumption.rows ?? []}
          measure={(row) => row.minutesWatched}
          render={(row, bar) => (
            <>
              <td>{row.planName ?? `Plan ${row.planId}`}</td>
              <td>{TYPE_LABEL[row.contentType]}</td>
              <td>{bar(`${number.format(row.minutesWatched)} min`)}</td>
              <td className="num">{number.format(row.distinctProfiles)}</td>
            </>
          )}
          footer={(rows) => (
            <>
              <td colSpan={2}>Total</td>
              <td className="num">{number.format(rows.reduce((sum, r) => sum + r.minutesWatched, 0))} min</td>
              <td className="num muted" title="Un mismo perfil puede aparecer en varias filas: no se suma">
                —
              </td>
            </>
          )}
        />
      </section>
    </>
  )
}

function ReportTable({ state, empty, columns, rows, measure, render, footer }) {
  if (state.error) return <Notice tone="error">{state.error.message}</Notice>
  if (state.loading && !state.rows) return <div className="skeleton" style={{ height: '9rem' }} aria-hidden="true" />
  if (rows.length === 0) return <p className="muted">{empty}</p>

  const max = Math.max(...rows.map(measure), 1)
  const bar = (row) => (label) => (
    <span className="bar-cell" title={label}>
      <span className="bar-track" aria-hidden="true">
        <span className="bar" style={{ width: `${(measure(row) / max) * 100}%` }} />
      </span>
      <span className="bar-value">{label}</span>
    </span>
  )

  return (
    <div className="table-wrap" style={{ opacity: state.loading ? 0.6 : 1 }}>
      <table className="table">
        <thead>
          <tr>
            {columns.map(([label, align]) => (
              <th key={label} scope="col" className={align}>
                {label}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row, i) => (
            <tr key={i}>{render(row, bar(row))}</tr>
          ))}
        </tbody>
        <tfoot>
          <tr>{footer(rows)}</tr>
        </tfoot>
      </table>
    </div>
  )
}
