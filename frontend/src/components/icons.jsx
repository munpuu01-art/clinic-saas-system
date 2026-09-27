import React from 'react'

/** ไอคอนเส้น (stroke) ขนาดเดียวกันทั้งระบบ — ใช้สีตาม currentColor */
function Svg({ children, size = 18 }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor"
      strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      {children}
    </svg>
  )
}

export const Icon = {
  dashboard: () => <Svg><rect x="3" y="3" width="7" height="9" rx="1.5" /><rect x="14" y="3" width="7" height="5" rx="1.5" /><rect x="14" y="12" width="7" height="9" rx="1.5" /><rect x="3" y="16" width="7" height="5" rx="1.5" /></Svg>,
  calendar: () => <Svg><rect x="3" y="4.5" width="18" height="16.5" rx="2" /><path d="M3 9.5h18M8 2.5v4M16 2.5v4" /></Svg>,
  calendarPlus: () => <Svg><rect x="3" y="4.5" width="18" height="16.5" rx="2" /><path d="M3 9.5h18M8 2.5v4M16 2.5v4M12 13v5M9.5 15.5h5" /></Svg>,
  queue: () => <Svg><path d="M4 6h10M4 12h10M4 18h7" /><circle cx="18.5" cy="6" r="1.5" /><circle cx="18.5" cy="12" r="1.5" /></Svg>,
  users: () => <Svg><circle cx="9" cy="8" r="3.5" /><path d="M2.5 20c.6-3.4 3.2-5.5 6.5-5.5s5.9 2.1 6.5 5.5" /><path d="M16 4.8a3.3 3.3 0 0 1 0 6.4M18.5 14.8c1.6.8 2.7 2.6 3 5.2" /></Svg>,
  stethoscope: () => <Svg><path d="M5 3v5a5 5 0 0 0 10 0V3" /><path d="M10 13v2.5a4.5 4.5 0 0 0 9 0V13" /><circle cx="19" cy="11" r="2" /></Svg>,
  key: () => <Svg><circle cx="8" cy="15" r="4" /><path d="m11 12 9-9M17 6l3 3M14.5 8.5l2 2" /></Svg>,
  card: () => <Svg><rect x="2.5" y="5" width="19" height="14" rx="2" /><path d="M2.5 10h19M6 15h4" /></Svg>,
  building: () => <Svg><path d="M4 21V5a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2v16M16 9h2a2 2 0 0 1 2 2v10M2.5 21h19" /><path d="M8 7h4M8 11h4M8 15h4" /></Svg>,
  file: () => <Svg><path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z" /><path d="M14 3v5h5M9 13h6M9 17h4" /></Svg>,
  logout: () => <Svg><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9" /></Svg>,
  menu: () => <Svg size={20}><path d="M4 6h16M4 12h16M4 18h16" /></Svg>,
  close: () => <Svg size={20}><path d="M6 6l12 12M18 6 6 18" /></Svg>,
  search: () => <Svg size={16}><circle cx="11" cy="11" r="7" /><path d="m20 20-3.5-3.5" /></Svg>,
  check: () => <Svg size={16}><path d="m5 12.5 4.5 4.5L19 7.5" /></Svg>,
  pulse: () => <Svg size={22}><path d="M3 12h4l2.5-6 4 12 2.5-6H21" /></Svg>
}

/** ไอคอนประจำเมนูแต่ละหน้า */
export const ROUTE_ICON = {
  '/dashboard': Icon.dashboard,
  '/appointments': Icon.calendar,
  '/queue': Icon.queue,
  '/patients': Icon.users,
  '/doctors': Icon.stethoscope,
  '/accounts': Icon.key,
  '/billing': Icon.card,
  '/super-admin': Icon.building,
  '/portal': Icon.calendar,
  '/portal/book': Icon.calendarPlus,
  '/portal/records': Icon.file
}
