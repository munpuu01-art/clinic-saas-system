import React, { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ClinicApi, TokenStore } from '../api/ApiClient.js'
import { Field, Notice } from '../components/ui.jsx'

const EMPTY_FORM = {
  clinicName: '', slug: '', contactEmail: '', contactPhone: '',
  adminUsername: '', adminPassword: '', confirm: ''
}

// สร้างรหัสคลินิกอัตโนมัติจากชื่อ (แก้ไขเองได้ภายหลัง) — ผู้ป่วยจะใช้รหัสนี้ตอนสมัครสมาชิก
function slugify(text) {
  return text
    .toLowerCase()
    .replace(/[^a-z0-9ก-๙\s-]/g, '')
    .trim()
    .replace(/\s+/g, '-')
}

export default function ClinicSignup() {
  const [plans, setPlans] = useState([])
  const [planCode, setPlanCode] = useState(null)
  const [form, setForm] = useState(EMPTY_FORM)
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)
  const [slugTouched, setSlugTouched] = useState(false)

  useEffect(() => {
    ClinicApi.plans().then((list) => {
      setPlans(list)
      if (list.length > 0) setPlanCode(list[0].code)
    }).catch(setError)
  }, [])

  const set = (key) => (e) => {
    const value = e.target.value
    setForm((f) => {
      const next = { ...f, [key]: value }
      if (key === 'clinicName' && !slugTouched) next.slug = slugify(value)
      return next
    })
    if (key === 'slug') setSlugTouched(true)
  }

  const submit = async (e) => {
    e.preventDefault()
    setError(null)
    if (form.adminPassword !== form.confirm) {
      setError({ message: 'รหัสผ่านทั้งสองช่องไม่ตรงกัน' })
      return
    }
    if (!planCode) {
      setError({ message: 'กรุณาเลือกแพ็กเกจ' })
      return
    }
    setBusy(true)
    try {
      const result = await ClinicApi.register({
        clinicName: form.clinicName,
        slug: form.slug,
        contactEmail: form.contactEmail,
        contactPhone: form.contactPhone,
        adminUsername: form.adminUsername,
        adminPassword: form.adminPassword,
        planCode
      })
      if (result.requiresPayment) {
        // แพ็กเกจเสียเงิน — ไปกรอกบัตรที่หน้า Stripe Checkout ก่อน
        window.location.href = result.checkoutUrl
        return
      }
      // แพ็กเกจฟรี — เข้าใช้งานได้ทันที
      TokenStore.save(result.login)
      window.location.href = '/dashboard'
    } catch (err) {
      setError(err)
      setBusy(false)
    }
  }

  return (
    <div className="auth-shell">
      <section className="auth-side">
        <div className="auth-brand">
          <strong>สมัครใช้งานระบบคลินิก</strong>
          <span>สำหรับเจ้าของคลินิก / ผู้ดูแลคลินิก</span>
        </div>
        <p className="auth-tagline">
          เลือกแพ็กเกจที่เหมาะกับคลินิกของท่าน กรอกข้อมูลครั้งเดียว ได้ทั้งระบบนัดหมาย
          จัดคิว เวชระเบียน และบัญชีผู้ดูแลพร้อมใช้งานทันที
        </p>
        {plans.length > 0 && (
          <div className="plan-list">
            {plans.map((p) => (
              <button
                type="button"
                key={p.code}
                className={`plan-card ${planCode === p.code ? 'selected' : ''}`}
                onClick={() => setPlanCode(p.code)}
              >
                <div className="plan-card-head">
                  <strong>{p.name}</strong>
                  <span>{Number(p.priceMonthlyThb) === 0 ? 'ฟรี' : `${Number(p.priceMonthlyThb).toLocaleString()} บาท/เดือน`}</span>
                </div>
                <p>{p.description}</p>
              </button>
            ))}
          </div>
        )}
        <p className="auth-owner-link">
          มีบัญชีอยู่แล้ว? <Link to="/login">เข้าสู่ระบบ</Link>
        </p>
      </section>

      <section className="auth-main">
        <div className="auth-card">
          <Notice error={error} onDismiss={() => setError(null)} />
          <form onSubmit={submit}>
            <h1>ข้อมูลคลินิกและผู้ดูแล</h1>
            <p className="hint">ระบบจะสร้างบัญชีผู้ดูแลคลินิก (ADMIN) ให้อัตโนมัติ ใช้เข้าสู่ระบบได้ทันที</p>
            <div className="form-grid">
              <Field label="ชื่อคลินิก">
                <input value={form.clinicName} onChange={set('clinicName')} />
              </Field>
              <Field label="รหัสคลินิก (ใช้ในลิงก์สมัครสมาชิกผู้ป่วย)">
                <input value={form.slug} onChange={set('slug')} placeholder="เช่น jaidee-clinic" />
              </Field>
              <Field label="อีเมลติดต่อคลินิก">
                <input type="email" value={form.contactEmail} onChange={set('contactEmail')} />
              </Field>
              <Field label="เบอร์โทรศัพท์คลินิก">
                <input value={form.contactPhone} onChange={set('contactPhone')} />
              </Field>
              <Field label="ชื่อผู้ใช้สำหรับผู้ดูแลคลินิก">
                <input value={form.adminUsername} onChange={set('adminUsername')} autoComplete="username" />
              </Field>
              <Field label="รหัสผ่าน (อย่างน้อย 8 ตัว)">
                <input type="password" value={form.adminPassword} onChange={set('adminPassword')} autoComplete="new-password" />
              </Field>
              <Field label="ยืนยันรหัสผ่าน">
                <input type="password" value={form.confirm} onChange={set('confirm')} autoComplete="new-password" />
              </Field>
            </div>
            <button className="primary block" type="submit" disabled={busy}>
              {busy ? 'กำลังสมัคร…' : 'สมัครใช้งาน'}
            </button>
            <p className="hint center">
              เลือกแพ็กเกจฟรีจะเข้าใช้งานได้ทันที ส่วนแพ็กเกจเสียเงินจะพาไปกรอกบัตรผ่าน Stripe ก่อน
            </p>
          </form>
        </div>
      </section>
    </div>
  )
}
