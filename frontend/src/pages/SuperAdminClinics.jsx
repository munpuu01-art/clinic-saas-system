import React, { useEffect, useState } from 'react'
import { SuperAdminApi } from '../api/ApiClient.js'
import { Card, Empty, Field, Modal, Notice, Pill } from '../components/ui.jsx'

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

  const load = () => SuperAdminApi.clinics().then(setClinics).catch(setError)

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

  return (
    <>
      <div className="page-head">
        <div>
          <h1>คลินิกทั้งหมด</h1>
          <p>ภาพรวมลูกค้าทุกคลินิกที่สมัครใช้งานระบบ ไม่ว่าจะสมัครเองหรือเพิ่มโดยผู้ดูแล</p>
        </div>
        <button className="primary" onClick={openCreate}>เพิ่มคลินิกใหม่</button>
      </div>

      <Notice error={error} message={message} onDismiss={() => { setError(null); setMessage(null) }} />

      <div className="stat-row">
        <div className="stat"><div className="label">คลินิกทั้งหมด</div><div className="value">{summary.total}</div></div>
        <div className="stat"><div className="label">ใช้งานปกติ</div><div className="value">{summary.active}</div></div>
        <div className="stat"><div className="label">ทดลองใช้งาน</div><div className="value">{summary.trialing}</div></div>
        <div className="stat"><div className="label">ระงับ/ยกเลิก</div><div className="value">{summary.suspended}</div></div>
      </div>

      <Card title={`รายชื่อคลินิก (${clinics.length})`}>
        {clinics.length === 0 ? <Empty>ยังไม่มีคลินิกในระบบ</Empty> : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>คลินิก</th><th>รหัส</th><th>แพ็กเกจ</th><th>สถานะ</th>
                  <th>แพทย์</th><th>ผู้ป่วย</th><th>รอบบิลถัดไป</th><th>การจัดการ</th>
                </tr>
              </thead>
              <tbody>
                {clinics.map((c) => (
                  <tr key={c.id}>
                    <td>{c.name}<span className="sub"> {c.contactEmail}</span></td>
                    <td className="numeric">{c.slug}</td>
                    <td>{c.planName || '-'}</td>
                    <td><Pill status={c.status} label={STATUS_LABEL[c.status] || c.statusLabel} /></td>
                    <td className="numeric">{c.doctorCount}</td>
                    <td className="numeric">{c.patientCount}</td>
                    <td className="numeric">
                      {c.currentPeriodEnd ? c.currentPeriodEnd.slice(0, 10) : '-'}
                    </td>
                    <td>
                      <div className="row wrap">
                        {c.status !== 'ACTIVE' && (
                          <button className="ghost" onClick={() => act(() => SuperAdminApi.setClinicStatus(c.id, 'ACTIVE'), 'เปิดใช้งานคลินิกแล้ว')}>เปิดใช้งาน</button>
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
