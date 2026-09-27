import React from 'react'

export function Card({ title, actions, children }) {
  return (
    <section className="card">
      {(title || actions) && (
        <div className="card-title">
          <h2>{title}</h2>
          <div className="row">{actions}</div>
        </div>
      )}
      {children}
    </section>
  )
}

/** tone: 'violet' | 'blue' | 'cyan' | 'green' | 'amber' | 'red' — หรือค่าสี CSS แบบเดิมก็ยังใช้ได้ */
const STAT_TONES = ['violet', 'blue', 'cyan', 'green', 'amber', 'red']

export function Stat({ label, value, tone, hint }) {
  const named = STAT_TONES.includes(tone)
  return (
    <div className={`stat ${named ? `tone-${tone}` : ''}`}>
      <div className="label">{label}</div>
      <div className="value numeric" style={tone && !named ? { color: tone } : undefined}>{value}</div>
      {hint && <div className="stat-hint">{hint}</div>}
    </div>
  )
}

/** สีของป้ายสถานะ — ใช้ร่วมกันทั้งนัดหมาย คิว คลินิก และแพ็กเกจ */
export const PILL_TONE = {
  // นัดหมาย
  REQUESTED: 'violet', CONFIRMED: 'blue', CHECKED_IN: 'cyan', IN_PROGRESS: 'amber',
  COMPLETED: 'green', CANCELLED: 'gray', NO_SHOW: 'red',
  // บัตรคิว
  WAITING: 'gray', CALLED: 'amber', SERVING: 'blue', DONE: 'green', SKIPPED: 'red',
  // ระดับความสำคัญของคิว
  EMERGENCY: 'red', ELDERLY: 'amber', APPOINTMENT: 'blue', NORMAL: 'gray',
  // คลินิก / แพ็กเกจ
  TRIALING: 'violet', ACTIVE: 'green', PAST_DUE: 'amber', SUSPENDED: 'red',
  CANCELED: 'gray', INCOMPLETE: 'amber'
}

export function Pill({ status, label }) {
  return <span className={`pill ${PILL_TONE[status] || ''}`}>{label || status}</span>
}

export function Field({ label, children }) {
  return (
    <div className="field">
      <label>{label}</label>
      {children}
    </div>
  )
}

export function Modal({ title, onClose, children, footer }) {
  return (
    <div className="backdrop" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className="modal" role="dialog" aria-modal="true" aria-label={title}>
        <h2>{title}</h2>
        {children}
        <div className="modal-actions">{footer}</div>
      </div>
    </div>
  )
}

export function Notice({ error, message, onDismiss }) {
  if (!error && !message) return null
  return (
    <div className={`notice ${error ? 'error' : 'ok'}`} onClick={onDismiss}>
      {error ? error.message : message}
    </div>
  )
}

export function Empty({ children }) {
  return <div className="empty">{children}</div>
}

export function today() {
  return new Date().toISOString().slice(0, 10)
}

export function thaiDate(iso) {
  if (!iso) return '-'
  const [y, m, d] = iso.split('-')
  return `${d}/${m}/${y}`
}
