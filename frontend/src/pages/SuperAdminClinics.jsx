import React, { useEffect, useState } from 'react'
import { SuperAdminApi } from '../api/ApiClient.js'
import { Card, Empty, Field, Modal, Notice, Pill, Stat, baht, thaiDate, today } from '../components/ui.jsx'
import { Icon } from '../components/icons.jsx'

const STATUS_LABEL = {
  TRIALING: 'ทดลองใช้งาน', ACTIVE: 'ใช้งานปกติ', PAST_DUE: 'ค้างชำระเงิน',
  SUSPENDED: 'ถูกระงับ', CANCELED: 'ยกเลิกแล้ว'
}

const EMPTY_FORM = {
  clinicName: '', slug: '', contactEmail: '', contactPhone: '',
  adminUsername: '', adminPassword: '', planCode: ''
}

export default function SuperAdminClinics() {
  const [clinics, setClinics] = useState([])
  const [plans, setPlans] = useState([])
  const [form, setForm] = useState(null)
  const [error, setError] = useState(null)
  const [message, setMessage] = useState(null)
  const [query, setQuery] = useState('')
  const [filter, setFilter] = useState('ALL')
  const [revenue, setRevenue] = useState(null)
  const [payForm, setPayForm] = useState(null)

  const load = () => {
    SuperAdminApi.clinics().then(setClinics).catch(setError)
    SuperAdminApi.revenue().then(setRevenue).catch(setError)
  }

  // ยอดชำระสะสมของแต่ละคลินิก (clinicId → ข้อมูล)
  const paidByClinic = Object.fromEntries((revenue?.byClinic || []).map((r) => [r.clinicId, r]))

  const openPayment = (clinic) => setPayForm({
    clinic,
    amountThb: clinic.planPriceMonthlyThb > 0 ? String(Number(clinic.planPriceMonthlyThb)) : '',
    method: 'BANK_TRANSFER',
    paidDate: today(),
    note: ''
  })

  const savePayment = async () => {
    try {
      await SuperAdminApi.recordPayment(payForm.clinic.id, {
        amountThb: Number(payForm.amountThb),
        method: payForm.method,
        paidDate: payForm.paidDate || null,
        note: payForm.note
      })
      setMessage(`บันทึกรับเงิน ${baht(payForm.amountThb)} จาก ${payForm.clinic.name} แล้ว`)
      setPayForm(null)
      load()
    } catch (err) { setError(err) }
  }

  const removePayment = (p) => {
    if (!window.confirm(`ลบรายการรับเงิน ${baht(p.amountThb)} ของ ${p.clinicName}?`)) return
    act(() => SuperAdminApi.deletePayment(p.id), 'ลบรายการรับเงินแล้ว')
  }

  useEffect(() => {
    load()
    SuperAdminApi.plans().then((list) => {
      setPlans(list)
    }).catch(setError)
  }, [])

  const set = (key) => (e) => setForm({ ...form, [key]: e.target.value })

  const act = async (fn, text) => {
    try { await fn(); setMessage(text); load() } catch (err) { setError(err) }
  }

  const openCreate = () => setForm({ ...EMPTY_FORM, planCode: plans[0]?.code || '' })

  const save = async () => {
    try {
      await SuperAdminApi.createClinic(form)
      setMessage(`เพิ่มคลินิก ${form.clinicName} เรียบร้อย (เปิดใช้งานทันที ไม่ผ่านการชำระเงิน)`)
      setForm(null)
      load()
    } catch (err) { setError(err) }
  }

  const summary = {
    total: clinics.length,
    active: clinics.filter((c) => c.status === 'ACTIVE').length,
    trialing: clinics.filter((c) => c.status === 'TRIALING').length,
    suspended: clinics.filter((c) => c.status === 'SUSPENDED' || c.status === 'CANCELED').length
  }

  const FILTERS = [
    { key: 'ALL', label: 'ทั้งหมด' },
    { key: 'ACTIVE', label: 'ใช้งานปกติ' },
    { key: 'TRIALING', label: 'ทดลองใช้' },
    { key: 'PAST_DUE', label: 'ค้างชำระ' },
    { key: 'SUSPENDED', label: 'ระงับ' },
    { key: 'CANCELED', label: 'ยกเลิก' }
  ]
  const countOf = (key) => key === 'ALL' ? clinics.length : clinics.filter((c) => c.status === key).length
  const q = query.trim().toLowerCase()
  const visible = clinics.filter((c) =>
    (filter === 'ALL' || c.status === filter) &&
    (!q || [c.name, c.slug, c.contactEmail, c.planName].some((v) => v && v.toLowerCase().includes(q)))
  )

  return (
    <>
      <div className="page-head">
        <div>
          <h1>ภาพรวมแพลตฟอร์ม</h1>
          <p>รายรับจากค่าบริการ และลูกค้าทุกคลินิกที่สมัครใช้งานระบบ</p>
        </div>
        <button className="primary" onClick={openCreate}>เพิ่มคลินิกใหม่</button>
      </div>

      <Notice error={error} message={message} onDismiss={() => { setError(null); setMessage(null) }} />

      <section className="hero hero-revenue">
        <div>
          <div className="hero-label">รายรับรวมทั้งหมด</div>
          <div className="hero-number">{baht(revenue?.totalReceivedThb)}</div>
          <div className="hero-sub">
            จากการชำระเงิน {revenue?.paymentCount ?? 0} รายการ · นับเฉพาะเงินที่ได้รับจริง
          </div>
        </div>
        <div className="hero-side">
          <div><span>รับเดือนนี้</span><strong>{baht(revenue?.receivedThisMonthThb)}</strong></div>
          <div><span>รายได้ประจำ/เดือน</span><strong>{baht(revenue?.monthlyRecurringThb)}</strong></div>
          <div><span>คลินิกที่จ่ายเงิน</span><strong>{revenue?.payingClinicCount ?? 0}</strong></div>
        </div>
      </section>

      <div className="stat-row">
        <Stat label="คลินิกทั้งหมด" value={summary.total} />
        <Stat label="ใช้งานปกติ" value={summary.active} tone="green" />
        <Stat label="ทดลองใช้งาน" value={summary.trialing} tone="violet" />
        <Stat label="ระงับ/ยกเลิก" value={summary.suspended} tone="red" />
      </div>

      <Card title="รายชื่อคลินิก">
        <div className="toolbar">
          <label className="search" aria-label="ค้นหาคลินิก">
            <Icon.search />
            <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="ค้นหาชื่อ รหัส อีเมล หรือแพ็กเกจ" />
          </label>
          <div className="segmented" role="tablist">
            {FILTERS.map((f) => (
              <button key={f.key} role="tab" aria-selected={filter === f.key}
                className={filter === f.key ? 'active' : ''} onClick={() => setFilter(f.key)}>
                {f.label}<span className="count">{countOf(f.key)}</span>
              </button>
            ))}
          </div>
        </div>
        {clinics.length === 0 ? <Empty>ยังไม่มีคลินิกในระบบ กด "เพิ่มคลินิกใหม่" เพื่อเริ่มต้น</Empty>
          : visible.length === 0 ? <Empty>ไม่พบคลินิกที่ตรงกับการค้นหา</Empty> : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>คลินิก</th><th>แพ็กเกจ</th><th className="num">ราคา/เดือน</th>
                  <th className="num">ชำระแล้วรวม</th><th>สถานะ</th>
                  <th>แพทย์</th><th>ผู้ป่วย</th><th>รอบบิลถัดไป</th><th>การจัดการ</th>
                </tr>
              </thead>
              <tbody>
                {visible.map((c) => (
                  <tr key={c.id}>
                    <td>
                      <div className="clinic-cell">
                        <strong>{c.name}</strong>
                        <span><span className="slug">{c.slug}</span> {c.contactEmail}</span>
                      </div>
                    </td>
                    <td>{c.planName || '-'}</td>
                    <td className="numeric num">
                      {c.planPriceMonthlyThb == null ? '-' : Number(c.planPriceMonthlyThb) === 0 ? 'ฟรี' : baht(c.planPriceMonthlyThb)}
                    </td>
                    <td className="numeric num">
                      {paidByClinic[c.id]
                        ? <><strong className="money">{baht(paidByClinic[c.id].totalPaidThb)}</strong>
                          <span className="sub"> · {paidByClinic[c.id].paymentCount} ครั้ง</span></>
                        : <span className="sub">ยังไม่มี</span>}
                    </td>
                    <td><Pill status={c.status} label={STATUS_LABEL[c.status] || c.statusLabel} /></td>
                    <td className="numeric">{c.doctorCount}</td>
                    <td className="numeric">{c.patientCount}</td>
                    <td className="numeric">
                      {c.currentPeriodEnd ? c.currentPeriodEnd.slice(0, 10) : '-'}
                    </td>
                    <td className="actions">
                      <div className="row">
                        <button className="ghost" onClick={() => openPayment(c)}>รับเงิน</button>
                        {c.status !== 'ACTIVE' && (
                          <button className="ghost" onClick={() => act(() => SuperAdminApi.setClinicStatus(c.id, 'ACTIVE'), 'เปิดใช้งานคลินิกแล้ว')}>เปิดใช้</button>
                        )}
                        {c.status !== 'SUSPENDED' && (
                          <button className="ghost danger" onClick={() => act(() => SuperAdminApi.setClinicStatus(c.id, 'SUSPENDED'), 'ระงับคลินิกแล้ว')}>ระงับ</button>
                        )}
                        {c.status !== 'CANCELED' && (
                          <button className="ghost danger" onClick={() => {
                            if (window.confirm(`ยืนยันยกเลิกคลินิก ${c.name}?`)) {
                              act(() => SuperAdminApi.setClinicStatus(c.id, 'CANCELED'), 'ยกเลิกคลินิกแล้ว')
                            }
                          }}>ยกเลิก</button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <Card title="รายการรับเงินล่าสุด">
        {!revenue || revenue.recentPayments.length === 0 ? (
          <Empty>ยังไม่มีรายการรับเงิน — เมื่อคลินิกจ่ายผ่าน Stripe ระบบจะบันทึกให้อัตโนมัติ หรือกด "รับเงิน" ในตารางด้านบน</Empty>
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>วันที่รับเงิน</th><th>คลินิก</th><th>แพ็กเกจ</th><th>ช่องทาง</th>
                  <th className="num">จำนวนเงิน</th><th>หมายเหตุ / อ้างอิง</th><th></th>
                </tr>
              </thead>
              <tbody>
                {revenue.recentPayments.map((p) => (
                  <tr key={p.id}>
                    <td className="numeric">{thaiDate(p.paidAt.slice(0, 10))}</td>
                    <td>{p.clinicName}</td>
                    <td>{p.planCode || '-'}</td>
                    <td><Pill status={p.method} label={p.methodLabel} /></td>
                    <td className="numeric num"><strong className="money">{baht(p.amountThb)}</strong></td>
                    <td><span className="sub">{p.note || p.reference || '-'}</span></td>
                    <td>
                      {p.manual && <button className="ghost danger" onClick={() => removePayment(p)}>ลบ</button>}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      {payForm && (
        <Modal
          title={`บันทึกรับเงิน · ${payForm.clinic.name}`}
          onClose={() => setPayForm(null)}
          footer={<>
            <button onClick={() => setPayForm(null)}>ยกเลิก</button>
            <button className="primary" onClick={savePayment} disabled={!(Number(payForm.amountThb) > 0)}>บันทึก</button>
          </>}
        >
          <div className="form-grid">
            <Field label="จำนวนเงิน (บาท)">
              <input type="number" min="0" step="0.01" inputMode="decimal" value={payForm.amountThb}
                onChange={(e) => setPayForm({ ...payForm, amountThb: e.target.value })} />
            </Field>
            <Field label="ช่องทาง">
              <select value={payForm.method} onChange={(e) => setPayForm({ ...payForm, method: e.target.value })}>
                <option value="BANK_TRANSFER">โอนเงิน</option>
                <option value="CASH">เงินสด</option>
              </select>
            </Field>
            <Field label="วันที่รับเงิน">
              <input type="date" max={today()} value={payForm.paidDate}
                onChange={(e) => setPayForm({ ...payForm, paidDate: e.target.value })} />
            </Field>
            <Field label="หมายเหตุ">
              <input value={payForm.note} placeholder="เช่น เลขที่สลิป / รอบบิล ต.ค."
                onChange={(e) => setPayForm({ ...payForm, note: e.target.value })} />
            </Field>
          </div>
          <p className="hint">
            แพ็กเกจปัจจุบัน: {payForm.clinic.planName || '-'}
            {payForm.clinic.planPriceMonthlyThb > 0 && ` (${baht(payForm.clinic.planPriceMonthlyThb)}/เดือน)`}
            {' '}· การชำระผ่าน Stripe จะถูกบันทึกอัตโนมัติ ไม่ต้องบันทึกซ้ำ
          </p>
        </Modal>
      )}

      {form && (
        <Modal
          title="เพิ่มคลินิกใหม่ (ไม่ผ่านการชำระเงิน)"
          onClose={() => setForm(null)}
          footer={<>
            <button onClick={() => setForm(null)}>ยกเลิก</button>
            <button className="primary" onClick={save}>สร้างคลินิก</button>
          </>}
        >
          <div className="form-grid">
            <Field label="ชื่อคลินิก"><input value={form.clinicName} onChange={set('clinicName')} /></Field>
            <Field label="รหัสคลินิก (slug)"><input value={form.slug} onChange={set('slug')} /></Field>
            <Field label="อีเมลติดต่อ"><input type="email" value={form.contactEmail} onChange={set('contactEmail')} /></Field>
            <Field label="เบอร์โทรศัพท์"><input value={form.contactPhone} onChange={set('contactPhone')} /></Field>
            <Field label="ชื่อผู้ใช้ผู้ดูแลคลินิก"><input value={form.adminUsername} onChange={set('adminUsername')} /></Field>
            <Field label="รหัสผ่าน (อย่างน้อย 8 ตัว)">
              <input type="password" value={form.adminPassword} onChange={set('adminPassword')} />
            </Field>
            <Field label="แพ็กเกจ">
              <select value={form.planCode} onChange={set('planCode')}>
                {plans.map((p) => <option key={p.code} value={p.code}>{p.name}</option>)}
              </select>
            </Field>
          </div>
          <p className="hint">ใช้สำหรับลูกค้าที่ตกลงราคากันนอกระบบแล้ว คลินิกจะถูกเปิดใช้งานทันทีโดยไม่ต้องผ่าน Stripe</p>
        </Modal>
      )}
    </>
  )
}
