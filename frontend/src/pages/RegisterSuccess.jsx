import React from 'react'
import { Link } from 'react-router-dom'

export default function RegisterSuccess() {
  return (
    <div className="auth-shell">
      <section className="auth-main" style={{ gridColumn: '1 / -1' }}>
        <div className="auth-card result-card">
          <h1>ชำระเงินสำเร็จ 🎉</h1>
          <p className="hint">
            ขอบคุณที่สมัครใช้งานระบบ ระบบกำลังเปิดใช้งานคลินิกของท่าน ซึ่งอาจใช้เวลาสักครู่
            (รอการยืนยันอัตโนมัติจากผู้ให้บริการชำระเงิน) กรุณาเข้าสู่ระบบด้วยชื่อผู้ใช้และรหัสผ่าน
            ที่ตั้งไว้ตอนสมัคร
          </p>
          <Link className="btn primary" to="/login">ไปหน้าเข้าสู่ระบบ</Link>
        </div>
      </section>
    </div>
  )
}
