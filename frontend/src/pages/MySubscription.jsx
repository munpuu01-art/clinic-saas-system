import React, { useEffect, useState } from 'react'
import { BillingApi, ClinicApi } from '../api/ApiClient.js'
import { Card, Empty, Notice, Pill } from '../components/ui.jsx'

const STATUS_LABEL = {
  TRIALING: 'ทดลองใช้งาน', ACTIVE: 'ใช้งานปกติ', PAST_DUE: 'ค้างชำระเงิน',
  CANCELED: 'ยกเลิกแล้ว', INCOMPLETE: 'รอดำเนินการชำระเงิน'
}

function formatDate(iso) {
  if (!iso) return '-'
  return new Date(iso).toLocaleDateString('th-TH', { year: 'numeric', month: 'long', day: 'numeric' })
}

export default function MySubscription() {
  const [subscription, setSubscription] = useState(null)
  const [plans, setPlans] = useState([])
  const [error, setError] = useState(null)
  const [message, setMessage] = useState(null)
  const [busyPlan, setBusyPlan] = useState(null)
  const [busyAction, setBusyAction] = useState(false)

  const load = () => BillingApi.mySubscription().then(setSubscription).catch(setError)

  useEffect(() => {
    load()
    ClinicApi.plans().then(setPlans).catch(setError)
  }, [])

  const changePlan = async (planCode) => {
    setError(null)
    setBusyPlan(planCode)
    try {
      const result = await BillingApi.changePlan(planCode)
      if (result.requiresPayment) {
        // แพ็กเกจฟรี → เสียเงินครั้งแรก ต้องไปกรอกบัตรที่ Stripe ก่อน
        window.location.href = result.checkoutUrl
        return
      }
      setSubscription(result.subscription)
      setMessage(`เปลี่ยนแพ็กเกจเป็น "${result.subscription.planName}" เรียบร้อยแล้ว`)
    } catch (err) {
      setError(err)
    } finally {
      setBusyPlan(null)
    }
  }

  const cancelSubscription = async () => {
    if (!window.confirm('ยืนยันยกเลิกแพ็กเกจปัจจุบัน? ท่านยังใช้งานได้จนถึงวันสิ้นสุดรอบบิลปัจจุบัน')) return
    setError(null)
    setBusyAction(true)
    try {
      const updated = await BillingApi.cancel()
      setSubscription(updated)
      setMessage('ตั้งค่าการยกเลิกเรียบร้อย จะมีผลเมื่อสิ้นสุดรอบบิลปัจจุบัน')
    } catch (err) {
      setError(err)
    } finally {
      setBusyAction(false)
    }
  }

  const reactivate = async () => {
    setError(null)
    setBusyAction(true)
    try {
      const updated = await BillingApi.reactivate()
      setSubscription(updated)
      setMessage('ยกเลิกคำขอยกเลิกแล้ว แพ็กเกจจะต่ออายุตามปกติ')
    } catch (err) {
      setError(err)
    } finally {
      setBusyAction(false)
    }
  }

  if (!subscription && !error) {
    return (
      <>
        <div className="page-head"><div><h1>แพ็กเกจของฉัน</h1></div></div>
        <Card><Empty>กำลังโหลดข้อมูล…</Empty></Card>
      </>
    )
  }

  return (
    <>
      <div className="page-head">
        <div>
          <h1>แพ็กเกจของฉัน</h1>
          <p>ดูแพ็กเกจปัจจุบันของคลินิก และอัปเกรด/ดาวน์เกรด/ยกเลิกได้ด้วยตนเอง</p>
        </div>
      </div>

      <Notice error={error} message={message} onDismiss={() => { setError(null); setMessage(null) }} />

      {subscription && (
        <Card title="แพ็กเกจปัจจุบัน">
          <div className="sub-current">
            <div>
              <div className="sub-plan-name">{subscription.planName}</div>
              <div className="sub-plan-price">
                {Number(subscription.planPriceMonthlyThb) === 0
                  ? 'ฟรี'
                  : `${Number(subscription.planPriceMonthlyThb).toLocaleString()} บาท/เดือน`}
              </div>
            </div>
            <div className="sub-status">
              <Pill status={subscription.subscriptionStatus} label={STATUS_LABEL[subscription.subscriptionStatus] || subscription.subscriptionStatusLabel} />
              {subscription.cancelAtPeriodEnd && (
                <span className="sub-cancel-note">จะสิ้นสุดวันที่ {formatDate(subscription.currentPeriodEnd)}</span>
              )}
            </div>
          </div>

          <div className="sub-meta">
            <div><span className="label">รอบบิลปัจจุบันเริ่ม</span><strong>{formatDate(subscription.currentPeriodStart)}</strong></div>
            <div><span className="label">รอบบิลถัดไป / สิ้นสุด</span><strong>{formatDate(subscription.currentPeriodEnd)}</strong></div>
            <div><span className="label">จำนวนแพทย์สูงสุด</span><strong>{subscription.maxDoctors ?? 'ไม่จำกัด'}</strong></div>
            <div><span className="label">ผู้ป่วยสูงสุด/เดือน</span><strong>{subscription.maxActivePatients ?? 'ไม่จำกัด'}</strong></div>
          </div>

          <div className="row wrap sub-actions">
            {subscription.cancelAtPeriodEnd ? (
              <button className="primary" disabled={busyAction} onClick={reactivate}>
                {busyAction ? 'กำลังดำเนินการ…' : 'ยกเลิกคำขอยกเลิก (ใช้ต่อตามปกติ)'}
              </button>
            ) : subscription.planPriceMonthlyThb > 0 ? (
              <button className="ghost danger" disabled={busyAction} onClick={cancelSubscription}>
                {busyAction ? 'กำลังดำเนินการ…' : 'ยกเลิกแพ็กเกจ'}
              </button>
            ) : null}
          </div>
        </Card>
      )}

      <Card title="เปลี่ยนแพ็กเกจ">
        {plans.length === 0 ? <Empty>กำลังโหลดแพ็กเกจ…</Empty> : (
          <div className="plan-grid">
            {plans.map((p) => {
              const isCurrent = subscription && p.code === subscription.planCode
              return (
                <div key={p.code} className={`plan-tile ${isCurrent ? 'current' : ''}`}>
                  <div className="plan-tile-head">
                    <strong>{p.name}</strong>
                    <span>{Number(p.priceMonthlyThb) === 0 ? 'ฟรี' : `${Number(p.priceMonthlyThb).toLocaleString()} บาท/เดือน`}</span>
                  </div>
                  <p>{p.description}</p>
                  <ul>
                    <li>แพทย์สูงสุด: {p.maxDoctors ?? 'ไม่จำกัด'}</li>
                    <li>ผู้ป่วยสูงสุด/เดือน: {p.maxActivePatients ?? 'ไม่จำกัด'}</li>
                  </ul>
                  {isCurrent ? (
                    <span className="plan-tile-current-badge">แพ็กเกจปัจจุบัน</span>
                  ) : (
                    <button
                      className="primary block"
                      disabled={busyPlan === p.code}
                      onClick={() => changePlan(p.code)}
                    >
                      {busyPlan === p.code ? 'กำลังดำเนินการ…' : 'เปลี่ยนเป็นแพ็กเกจนี้'}
                    </button>
                  )}
                </div>
              )
            })}
          </div>
        )}
        <p className="hint">
          การอัปเกรด/ดาวน์เกรดระหว่างแพ็กเกจเสียเงินจะปรับยอดเรียกเก็บตามสัดส่วนวันที่เหลืออัตโนมัติ
          ส่วนการอัปเกรดจากแพ็กเกจฟรีครั้งแรกจะพาไปกรอกบัตรผ่าน Stripe ก่อน
        </p>
      </Card>
    </>
  )
}
