package com.clinic.config;

import com.clinic.domain.auth.Role;
import com.clinic.domain.auth.UserAccount;
import com.clinic.domain.billing.Plan;
import com.clinic.domain.billing.Subscription;
import com.clinic.domain.billing.SubscriptionStatus;
import com.clinic.domain.common.ContactInfo;
import com.clinic.domain.common.Gender;
import com.clinic.domain.doctor.Doctor;
import com.clinic.domain.doctor.DoctorSchedule;
import com.clinic.domain.doctor.Specialty;
import com.clinic.domain.person.Patient;
import com.clinic.domain.person.Person;
import com.clinic.domain.person.Staff;
import com.clinic.domain.person.StaffRole;
import com.clinic.domain.tenant.Clinic;
import com.clinic.domain.tenant.TenantContext;
import com.clinic.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * ข้อมูลตัวอย่างสำหรับ dev/demo — สร้าง "คลินิกตัวอย่าง" 4 แห่ง แต่ละแห่งมีแพ็กเกจและ
 * สถานะสมาชิกต่างกัน เพื่อสาธิตว่าระบบเป็น Multi-tenant SaaS อย่างสมบูรณ์: ข้อมูลของ
 * แต่ละคลินิกแยกขาดจากกัน และหน้า Super Admin เห็นภาพรวมความหลากหลายของลูกค้าจริง
 * มี SUPER_ADMIN แยกต่างหากที่มองเห็นได้ทุกคลินิก
 */
@Component
@Profile({"dev", "prod"})
public class DataSeeder implements CommandLineRunner {

    private final ClinicRepository clinicRepository;
    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SpecialtyRepository specialtyRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final StaffRepository staffRepository;
    private final UserAccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final ClinicProperties properties;

    public DataSeeder(ClinicRepository clinicRepository, PlanRepository planRepository,
                      SubscriptionRepository subscriptionRepository,
                      SpecialtyRepository specialtyRepository, DoctorRepository doctorRepository,
                      PatientRepository patientRepository, StaffRepository staffRepository,
                      UserAccountRepository accountRepository, PasswordEncoder passwordEncoder,
                      ClinicProperties properties) {
        this.clinicRepository = clinicRepository;
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.specialtyRepository = specialtyRepository;
        this.doctorRepository = doctorRepository;
        this.patientRepository = patientRepository;
        this.staffRepository = staffRepository;
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    public void run(String... args) {
        List<Plan> plans = seedPlans();

        if (clinicRepository.count() > 0) return;   // เคย seed ไปแล้ว ไม่ทำซ้ำ

        String rawPassword = properties.getSecurity().getSeedDefaultPassword();
        Plan free = planOf(plans, "FREE");
        Plan basic = planOf(plans, "BASIC");
        Plan pro = planOf(plans, "PRO");

        // ---------- SUPER_ADMIN: ไม่สังกัดคลินิกใด สร้างนอก TenantContext ----------
        createAccount("superadmin", Role.SUPER_ADMIN, null, "ผู้ดูแลระบบ SaaS", rawPassword, null);

        seedJaideeClinic(pro, rawPassword);
        seedMordeeClinic(basic, rawPassword);
        seedRaksukClinic(free, rawPassword);
        seedYimsuayClinic(basic, rawPassword);

        System.out.println("""
            ---------------------------------------------------------------
             บัญชีตัวอย่าง (โปรไฟล์ dev) — รหัสผ่านทุกบัญชีคือ %s
             superadmin : ผู้ดูแลระบบ SaaS เห็น/จัดการทุกคลินิก + แพ็กเกจ

             1) คลินิกใจดี (jaidee-clinic) — แพ็กเกจ PRO · ใช้งานปกติ
                admin / staff / doctor / doctor2 / piya / somying

             2) คลินิกหมอดี (mordee-clinic) — แพ็กเกจ BASIC · ใช้งานปกติ
                mordee_admin / mordee_staff / mordee_doctor / mordee_patient

             3) คลินิกรักษ์สุขภาพ (raksuk-clinic) — แพ็กเกจ FREE · ทดลองใช้งาน
                raksuk_admin / raksuk_staff / raksuk_doctor / raksuk_patient

             4) คลินิกทันตกรรมยิ้มสวย (yimsuay-dental) — แพ็กเกจ BASIC · ถูกระงับ (สาธิต)
                yimsuay_admin / yimsuay_staff / yimsuay_doctor / yimsuay_patient
                (บัญชีนี้ล็อกอินไม่ได้โดยตั้งใจ — สาธิตการปฏิเสธคลินิกที่ถูกระงับ)
            ---------------------------------------------------------------
            """.formatted(rawPassword));
    }

    /** แคตตาล็อกแพ็กเกจ — เป็นของกลาง ไม่ผูกกับคลินิกไหน สร้างครั้งเดียวไว้ก่อนมีคลินิกแรกด้วยซ้ำ */
    private List<Plan> seedPlans() {
        if (planRepository.count() > 0) return planRepository.findAll();
        List<Plan> plans = List.of(
                new Plan("FREE", "ฟรี", BigDecimal.ZERO, 1, 50,
                        "เหมาะสำหรับทดลองใช้งาน แพทย์ 1 คน ผู้ป่วยไม่เกิน 50 ราย/เดือน", null),
                new Plan("BASIC", "เริ่มต้น", BigDecimal.valueOf(990), 3, 300,
                        "แพทย์ได้ถึง 3 คน ผู้ป่วยไม่เกิน 300 ราย/เดือน เหมาะกับคลินิกขนาดเล็ก",
                        System.getenv("STRIPE_PRICE_BASIC")),
                new Plan("PRO", "โปร", BigDecimal.valueOf(2990), null, null,
                        "ไม่จำกัดจำนวนแพทย์และผู้ป่วย รองรับหลายห้องตรวจพร้อมกัน",
                        System.getenv("STRIPE_PRICE_PRO"))
        );
        return planRepository.saveAll(plans);
    }

    private Plan planOf(List<Plan> plans, String code) {
        return plans.stream().filter(p -> p.getCode().equals(code)).findFirst().orElseThrow();
    }

    /** สร้าง Clinic + Subscription ตามสถานะที่กำหนด คืนค่า Clinic ที่บันทึกแล้ว */
    private Clinic createClinicWithSubscription(String name, String slug, String email, String phone,
                                                Plan plan, ClinicSubStatus status) {
        Clinic clinic = clinicRepository.save(new Clinic(name, slug, email, phone));
        if (status == ClinicSubStatus.ACTIVE) clinic.activate();
        else if (status == ClinicSubStatus.SUSPENDED) clinic.suspend();
        // TRIALING ใช้สถานะเริ่มต้นจาก constructor อยู่แล้ว ไม่ต้องเปลี่ยน
        clinicRepository.save(clinic);

        SubscriptionStatus subStatus = switch (status) {
            case ACTIVE -> SubscriptionStatus.ACTIVE;
            case SUSPENDED -> SubscriptionStatus.CANCELED;
            case TRIALING -> SubscriptionStatus.TRIALING;
        };
        Subscription subscription = new Subscription(clinic.getId(), plan, subStatus);
        subscription.renewPeriod(LocalDateTime.now(),
                status == ClinicSubStatus.TRIALING ? clinic.getTrialEndsAt() : LocalDateTime.now().plusMonths(1));
        subscriptionRepository.save(subscription);
        return clinic;
    }

    private enum ClinicSubStatus { TRIALING, ACTIVE, SUSPENDED }

    /** ---------- คลินิกที่ 1: คลินิกใจดี — แพ็กเกจ PRO ใช้งานปกติ (ชุดข้อมูลเดิม) ---------- */
    private void seedJaideeClinic(Plan pro, String rawPassword) {
        Clinic clinic = createClinicWithSubscription("คลินิกใจดี", "jaidee-clinic",
                "owner@jaidee-clinic.test", "021234567", pro, ClinicSubStatus.ACTIVE);

        TenantContext.set(clinic.getId());
        try {
            Specialty internal = specialtyRepository.save(new Specialty("INT", "อายุรกรรม", 20, BigDecimal.valueOf(600)));
            Specialty dental = specialtyRepository.save(new Specialty("DEN", "ทันตกรรม", 30, BigDecimal.valueOf(800)));
            Specialty pediatric = specialtyRepository.save(new Specialty("PED", "กุมารเวชกรรม", 20, BigDecimal.valueOf(700)));

            Doctor somchai = new Doctor("ว.12345", internal, "สมชาย", "ใจดี", Gender.MALE,
                    LocalDate.of(1980, 4, 12), "1100100100101",
                    new ContactInfo("0812345678", "somchai@clinic.test", null));
            somchai.setRoomNo("A1");
            somchai.addSchedule(new DoctorSchedule(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(12, 0), 20, "A1"));
            somchai.addSchedule(new DoctorSchedule(DayOfWeek.WEDNESDAY, LocalTime.of(13, 0), LocalTime.of(16, 0), 20, "A1"));
            somchai.addSchedule(new DoctorSchedule(DayOfWeek.FRIDAY, LocalTime.of(9, 0), LocalTime.of(12, 0), 20, "A1"));

            Doctor malee = new Doctor("ว.22334", dental, "มาลี", "ฟันสวย", Gender.FEMALE,
                    LocalDate.of(1985, 8, 3), "1100100100102",
                    new ContactInfo("0823456789", "malee@clinic.test", null));
            malee.setRoomNo("B2");
            malee.addSchedule(new DoctorSchedule(DayOfWeek.TUESDAY, LocalTime.of(9, 0), LocalTime.of(12, 0), 30, "B2"));
            malee.addSchedule(new DoctorSchedule(DayOfWeek.THURSDAY, LocalTime.of(9, 0), LocalTime.of(12, 0), 30, "B2"));

            Doctor anan = new Doctor("ว.33445", pediatric, "อนันต์", "รักเด็ก", Gender.MALE,
                    LocalDate.of(1978, 1, 20), "1100100100103",
                    new ContactInfo("0834567890", "anan@clinic.test", null));
            anan.setRoomNo("C3");
            anan.addSchedule(new DoctorSchedule(DayOfWeek.MONDAY, LocalTime.of(13, 0), LocalTime.of(16, 0), 20, "C3"));
            anan.addSchedule(new DoctorSchedule(DayOfWeek.SATURDAY, LocalTime.of(9, 0), LocalTime.of(12, 0), 20, "C3"));

            doctorRepository.save(somchai);
            doctorRepository.save(malee);
            doctorRepository.save(anan);

            Patient p1 = new Patient("HN-2026-0001", "ปิยะ", "ตั้งใจดี", Gender.MALE,
                    LocalDate.of(1992, 6, 15), "1200100100201",
                    new ContactInfo("0891112222", "piya@mail.test", null));
            p1.setBloodType("O");
            p1.addAllergy("Penicillin");

            Patient p2 = new Patient("HN-2026-0002", "สมหญิง", "มีสุข", Gender.FEMALE,
                    LocalDate.of(1958, 2, 2), "1200100100202",
                    new ContactInfo("0892223333", "somying@mail.test", null));
            p2.setChronicDisease("เบาหวาน");

            Patient p3 = new Patient("HN-2026-0003", "ธนกร", "แข็งแรง", Gender.MALE,
                    LocalDate.of(2015, 11, 9), "1200100100203",
                    new ContactInfo("0893334444", null, null));

            patientRepository.save(p1);
            patientRepository.save(p2);
            patientRepository.save(p3);

            Staff kamon = staffRepository.save(new Staff("ST-001", StaffRole.RECEPTIONIST, "กมล", "ยิ้มแย้ม",
                    Gender.FEMALE, LocalDate.of(1995, 3, 3), "1300100100301",
                    new ContactInfo("0895556666", "kamon@clinic.test", null)));

            Long clinicId = clinic.getId();
            createAccount("admin", Role.ADMIN, null, "ผู้ดูแลระบบ", rawPassword, clinicId);
            createAccount("staff", Role.STAFF, kamon, null, rawPassword, clinicId);
            createAccount("doctor", Role.DOCTOR, somchai, null, rawPassword, clinicId);
            createAccount("doctor2", Role.DOCTOR, malee, null, rawPassword, clinicId);
            createAccount("piya", Role.PATIENT, p1, null, rawPassword, clinicId);
            createAccount("somying", Role.PATIENT, p2, null, rawPassword, clinicId);
        } finally {
            TenantContext.clear();
        }
    }

    /** ---------- คลินิกที่ 2: คลินิกหมอดี — แพ็กเกจ BASIC ใช้งานปกติ ---------- */
    private void seedMordeeClinic(Plan basic, String rawPassword) {
        Clinic clinic = createClinicWithSubscription("คลินิกหมอดี", "mordee-clinic",
                "owner@mordee-clinic.test", "022345678", basic, ClinicSubStatus.ACTIVE);

        TenantContext.set(clinic.getId());
        try {
            Specialty surgery = specialtyRepository.save(new Specialty("SUR", "ศัลยกรรม", 30, BigDecimal.valueOf(1200)));
            Specialty ent = specialtyRepository.save(new Specialty("ENT", "โสต ศอ นาสิก", 20, BigDecimal.valueOf(650)));

            Doctor wichai = new Doctor("ว.44556", surgery, "วิชัย", "แข็งแกร่ง", Gender.MALE,
                    LocalDate.of(1975, 9, 10), "1100100100201",
                    new ContactInfo("0845556677", "wichai@mordee.test", null));
            wichai.setRoomNo("S1");
            wichai.addSchedule(new DoctorSchedule(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(12, 0), 30, "S1"));
            wichai.addSchedule(new DoctorSchedule(DayOfWeek.THURSDAY, LocalTime.of(13, 0), LocalTime.of(16, 0), 30, "S1"));

            Doctor napa = new Doctor("ว.55667", ent, "นภา", "ใสสะอาด", Gender.FEMALE,
                    LocalDate.of(1988, 5, 22), "1100100100202",
                    new ContactInfo("0856667788", "napa@mordee.test", null));
            napa.setRoomNo("S2");
            napa.addSchedule(new DoctorSchedule(DayOfWeek.WEDNESDAY, LocalTime.of(9, 0), LocalTime.of(12, 0), 20, "S2"));
            napa.addSchedule(new DoctorSchedule(DayOfWeek.FRIDAY, LocalTime.of(13, 0), LocalTime.of(16, 0), 20, "S2"));

            doctorRepository.save(wichai);
            doctorRepository.save(napa);

            Patient q1 = new Patient("HN-2026-0001", "อรุณ", "รุ่งเรือง", Gender.MALE,
                    LocalDate.of(1990, 3, 18), "1200100100301",
                    new ContactInfo("0861112222", "arun@mail.test", null));
            Patient q2 = new Patient("HN-2026-0002", "วารี", "ใจเย็น", Gender.FEMALE,
                    LocalDate.of(1965, 7, 7), "1200100100302",
                    new ContactInfo("0862223333", "waree@mail.test", null));
            q2.addAllergy("Sulfa");

            patientRepository.save(q1);
            patientRepository.save(q2);

            Staff nid = staffRepository.save(new Staff("ST-001", StaffRole.RECEPTIONIST, "นิด", "ขยันดี",
                    Gender.FEMALE, LocalDate.of(1992, 12, 1), "1300100100401",
                    new ContactInfo("0865556666", "nid@mordee.test", null)));

            Long clinicId = clinic.getId();
            createAccount("mordee_admin", Role.ADMIN, null, "ผู้ดูแลคลินิกหมอดี", rawPassword, clinicId);
            createAccount("mordee_staff", Role.STAFF, nid, null, rawPassword, clinicId);
            createAccount("mordee_doctor", Role.DOCTOR, wichai, null, rawPassword, clinicId);
            createAccount("mordee_patient", Role.PATIENT, q1, null, rawPassword, clinicId);
        } finally {
            TenantContext.clear();
        }
    }

    /** ---------- คลินิกที่ 3: คลินิกรักษ์สุขภาพ — แพ็กเกจ FREE อยู่ระหว่างทดลองใช้งาน ---------- */
    private void seedRaksukClinic(Plan free, String rawPassword) {
        Clinic clinic = createClinicWithSubscription("คลินิกรักษ์สุขภาพ", "raksuk-clinic",
                "owner@raksuk-clinic.test", "023456789", free, ClinicSubStatus.TRIALING);

        TenantContext.set(clinic.getId());
        try {
            Specialty family = specialtyRepository.save(new Specialty("FAM", "เวชศาสตร์ครอบครัว", 20, BigDecimal.valueOf(500)));

            Doctor prasert = new Doctor("ว.66778", family, "ประเสริฐ", "ห่วงใย", Gender.MALE,
                    LocalDate.of(1982, 2, 14), "1100100100301",
                    new ContactInfo("0871112222", "prasert@raksuk.test", null));
            prasert.setRoomNo("F1");
            prasert.addSchedule(new DoctorSchedule(DayOfWeek.TUESDAY, LocalTime.of(9, 0), LocalTime.of(12, 0), 20, "F1"));
            prasert.addSchedule(new DoctorSchedule(DayOfWeek.FRIDAY, LocalTime.of(9, 0), LocalTime.of(12, 0), 20, "F1"));

            doctorRepository.save(prasert);

            Patient r1 = new Patient("HN-2026-0001", "มานพ", "สุขสันต์", Gender.MALE,
                    LocalDate.of(1995, 10, 5), "1200100100401",
                    new ContactInfo("0881112222", "manop@mail.test", null));
            Patient r2 = new Patient("HN-2026-0002", "ศศิธร", "แจ่มใส", Gender.FEMALE,
                    LocalDate.of(1970, 4, 30), "1200100100402",
                    new ContactInfo("0882223333", "sasithorn@mail.test", null));
            r2.setChronicDisease("ความดันโลหิตสูง");

            patientRepository.save(r1);
            patientRepository.save(r2);

            Staff dao = staffRepository.save(new Staff("ST-001", StaffRole.RECEPTIONIST, "ดาว", "พราวแสง",
                    Gender.FEMALE, LocalDate.of(1998, 6, 20), "1300100100501",
                    new ContactInfo("0885556666", "dao@raksuk.test", null)));

            Long clinicId = clinic.getId();
            createAccount("raksuk_admin", Role.ADMIN, null, "ผู้ดูแลคลินิกรักษ์สุขภาพ", rawPassword, clinicId);
            createAccount("raksuk_staff", Role.STAFF, dao, null, rawPassword, clinicId);
            createAccount("raksuk_doctor", Role.DOCTOR, prasert, null, rawPassword, clinicId);
            createAccount("raksuk_patient", Role.PATIENT, r1, null, rawPassword, clinicId);
        } finally {
            TenantContext.clear();
        }
    }

    /** ---------- คลินิกที่ 4: คลินิกทันตกรรมยิ้มสวย — ถูกระงับ (สาธิตการปฏิเสธคลินิกที่ระงับ) ---------- */
    private void seedYimsuayClinic(Plan basic, String rawPassword) {
        Clinic clinic = createClinicWithSubscription("คลินิกทันตกรรมยิ้มสวย", "yimsuay-dental",
                "owner@yimsuay-dental.test", "024567890", basic, ClinicSubStatus.SUSPENDED);

        TenantContext.set(clinic.getId());
        try {
            Specialty dental = specialtyRepository.save(new Specialty("DEN", "ทันตกรรม", 30, BigDecimal.valueOf(900)));

            Doctor somrudee = new Doctor("ว.77889", dental, "สมฤดี", "ยิ้มสวย", Gender.FEMALE,
                    LocalDate.of(1983, 11, 11), "1100100100401",
                    new ContactInfo("0891112222", "somrudee@yimsuay.test", null));
            somrudee.setRoomNo("D1");
            somrudee.addSchedule(new DoctorSchedule(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(12, 0), 30, "D1"));

            doctorRepository.save(somrudee);

            Patient s1 = new Patient("HN-2026-0001", "กิตติ", "หล่อเหลา", Gender.MALE,
                    LocalDate.of(1993, 8, 8), "1200100100501",
                    new ContactInfo("0891113333", "kitti@mail.test", null));
            Patient s2 = new Patient("HN-2026-0002", "รัตนา", "งามตา", Gender.FEMALE,
                    LocalDate.of(1980, 1, 15), "1200100100502",
                    new ContactInfo("0892224444", "rattana@mail.test", null));

            patientRepository.save(s1);
            patientRepository.save(s2);

            Staff fon = staffRepository.save(new Staff("ST-001", StaffRole.RECEPTIONIST, "ฝน", "ชื่นบาน",
                    Gender.FEMALE, LocalDate.of(1997, 9, 9), "1300100100601",
                    new ContactInfo("0895557777", "fon@yimsuay.test", null)));

            Long clinicId = clinic.getId();
            createAccount("yimsuay_admin", Role.ADMIN, null, "ผู้ดูแลคลินิกยิ้มสวย", rawPassword, clinicId);
            createAccount("yimsuay_staff", Role.STAFF, fon, null, rawPassword, clinicId);
            createAccount("yimsuay_doctor", Role.DOCTOR, somrudee, null, rawPassword, clinicId);
            createAccount("yimsuay_patient", Role.PATIENT, s1, null, rawPassword, clinicId);
        } finally {
            TenantContext.clear();
        }
    }

    private void createAccount(String username, Role role, Person person,
                               String displayName, String rawPassword, Long clinicId) {
        if (accountRepository.existsByUsernameIgnoreCase(username)) return;
        accountRepository.save(new UserAccount(username, passwordEncoder.encode(rawPassword),
                role, person, displayName, clinicId));
    }
}
