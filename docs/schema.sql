-- ============================================================
-- Clinic Appointment System — PostgreSQL schema (Multi-tenant SaaS)
-- ใช้ JOINED inheritance: person เป็นตารางแม่ของ patient / doctor / staff
-- ทุกตารางที่เป็นข้อมูลของคลินิก (ยกเว้น clinic, plan เอง) มีคอลัมน์ clinic_id กำกับ
-- ============================================================

DROP TABLE IF EXISTS subscription, plan, user_account, payment, invoice_item, invoice,
                     medical_record, queue_ticket, appointment, doctor_leave, doctor_schedule,
                     staff, doctor, patient, person, specialty, clinic CASCADE;

-- ---------- ผู้เช่าระบบ (Tenant) — คือคลินิกแต่ละแห่งที่สมัครใช้บริการ ----------
CREATE TABLE clinic (
    id             BIGSERIAL PRIMARY KEY,
    name           VARCHAR(150) NOT NULL,
    slug           VARCHAR(60)  NOT NULL UNIQUE,   -- ตัวระบุที่อ่านง่าย ใช้ตอนผู้ป่วยสมัครสมาชิก
    contact_email  VARCHAR(120) NOT NULL,
    contact_phone  VARCHAR(20),
    status         VARCHAR(20)  NOT NULL DEFAULT 'TRIALING',  -- TRIALING|ACTIVE|PAST_DUE|SUSPENDED|CANCELED
    trial_ends_at  TIMESTAMP,
    created_at     TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at     TIMESTAMP
);

-- ---------- แคตตาล็อกแพ็กเกจ (ของกลาง ไม่ผูกกับคลินิกไหน) ----------
CREATE TABLE plan (
    id                   BIGSERIAL PRIMARY KEY,
    code                 VARCHAR(30)  NOT NULL UNIQUE,   -- FREE | BASIC | PRO
    name                 VARCHAR(80)  NOT NULL,
    price_monthly_thb    NUMERIC(12,2) NOT NULL DEFAULT 0,
    max_doctors          INT,                             -- NULL = ไม่จำกัด
    max_active_patients  INT,                             -- NULL = ไม่จำกัด
    description          VARCHAR(500),
    stripe_price_id      VARCHAR(80),                      -- price_xxx จาก Stripe Dashboard
    active               BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at           TIMESTAMP
);

-- ---------- การสมัครสมาชิกของแต่ละคลินิก (1 คลินิก : 1 subscription) ----------
CREATE TABLE subscription (
    id                     BIGSERIAL PRIMARY KEY,
    clinic_id              BIGINT NOT NULL UNIQUE REFERENCES clinic(id) ON DELETE CASCADE,
    plan_id                BIGINT NOT NULL REFERENCES plan(id),
    status                 VARCHAR(20) NOT NULL,   -- TRIALING|ACTIVE|PAST_DUE|CANCELED|INCOMPLETE
    current_period_start   TIMESTAMP,
    current_period_end     TIMESTAMP,
    stripe_customer_id     VARCHAR(80),
    stripe_subscription_id VARCHAR(80),
    cancel_at_period_end   BOOLEAN NOT NULL DEFAULT FALSE,
    created_at             TIMESTAMP NOT NULL DEFAULT now(),
    updated_at             TIMESTAMP
);
CREATE INDEX idx_subscription_stripe_sub ON subscription(stripe_subscription_id);
CREATE INDEX idx_subscription_stripe_cus ON subscription(stripe_customer_id);

-- ---------- แผนก (ต่อคลินิก) ----------
CREATE TABLE specialty (
    id                    BIGSERIAL PRIMARY KEY,
    clinic_id             BIGINT NOT NULL REFERENCES clinic(id),
    code                  VARCHAR(20)  NOT NULL,
    name                  VARCHAR(120) NOT NULL,
    description           VARCHAR(500),
    default_slot_minutes  INT          NOT NULL DEFAULT 20,
    base_fee              NUMERIC(12,2) NOT NULL DEFAULT 0,
    created_at            TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at            TIMESTAMP,
    CONSTRAINT uk_specialty_code_per_clinic UNIQUE (clinic_id, code)
);

-- ---------- บุคคล (ตารางแม่ ต่อคลินิก) ----------
CREATE TABLE person (
    id           BIGSERIAL PRIMARY KEY,
    clinic_id    BIGINT REFERENCES clinic(id),
    first_name   VARCHAR(80)  NOT NULL,
    last_name    VARCHAR(80)  NOT NULL,
    gender       VARCHAR(10)  NOT NULL,
    birth_date   DATE,
    national_id  VARCHAR(20),
    phone        VARCHAR(20),
    email        VARCHAR(120),
    address_line VARCHAR(200),
    district     VARCHAR(80),
    province     VARCHAR(80),
    postcode     VARCHAR(10),
    created_at   TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at   TIMESTAMP
);
-- เลขบัตรประชาชนไม่ซ้ำ "ภายในคลินิกเดียวกัน" เท่านั้น (คนละคลินิกอาจมีผู้ป่วยคนเดียวกันมาก็ได้)
CREATE UNIQUE INDEX uk_person_national_id_per_clinic ON person(clinic_id, national_id)
    WHERE national_id IS NOT NULL;

CREATE TABLE patient (
    id                BIGINT PRIMARY KEY REFERENCES person(id) ON DELETE CASCADE,
    hn                VARCHAR(20) NOT NULL,
    blood_type        VARCHAR(5),
    allergies         VARCHAR(500),
    chronic_disease   VARCHAR(500),
    emergency_contact VARCHAR(200)
);

CREATE TABLE doctor (
    id               BIGINT PRIMARY KEY REFERENCES person(id) ON DELETE CASCADE,
    license_no       VARCHAR(40) NOT NULL,
    specialty_id     BIGINT      NOT NULL REFERENCES specialty(id),
    consultation_fee NUMERIC(12,2) NOT NULL DEFAULT 0,
    room_no          VARCHAR(20),
    biography        VARCHAR(1000),
    active           BOOLEAN     NOT NULL DEFAULT TRUE
);

CREATE TABLE staff (
    id          BIGINT PRIMARY KEY REFERENCES person(id) ON DELETE CASCADE,
    employee_no VARCHAR(30) NOT NULL,
    role        VARCHAR(30) NOT NULL,
    active      BOOLEAN     NOT NULL DEFAULT TRUE
);

-- ---------- ตารางออกตรวจ / วันลา (ต่อคลินิก) ----------
CREATE TABLE doctor_schedule (
    id                BIGSERIAL PRIMARY KEY,
    clinic_id         BIGINT NOT NULL REFERENCES clinic(id),
    doctor_id         BIGINT NOT NULL REFERENCES doctor(id) ON DELETE CASCADE,
    day_of_week       VARCHAR(12) NOT NULL,
    start_time        TIME   NOT NULL,
    end_time          TIME   NOT NULL,
    slot_minutes      INT    NOT NULL DEFAULT 20,
    capacity_per_slot INT    NOT NULL DEFAULT 1,
    room_no           VARCHAR(20),
    effective_from    DATE,
    effective_to      DATE,
    active            BOOLEAN NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMP NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP,
    CONSTRAINT uk_schedule UNIQUE (doctor_id, day_of_week, start_time),
    CONSTRAINT ck_schedule_time CHECK (end_time > start_time),
    CONSTRAINT ck_schedule_slot CHECK (slot_minutes >= 5)
);

CREATE TABLE doctor_leave (
    id         BIGSERIAL PRIMARY KEY,
    clinic_id  BIGINT NOT NULL REFERENCES clinic(id),
    doctor_id  BIGINT NOT NULL REFERENCES doctor(id) ON DELETE CASCADE,
    leave_date DATE   NOT NULL,
    full_day   BOOLEAN NOT NULL DEFAULT TRUE,
    start_time TIME,
    end_time   TIME,
    reason     VARCHAR(300),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP
);
CREATE INDEX idx_leave_doctor_date ON doctor_leave(doctor_id, leave_date);

-- ---------- นัดหมาย (ต่อคลินิก) ----------
CREATE TABLE appointment (
    id               BIGSERIAL PRIMARY KEY,
    clinic_id        BIGINT NOT NULL REFERENCES clinic(id),
    appointment_no   VARCHAR(30) NOT NULL,
    patient_id       BIGINT NOT NULL REFERENCES patient(id),
    doctor_id        BIGINT NOT NULL REFERENCES doctor(id),
    appointment_date DATE   NOT NULL,
    start_time       TIME   NOT NULL,
    end_time         TIME   NOT NULL,
    type             VARCHAR(20) NOT NULL,
    status           VARCHAR(20) NOT NULL,
    symptom_note     VARCHAR(1000),
    cancel_reason    VARCHAR(300),
    fee              NUMERIC(12,2) NOT NULL DEFAULT 0,
    created_by       VARCHAR(80),
    checked_in_at    TIMESTAMP,
    started_at       TIMESTAMP,
    completed_at     TIMESTAMP,
    created_at       TIMESTAMP NOT NULL DEFAULT now(),
    updated_at       TIMESTAMP,
    CONSTRAINT uk_appt_no_per_clinic UNIQUE (clinic_id, appointment_no),
    CONSTRAINT ck_appt_time CHECK (end_time > start_time)
);
CREATE INDEX idx_appt_clinic_date_status ON appointment(clinic_id, appointment_date, status);
CREATE INDEX idx_appt_doctor_date        ON appointment(doctor_id, appointment_date);
CREATE INDEX idx_appt_patient_date       ON appointment(patient_id, appointment_date);

-- กันจองชนเฉพาะนัดที่ยังไม่ถูกยกเลิก (partial unique index)
CREATE UNIQUE INDEX uk_appt_slot
    ON appointment(doctor_id, appointment_date, start_time)
    WHERE status NOT IN ('CANCELLED', 'NO_SHOW');

-- ---------- บัตรคิว (ต่อคลินิก) ----------
CREATE TABLE queue_ticket (
    id             BIGSERIAL PRIMARY KEY,
    clinic_id      BIGINT NOT NULL REFERENCES clinic(id),
    ticket_no      VARCHAR(20) NOT NULL,
    queue_date     DATE   NOT NULL,
    doctor_id      BIGINT NOT NULL REFERENCES doctor(id),
    patient_id     BIGINT NOT NULL REFERENCES patient(id),
    appointment_id BIGINT REFERENCES appointment(id),
    priority       VARCHAR(20) NOT NULL,
    status         VARCHAR(20) NOT NULL,
    sequence_no    INT    NOT NULL,
    issued_at      TIMESTAMP NOT NULL DEFAULT now(),
    called_at      TIMESTAMP,
    served_at      TIMESTAMP,
    completed_at   TIMESTAMP,
    counter_no     VARCHAR(20),
    symptom_note   VARCHAR(1000),
    created_at     TIMESTAMP NOT NULL DEFAULT now(),
    updated_at     TIMESTAMP,
    CONSTRAINT uk_ticket_sequence UNIQUE (doctor_id, queue_date, sequence_no)
);
CREATE INDEX idx_ticket_board ON queue_ticket(clinic_id, doctor_id, queue_date, status);

-- ---------- เวชระเบียน (ต่อคลินิก) ----------
CREATE TABLE medical_record (
    id             BIGSERIAL PRIMARY KEY,
    clinic_id      BIGINT NOT NULL REFERENCES clinic(id),
    appointment_id BIGINT NOT NULL UNIQUE REFERENCES appointment(id) ON DELETE CASCADE,
    patient_id     BIGINT NOT NULL REFERENCES patient(id),
    doctor_id      BIGINT NOT NULL REFERENCES doctor(id),
    symptoms       VARCHAR(1000),
    diagnosis      VARCHAR(1000),
    treatment      VARCHAR(1000),
    prescription   VARCHAR(1000),
    follow_up_date DATE,
    temperature    NUMERIC(5,2),
    systolic       INT,
    diastolic      INT,
    pulse          INT,
    weight_kg      NUMERIC(6,2),
    height_cm      NUMERIC(6,2),
    created_at     TIMESTAMP NOT NULL DEFAULT now(),
    updated_at     TIMESTAMP
);

-- ---------- การเงินของคลินิก (ต่อคลินิก) ----------
CREATE TABLE invoice (
    id             BIGSERIAL PRIMARY KEY,
    clinic_id      BIGINT NOT NULL REFERENCES clinic(id),
    invoice_no     VARCHAR(30) NOT NULL,
    appointment_id BIGINT REFERENCES appointment(id),
    patient_id     BIGINT NOT NULL REFERENCES patient(id),
    status         VARCHAR(20) NOT NULL,
    discount       NUMERIC(12,2) NOT NULL DEFAULT 0,
    issued_at      TIMESTAMP,
    paid_at        TIMESTAMP,
    created_at     TIMESTAMP NOT NULL DEFAULT now(),
    updated_at     TIMESTAMP,
    CONSTRAINT uk_invoice_no_per_clinic UNIQUE (clinic_id, invoice_no),
    CONSTRAINT ck_discount CHECK (discount >= 0)
);

CREATE TABLE invoice_item (
    id          BIGSERIAL PRIMARY KEY,
    invoice_id  BIGINT NOT NULL REFERENCES invoice(id) ON DELETE CASCADE,
    description VARCHAR(200) NOT NULL,
    quantity    INT NOT NULL DEFAULT 1,
    unit_price  NUMERIC(12,2) NOT NULL,
    CONSTRAINT ck_item_qty CHECK (quantity > 0)
);

CREATE TABLE payment (
    id           BIGSERIAL PRIMARY KEY,
    invoice_id   BIGINT NOT NULL REFERENCES invoice(id) ON DELETE CASCADE,
    amount       NUMERIC(12,2) NOT NULL,
    method       VARCHAR(20) NOT NULL,
    paid_at      TIMESTAMP NOT NULL DEFAULT now(),
    reference_no VARCHAR(60),
    CONSTRAINT ck_payment_amount CHECK (amount > 0)
);

-- ---------- บัญชีผู้ใช้งาน ----------
-- username ไม่ซ้ำกัน "ทั้งระบบ" โดยตั้งใจ (ล็อกอินได้โดยยังไม่ต้องรู้ว่าอยู่คลินิกไหน คล้าย Slack)
-- clinic_id เป็น NULL ได้เฉพาะบัญชี SUPER_ADMIN ของเจ้าของแพลตฟอร์มเท่านั้น
CREATE TABLE user_account (
    id                   BIGSERIAL PRIMARY KEY,
    clinic_id            BIGINT REFERENCES clinic(id),
    username             VARCHAR(60)  NOT NULL UNIQUE,
    password_hash        VARCHAR(120) NOT NULL,      -- BCrypt เท่านั้น ห้ามเก็บรหัสผ่านดิบ
    role                 VARCHAR(20)  NOT NULL,      -- SUPER_ADMIN | ADMIN | STAFF | DOCTOR | PATIENT
    person_id            BIGINT REFERENCES person(id),
    display_name         VARCHAR(120),
    active               BOOLEAN   NOT NULL DEFAULT TRUE,
    failed_attempts      INT       NOT NULL DEFAULT 0,
    locked_until         TIMESTAMP,
    last_login_at        TIMESTAMP,
    must_change_password BOOLEAN   NOT NULL DEFAULT FALSE,
    created_at           TIMESTAMP NOT NULL DEFAULT now(),
    updated_at           TIMESTAMP,
    CONSTRAINT uk_account_clinic_person UNIQUE (clinic_id, person_id)
);
CREATE INDEX idx_account_role ON user_account(role);
CREATE INDEX idx_account_clinic ON user_account(clinic_id);

-- ---------- ข้อมูลตั้งต้น: แคตตาล็อกแพ็กเกจ (ของกลาง สร้างก่อนมีคลินิกแรกด้วยซ้ำ) ----------
INSERT INTO plan (code, name, price_monthly_thb, max_doctors, max_active_patients, description) VALUES
  ('FREE',  'ฟรี',     0,    1,    50,  'เหมาะสำหรับทดลองใช้งาน แพทย์ 1 คน ผู้ป่วยไม่เกิน 50 ราย/เดือน'),
  ('BASIC', 'เริ่มต้น', 990,  3,    300, 'แพทย์ได้ถึง 3 คน ผู้ป่วยไม่เกิน 300 ราย/เดือน เหมาะกับคลินิกขนาดเล็ก'),
  ('PRO',   'โปร',     2990, NULL, NULL,'ไม่จำกัดจำนวนแพทย์และผู้ป่วย รองรับหลายห้องตรวจพร้อมกัน');

-- ---------- ข้อมูลตัวอย่าง: คลินิกสาธิตหนึ่งแห่งพร้อมแผนก ----------
INSERT INTO clinic (name, slug, contact_email, contact_phone, status) VALUES
  ('คลินิกใจดี', 'jaidee-clinic', 'owner@jaidee-clinic.test', '021234567', 'ACTIVE');

INSERT INTO specialty (clinic_id, code, name, default_slot_minutes, base_fee)
SELECT id, 'INT', 'อายุรกรรม', 20, 500 FROM clinic WHERE slug = 'jaidee-clinic'
UNION ALL
SELECT id, 'DEN', 'ทันตกรรม', 30, 800 FROM clinic WHERE slug = 'jaidee-clinic'
UNION ALL
SELECT id, 'PED', 'กุมารเวชกรรม', 20, 600 FROM clinic WHERE slug = 'jaidee-clinic';
