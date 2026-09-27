import React, { useEffect, useState } from 'react'

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
  CANCELED: 'gray', INCOMPLETE: 'amber',
  // ช่องทางรับเงินของแพลตฟอร์ม
  STRIPE: 'violet', BANK_TRANSFER: 'blue', CASH: 'green'
}

/** แสดงเงินบาท เช่น 2990 → ฿2,990 (ทศนิยมแสดงเฉพาะเมื่อมีสตางค์) */
export function baht(value) {
  const n = Number(value || 0)
  return `฿${n.toLocaleString('th-TH', { minimumFractionDigits: 0, maximumFractionDigits: 2 })}`
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

/**
 * คืนค่า true เมื่อ active ค้างนานเกิน ms มิลลิวินาที
 * ใช้บอกผู้ใช้ว่ากำลังรอเซิร์ฟเวอร์ (Render แผนฟรีต้องปลุกเครื่องประมาณ 1 นาที)
 */
export function useSlow(active, ms = 5000) {
  const [slow, setSlow] = useState(false)
  useEffect(() => {
    if (!active) { setSlow(false); return undefined }
    const timer = setTimeout(() => setSlow(true), ms)
    return () => clearTimeout(timer)
  }, [active, ms])
  return slow
}

/** หน้ารอระหว่างตรวจสิทธิ์ตอนเปิดเว็บ — บอกสถานะเพิ่มเมื่อรอนาน */
export function BootScreen() {
  const slow = useSlow(true, 5000)
  const verySlow = useSlow(true, 90000)
  return (
    <div className="boot" role="status" aria-live="polite">
      <div className="boot-inner">
        <span className="spinner" aria-hidden="true" />
        <p className="boot-title">{slow ? 'กำลังเชื่อมต่อเซิร์ฟเวอร์' : 'กำลังตรวจสอบสิทธิ์…'}</p>
        {slow && !verySlow && (
          <p className="boot-hint">เซิร์ฟเวอร์อาจเพิ่งเริ่มทำงาน ใช้เวลาประมาณ 1 นาที ไม่ต้องปิดหน้านี้</p>
        )}
        {verySlow && (
          <>
            <p className="boot-hint">เชื่อมต่อนานกว่าปกติ ลองโหลดหน้าใหม่อีกครั้ง</p>
            <button className="primary" onClick={() => window.location.reload()}>โหลดหน้าใหม่</button>
          </>
        )}
      </div>
    </div>
  )
}
