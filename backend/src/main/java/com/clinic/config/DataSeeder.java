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
import com.clinic.domain.tenant.ClinicStatus;
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
 * ข้อมูลตัวอย่างสำหรับ dev/demo — สร้าง "คลินิกตัวอย่าง" ขึ้นมาหนึ่งแห่งพร้อมข้อมูลครบ
 * เพื่อสาธิตว่าระบบเป็น Multi-tenant SaaS: ทุก record ในนี้ถูกผูกกับ clinic_id เดียวกัน
 * และมี SUPER_ADMIN แยกต่างหากที่มองเห็นได้ทุกคลินิก
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

        // ---------- SUPER_ADMIN: ไม่สังกัดคลินิกใด สร้างนอก TenantContext ----------
        createAccount("superadmin", Role.SUPER_ADMIN, null, "ผู้ดูแลระบบ SaaS", rawPassword, null);

        // ---------- คลินิกตัวอย่าง ----------
        Clinic clinic = clinicRepository.save(
                new Clinic("คลินิกใจดี", "jaidee-clinic", "owner@jaidee-clinic.test", "021234567"));
        clinic.activate();
        clinicRepository.save(clinic);

        Plan proPlan = plans.stream().filter(p -> p.getCode().equals("PRO")).findFirst().orElseThrow();
        Subscription subscription = new Subscription(clinic.getId(), proPlan, SubscriptionStatus.ACTIVE);
        subscription.renewPeriod(LocalDateTime.now(), LocalDateTime.now().plusMonths(1));
        subscriptionRepository.save(subscription);

        // ทุก record ต่อจากนี้ถูกสร้างภายใต้ TenantContext ของคลินิกนี้ (จำลองว่าล็อกอินอยู่ในคลินิกนี้)
        TenantContext.set(clinic.getId());
        try {
            seedClinicalData(clinic, rawPassword);
        } finally {
            TenantContext.clear();
        }

        System.out.println("""
            ---------------------------------------------------------------
             บัญชีตัวอย่าง (โปรไฟล์ dev) — รหัสผ่านทุกบัญชีคือ %s
               superadmin : ผู้ดูแลระบบ SaaS   เห็น/จัดการทุกคลินิก + แพ็กเกจ
               admin      : ผู้ดูแลคลินิกใจดี  เห็นทุกเมนู + จัดการบัญชีผู้ใช้
               staff      : เจ้าหน้าที่        จองนัด/คิว/การเงิน
               doctor     : แพทย์ สมชาย       คิว + เวชระเบียน
               piya       : ผู้ป่วย ปิยะ       พอร์ทัลผู้ป่วย จองนัดเอง (slug: %s)
            ---------------------------------------------------------------
            """.formatted(rawPassword, clinic.getSlug()));
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

    private void seedClinicalData(Clinic clinic, String rawPassword) {
        Specialty internal = specialtyRepository.save(
                new Specialty("INT", "อายุรกรรม", 20, BigDecimal.valueOf(600)));
        Specialty dental = specialtyRepository.save(
                new Specialty("DEN", "ทันตกรรม", 30, BigDecimal.valueOf(800)));
        Specialty pediatric = specialtyRepository.save(
                new Specialty("PED", "กุมารเวชกรรม", 20, BigDecimal.valueOf(700)));

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
    }

    private void createAccount(String username, Role role, Person person,
                               String displayName, String rawPassword, Long clinicId) {
        if (accountRepository.existsByUsernameIgnoreCase(username)) return;
        accountRepository.save(new UserAccount(username, passwordEncoder.encode(rawPassword),
                role, person, displayName, clinicId));
    }
}
