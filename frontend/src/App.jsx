import React, { useEffect, useState } from 'react'
import { NavLink, Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from './auth/AuthContext.jsx'
import RequireRole from './auth/RequireRole.jsx'
import { Icon, ROUTE_ICON } from './components/icons.jsx'
import { PILL_TONE } from './components/ui.jsx'
import Login from './pages/Login.jsx'
import Dashboard from './pages/Dashboard.jsx'
import Patients from './pages/Patients.jsx'
import Doctors from './pages/Doctors.jsx'
import Appointments from './pages/Appointments.jsx'
import QueueBoard from './pages/QueueBoard.jsx'
import Accounts from './pages/Accounts.jsx'
import MySubscription from './pages/MySubscription.jsx'
import MyAppointments from './pages/portal/MyAppointments.jsx'
import BookAppointment from './pages/portal/BookAppointment.jsx'
import MyRecords from './pages/portal/MyRecords.jsx'
import ClinicSignup from './pages/ClinicSignup.jsx'
import RegisterSuccess from './pages/RegisterSuccess.jsx'
import RegisterCancel from './pages/RegisterCancel.jsx'
import SuperAdminClinics from './pages/SuperAdminClinics.jsx'

const STAFF_ROLES = ['ADMIN', 'STAFF', 'DOCTOR']

const CLINIC_STATUS_LABEL = {
  TRIALING: 'ทดลองใช้งาน', ACTIVE: 'ใช้งานปกติ', PAST_DUE: 'ค้างชำระเงิน',
  SUSPENDED: 'ถูกระงับ', CANCELED: 'ยกเลิกแล้ว'
}

function initials(name) {
  if (!name) return '?'
  const parts = name.trim().split(/\s+/)
  return (parts[0][0] + (parts[1]?.[0] || '')).toUpperCase()
}

/** โครงหน้าจอหลังล็อกอิน — เมนูมาจากบทบาทของผู้ใช้ */
function Shell({ children }) {
  const { session, menu, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [navOpen, setNavOpen] = useState(false)

  // ปิดเมนูบนมือถือทุกครั้งที่เปลี่ยนหน้า
  useEffect(() => { setNavOpen(false) }, [location.pathname])

  const signOut = () => {
    logout()
    navigate('/login', { replace: true })
  }

  const isSuper = session?.role === 'SUPER_ADMIN'
  const current = menu.find((m) => m.to === location.pathname)
  const status = session?.clinicStatus
  const todayLabel = new Date().toLocaleDateString('th-TH', { weekday: 'long', day: 'numeric', month: 'long' })

  return (
    <div className={`shell ${navOpen ? 'nav-open' : ''}`}>
      <aside className="sidebar" aria-label="เมนูหลัก">
        <div className="brand">
          <span className="brand-mark"><Icon.pulse /></span>
          <div className="brand-text">
            <strong>{isSuper ? 'Clinic SaaS' : (session?.clinicName || 'คลินิก')}</strong>
            <span>
              {isSuper ? 'แผงควบคุมแพลตฟอร์ม'
                : session?.role === 'PATIENT' ? 'พอร์ทัลผู้ป่วย' : 'ระบบนัดหมายและคิว'}
            </span>
          </div>
          <button className="nav-close" onClick={() => setNavOpen(false)} aria-label="ปิดเมนู"><Icon.close /></button>
        </div>

        <nav className="nav">
          {menu.map((item) => {
            const RouteIcon = ROUTE_ICON[item.to]
            return (
              <NavLink key={item.to} to={item.to} end={item.to === '/portal'}>
                {RouteIcon && <RouteIcon />}
                <span>{item.label}</span>
              </NavLink>
            )
          })}
        </nav>

        <div className="who">
          <span className="avatar">{initials(session?.displayName)}</span>
          <div className="who-text">
            <div className="who-name">{session?.displayName}</div>
            <div className="who-role">{session?.roleLabel}{session?.hn ? ` · ${session.hn}` : ''}</div>
          </div>
          <button className="signout" onClick={signOut} aria-label="ออกจากระบบ" title="ออกจากระบบ">
            <Icon.logout />
          </button>
        </div>
      </aside>

      <div className="scrim" onClick={() => setNavOpen(false)} aria-hidden="true" />

      <div className="main">
        <header className="topbar">
          <button className="nav-toggle" onClick={() => setNavOpen(true)} aria-label="เปิดเมนู"><Icon.menu /></button>
          <div className="crumb">
            {!isSuper && session?.clinicName && <span className="crumb-clinic">{session.clinicName}</span>}
            <span className="crumb-page">{current?.label || ''}</span>
          </div>
          <div className="topbar-side">
            {status && !isSuper && (
              <span className={`pill ${PILL_TONE[status] || ''}`}>{CLINIC_STATUS_LABEL[status] || status}</span>
            )}
            <span className="topbar-date">{todayLabel}</span>
          </div>
        </header>
        <main className="content">{children}</main>
      </div>
    </div>
  )
}

/** ส่งผู้ใช้ไปยังหน้าแรกที่เหมาะกับบทบาทของตน */
function HomeRedirect() {
  const { isAuthenticated, role, checking } = useAuth()
  if (checking) return <div className="boot">กำลังตรวจสอบสิทธิ์…</div>
  if (!isAuthenticated) return <Navigate to="/login" replace />
  return <Navigate to={landingPathFor(role)} replace />
}

function landingPathFor(role) {
  if (role === 'PATIENT') return '/portal'
  if (role === 'SUPER_ADMIN') return '/super-admin'
  return '/dashboard'
}

export default function App() {
  const { isAuthenticated, role } = useAuth()

  return (
    <Routes>
      <Route
        path="/login"
        element={isAuthenticated ? <Navigate to={landingPathFor(role)} replace /> : <Login />}
      />
      <Route path="/signup" element={isAuthenticated ? <Navigate to={landingPathFor(role)} replace /> : <ClinicSignup />} />
      <Route path="/register/success" element={<RegisterSuccess />} />
      <Route path="/register/cancel" element={<RegisterCancel />} />

      {/* ---------- หน้าจอหลังบ้าน ---------- */}
      <Route path="/dashboard" element={
        <RequireRole roles={STAFF_ROLES}><Shell><Dashboard /></Shell></RequireRole>} />
      <Route path="/appointments" element={
        <RequireRole roles={STAFF_ROLES}><Shell><Appointments /></Shell></RequireRole>} />
      <Route path="/queue" element={
        <RequireRole roles={STAFF_ROLES}><Shell><QueueBoard /></Shell></RequireRole>} />
      <Route path="/patients" element={
        <RequireRole roles={STAFF_ROLES}><Shell><Patients /></Shell></RequireRole>} />
      <Route path="/doctors" element={
        <RequireRole roles={STAFF_ROLES}><Shell><Doctors /></Shell></RequireRole>} />
      <Route path="/accounts" element={
        <RequireRole roles={['ADMIN']}><Shell><Accounts /></Shell></RequireRole>} />
      <Route path="/billing" element={
        <RequireRole roles={['ADMIN']}><Shell><MySubscription /></Shell></RequireRole>} />

      {/* ---------- ผู้ดูแลระบบ SaaS ---------- */}
      <Route path="/super-admin" element={
        <RequireRole roles={['SUPER_ADMIN']}><Shell><SuperAdminClinics /></Shell></RequireRole>} />

      {/* ---------- พอร์ทัลผู้ป่วย ---------- */}
      <Route path="/portal" element={
        <RequireRole roles={['PATIENT']}><Shell><MyAppointments /></Shell></RequireRole>} />
      <Route path="/portal/book" element={
        <RequireRole roles={['PATIENT']}><Shell><BookAppointment /></Shell></RequireRole>} />
      <Route path="/portal/records" element={
        <RequireRole roles={['PATIENT']}><Shell><MyRecords /></Shell></RequireRole>} />

      <Route path="*" element={<HomeRedirect />} />
    </Routes>
  )
}
