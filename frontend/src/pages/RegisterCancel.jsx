import React from 'react'
import { Link } from 'react-router-dom'

export default function RegisterCancel() {
  return (
    <div className="auth-shell">
      <section className="auth-main" style={{ gridColumn: '1 / -1' }}>
        <div className="auth-card result-card">
          <h1>ยกเลิกการชำระเงิน</h1>
          <p className="hint">
            ท่านยังไม่ได้ชำระเงิน จึงยังไม่ได้เริ่มใช้งานแพ็กเกจที่เลือก
            สามารถกลับไปสมัครใหม่หรือเลือกแพ็กเกจฟรีเพื่อทดลองใช้งานก่อนได้
          </p>
          <Link className="btn primary" to="/signup">กลับไปหน้าสมัคร</Link>
        </div>
      </section>
    </div>
  )
}
