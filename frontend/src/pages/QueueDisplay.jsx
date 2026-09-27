import React, { useCallback, useEffect, useRef, useState } from 'react'
import { DoctorApi, QueueApi } from '../api/ApiClient.js'
import { useAuth } from '../auth/AuthContext.jsx'
import { today } from '../components/ui.jsx'

/**
 * จอแสดงคิวสำหรับทีวี/จอมอนิเตอร์หน้าห้องตรวจ — เปิดจากหน้า "คิวหน้าห้องตรวจ"
 *   /queue/display?doctor=5      แสดงห้องเดียว (ตัวเลขใหญ่เต็มจอ)
 *   /queue/display?doctor=all    แสดงทุกห้องพร้อมกัน
 *   &names=hide                  ไม่แสดงชื่อผู้ป่วยเลย (ค่าเริ่มต้นแสดงแบบปิดบางส่วน)
 * จอนี้อ่านข้อมูลอย่างเดียว ไม่มีปุ่มควบคุมคิว — การเรียกคิวยังทำที่เครื่องเจ้าหน้าที่
 */

const POLL_MS = 5000
const MAX_NEXT_SINGLE = 6
const MAX_NEXT_GRID = 3

const THAI_DIGIT = ['ศูนย์', 'หนึ่ง', 'สอง', 'สาม', 'สี่', 'ห้า', 'หก', 'เจ็ด', 'แปด', 'เก้า']
const LETTER = {
  A: 'เอ', B: 'บี', C: 'ซี', D: 'ดี', E: 'อี', F: 'เอฟ', G: 'จี', H: 'เอช', I: 'ไอ', J: 'เจ',
  K: 'เค', L: 'แอล', M: 'เอ็ม', N: 'เอ็น', O: 'โอ', P: 'พี', Q: 'คิว', R: 'อาร์', S: 'เอส',
  T: 'ที', U: 'ยู', V: 'วี', W: 'ดับเบิลยู', X: 'เอ็กซ์', Y: 'วาย', Z: 'แซด'
}
const LEADING_VOWELS = 'เแโใไ'

/** "A012" → "เอ ศูนย์ หนึ่ง สอง" ให้เสียงอ่านทีละตัวชัดเจน */
function speakable(ticketNo) {
  return [...ticketNo].map((ch) => (/\d/.test(ch) ? THAI_DIGIT[ch] : LETTER[ch.toUpperCase()] || ch)).join(' ')
}

/** ปิดบังชื่อบนจอสาธารณะ (PDPA): "สมหญิง ใจงาม" → "สมหญิง ใ." */
function maskName(name) {
  if (!name) return ''
  const [first, ...rest] = name.trim().split(/\s+/)
  const last = rest.join(' ')
  if (!last) return first
  const chars = [...last]
  const initial = LEADING_VOWELS.includes(chars[0]) ? chars.slice(0, 2).join('') : chars[0]
  return `${first} ${initial}.`
}

function useClock() {
  const [now, setNow] = useState(new Date())
  useEffect(() => {
    const timer = setInterval(() => setNow(new Date()), 1000)
    return () => clearInterval(timer)
  }, [])
  return now
}

export default function QueueDisplay() {
  const { session } = useAuth()
  const params = new URLSearchParams(window.location.search)
  const doctorParam = params.get('doctor') || 'all'
  const strategy = params.get('strategy') || undefined
  const hideNames = params.get('names') === 'hide'
  const isAll = doctorParam === 'all'

  const [rooms, setRooms] = useState([])          // [{ doctor, board }]
  const [offline, setOffline] = useState(false)
  const [started, setStarted] = useState(false)
  const [soundOn, setSoundOn] = useState(false)
  const now = useClock()

  const audioRef = useRef(null)
  const lastCalledRef = useRef(null)               // doctorId → "ticketId|calledAt"
  const announceChain = useRef(Promise.resolve())
  const soundRef = useRef(false)
  soundRef.current = soundOn

  // ---------- เสียงเรียกคิว ----------
  const chime = useCallback(() => new Promise((resolve) => {
    const ctx = audioRef.current
    if (!ctx) { resolve(); return }
    const t = ctx.currentTime
    ;[[880, 0], [659, 0.38]].forEach(([freq, delay]) => {
      const osc = ctx.createOscillator()
      const gain = ctx.createGain()
      osc.type = 'sine'
      osc.frequency.value = freq
      gain.gain.setValueAtTime(0.0001, t + delay)
      gain.gain.exponentialRampToValueAtTime(0.35, t + delay + 0.02)
      gain.gain.exponentialRampToValueAtTime(0.0001, t + delay + 0.9)
      osc.connect(gain).connect(ctx.destination)
      osc.start(t + delay)
      osc.stop(t + delay + 1)
    })
    setTimeout(resolve, 1300)
  }), [])

  const speak = useCallback((text) => new Promise((resolve) => {
    if (!('speechSynthesis' in window)) { resolve(); return }
    const utter = new SpeechSynthesisUtterance(text)
    utter.lang = 'th-TH'
    utter.rate = 0.9
    const thai = window.speechSynthesis.getVoices().find((v) => v.lang?.toLowerCase().startsWith('th'))
    if (thai) utter.voice = thai
    utter.onend = resolve
    utter.onerror = resolve
    window.speechSynthesis.speak(utter)
    setTimeout(resolve, 8000)                        // กันค้างถ้าเบราว์เซอร์ไม่ยิง onend
  }), [])

  const announce = useCallback((ticketNo, roomNo) => {
    if (!soundRef.current) return
    announceChain.current = announceChain.current
      .then(chime)
      .then(() => speak(`ขอเชิญหมายเลข ${speakable(ticketNo)} ที่ห้องตรวจ ${roomNo || ''}`))
  }, [chime, speak])

  // ---------- ดึงข้อมูล ----------
  const load = useCallback(async () => {
    try {
      const doctors = (await DoctorApi.list()).filter((d) => d.active !== false)
      const shown = isAll ? doctors : doctors.filter((d) => String(d.id) === doctorParam)
      const boards = await Promise.all(shown.map((d) => QueueApi.board(d.id, today(), strategy)))
      const next = shown.map((doctor, i) => ({ doctor, board: boards[i] }))

      // ประกาศเฉพาะคิวที่เพิ่งถูกเรียก (รวมการกด "เรียกซ้ำ" ซึ่งเปลี่ยน calledAt)
      const seen = lastCalledRef.current
      const current = {}
      next.forEach(({ doctor, board }) => {
        const t = board?.nowServing
        const key = t ? `${t.id}|${t.calledAt || ''}` : ''
        current[doctor.id] = key
        if (seen && t && t.status === 'CALLED' && seen[doctor.id] !== key) {
          announce(t.ticketNo, board.roomNo || doctor.roomNo)
        }
      })
      lastCalledRef.current = current

      setRooms(next)
      setOffline(false)
    } catch {
      setOffline(true)                               // เก็บข้อมูลล่าสุดไว้ แล้วลองใหม่รอบหน้า
    }
  }, [isAll, doctorParam, strategy, announce])

  useEffect(() => {
    load()
    const timer = setInterval(load, POLL_MS)
    return () => clearInterval(timer)
  }, [load])

  // เบราว์เซอร์อนุญาตให้เล่นเสียงได้หลังผู้ใช้คลิกเท่านั้น จึงต้องมีปุ่มเริ่มก่อน
  const start = async (withSound) => {
    if (withSound) {
      const Ctx = window.AudioContext || window.webkitAudioContext
      if (Ctx && !audioRef.current) audioRef.current = new Ctx()
      await audioRef.current?.resume?.()
      window.speechSynthesis?.getVoices()           // กระตุ้นให้โหลดรายชื่อเสียง
    }
    setSoundOn(withSound)
    setStarted(true)
    document.documentElement.requestFullscreen?.().catch(() => {})
  }

  const toggleSound = async () => {
    if (!soundOn) {
      const Ctx = window.AudioContext || window.webkitAudioContext
      if (Ctx && !audioRef.current) audioRef.current = new Ctx()
      await audioRef.current?.resume?.()
    }
    setSoundOn(!soundOn)
  }

  const nameOf = (t) => (hideNames ? '' : maskName(t.patientName))
  const waitingOf = (board) => (board?.waiting || []).filter((t) => t.status === 'WAITING')
  const headline = (t) => (t.status === 'CALLED' ? 'เชิญหมายเลข' : t.statusLabel)

  const timeText = now.toLocaleTimeString('th-TH', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
  const dateText = now.toLocaleDateString('th-TH', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })
  const single = !isAll ? rooms[0] : null

  return (
    <div className="tv">
      <header className="tv-top">
        <div className="tv-brand">
          <strong>{session?.clinicName || 'คลินิก'}</strong>
          <span>{isAll ? 'จอแสดงคิวทุกห้องตรวจ' : 'จอแสดงคิวหน้าห้องตรวจ'}</span>
        </div>
        <div className="tv-clock">
          <strong className="numeric">{timeText}</strong>
          <span>{dateText}</span>
        </div>
      </header>

      {rooms.length === 0 ? (
        <main className="tv-empty">{offline ? 'กำลังเชื่อมต่อเซิร์ฟเวอร์…' : 'ไม่พบห้องตรวจที่เปิดให้บริการ'}</main>
      ) : single ? (
        <main className="tv-single">
          <section className="tv-now">
            {single.board?.nowServing ? (
              <>
                <div className="tv-label">{headline(single.board.nowServing)}</div>
                <div className="tv-ticket numeric" key={single.board.nowServing.id + (single.board.nowServing.calledAt || '')}>
                  {single.board.nowServing.ticketNo}
                </div>
                <div className="tv-room">ห้องตรวจ {single.board.roomNo || single.doctor.roomNo || '-'}</div>
                <div className="tv-doctor">{single.doctor.displayName}</div>
                {nameOf(single.board.nowServing) && <div className="tv-patient">{nameOf(single.board.nowServing)}</div>}
              </>
            ) : (
              <>
                <div className="tv-label">ห้องตรวจ {single.board?.roomNo || single.doctor.roomNo || '-'}</div>
                <div className="tv-ticket idle">—</div>
                <div className="tv-doctor">{single.doctor.displayName}</div>
                <div className="tv-patient">ยังไม่มีการเรียกคิว</div>
              </>
            )}
          </section>
          <aside className="tv-next">
            <h2>คิวถัดไป</h2>
            {waitingOf(single.board).length === 0 ? (
              <p className="tv-muted">ไม่มีคิวรอ</p>
            ) : (
              <ol>
                {waitingOf(single.board).slice(0, MAX_NEXT_SINGLE).map((t) => (
                  <li key={t.id}>
                    <span className="tv-no numeric">{t.ticketNo}</span>
                    {t.priority === 'EMERGENCY' && <span className="tv-tag red">ฉุกเฉิน</span>}
                    {t.priority === 'ELDERLY' && <span className="tv-tag amber">ผู้สูงอายุ</span>}
                  </li>
                ))}
              </ol>
            )}
            <div className="tv-stats">
              <div><span>รอคิว</span><strong className="numeric">{single.board?.waitingCount ?? 0}</strong></div>
              <div><span>รอประมาณ</span><strong className="numeric">{single.board?.estimatedWaitMinutes ?? 0} นาที</strong></div>
            </div>
          </aside>
        </main>
      ) : (
        <main className={`tv-grid count-${Math.min(rooms.length, 6)}`}>
          {rooms.map(({ doctor, board }) => {
            const t = board?.nowServing
            const nextList = waitingOf(board).slice(0, MAX_NEXT_GRID)
            return (
              <section key={doctor.id} className={`tv-card ${t?.status === 'CALLED' ? 'calling' : ''}`}>
                <div className="tv-card-head">
                  <strong>ห้องตรวจ {board?.roomNo || doctor.roomNo || '-'}</strong>
                  <span>{doctor.displayName}</span>
                </div>
                <div className="tv-card-label">{t ? headline(t) : 'ยังไม่มีการเรียกคิว'}</div>
                <div className="tv-card-ticket numeric" key={t ? t.id + (t.calledAt || '') : 'none'}>
                  {t ? t.ticketNo : '—'}
                </div>
                <div className="tv-card-next">
                  <span>ถัดไป</span>
                  {nextList.length === 0 ? <em>ไม่มีคิวรอ</em>
                    : nextList.map((n) => <b key={n.id} className="numeric">{n.ticketNo}</b>)}
                </div>
              </section>
            )
          })}
        </main>
      )}

      <footer className="tv-bottom">
        <span>กรุณารอเรียกหมายเลขของท่าน และเตรียมบัตรประชาชนให้พร้อม</span>
        <span className="tv-bottom-side">
          {offline && <span className="tv-offline">ขาดการเชื่อมต่อ กำลังลองใหม่…</span>}
          <button className="tv-sound" onClick={toggleSound}>{soundOn ? '🔊 เสียงเปิด' : '🔇 เสียงปิด'}</button>
        </span>
      </footer>

      {!started && (
        <div className="tv-start">
          <div className="tv-start-card">
            <h1>จอแสดงคิว</h1>
            <p>ลากหน้าต่างนี้ไปที่จอทีวีหรือจอที่สอง แล้วกดเริ่ม ระบบจะขยายเต็มจอและอัปเดตคิวทุก {POLL_MS / 1000} วินาที</p>
            <div className="tv-start-actions">
              <button className="primary" onClick={() => start(true)}>🔊 เริ่มแสดงผล พร้อมเสียงเรียกคิว</button>
              <button onClick={() => start(false)}>เริ่มแบบไม่มีเสียง</button>
            </div>
            <p className="tv-start-hint">กด Esc เพื่อออกจากโหมดเต็มจอ</p>
          </div>
        </div>
      )}
    </div>
  )
}
