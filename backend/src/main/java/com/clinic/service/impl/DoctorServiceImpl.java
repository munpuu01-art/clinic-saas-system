package com.clinic.service.impl;

import com.clinic.domain.appointment.AppointmentType;
import com.clinic.domain.common.ContactInfo;
import com.clinic.domain.common.TimeSlot;
import com.clinic.domain.doctor.*;
import com.clinic.domain.person.Patient;
import com.clinic.dto.*;
import com.clinic.exception.ResourceNotFoundException;
import com.clinic.mapper.DomainMapper;
import com.clinic.repository.*;
import com.clinic.rules.BookingContext;
import com.clinic.rules.BookingRuleChain;
import com.clinic.service.DoctorService;
import com.clinic.service.TenantGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class DoctorServiceImpl implements DoctorService {

    private final DoctorRepository doctorRepository;
    private final SpecialtyRepository specialtyRepository;
    private final DoctorScheduleRepository scheduleRepository;
    private final PatientRepository patientRepository;
    private final BookingRuleChain ruleChain;
    private final DomainMapper mapper;
    private final TenantGuard tenantGuard;

    public DoctorServiceImpl(DoctorRepository doctorRepository,
                             SpecialtyRepository specialtyRepository,
                             DoctorScheduleRepository scheduleRepository,
                             PatientRepository patientRepository,
                             BookingRuleChain ruleChain,
                             DomainMapper mapper, TenantGuard tenantGuard) {
        this.doctorRepository = doctorRepository;
        this.specialtyRepository = specialtyRepository;
        this.scheduleRepository = scheduleRepository;
        this.patientRepository = patientRepository;
        this.ruleChain = ruleChain;
        this.mapper = mapper;
        this.tenantGuard = tenantGuard;
    }

    @Override
    public DoctorResponse create(DoctorRequest r) {
        Long clinicId = tenantGuard.requireCurrentClinicId();
        Specialty specialty = specialtyRepository.findByIdAndClinicId(r.specialtyId(), clinicId)
                .orElseThrow(() -> new ResourceNotFoundException("แผนก", r.specialtyId()));

        Doctor doctor = new Doctor(r.licenseNo(), specialty, r.firstName(), r.lastName(),
                r.gender(), r.birthDate(), r.nationalId(),
                new ContactInfo(r.phone(), r.email(), null));
        doctor.setClinicId(clinicId);
        doctor.setConsultationFee(r.consultationFee());
        doctor.setRoomNo(r.roomNo());
        doctor.setBiography(r.biography());

        return mapper.toDto(doctorRepository.save(doctor));
    }

    @Override
    @Transactional(readOnly = true)
    public DoctorResponse findById(Long id) { return mapper.toDto(getEntity(id)); }

    @Override
    @Transactional(readOnly = true)
    public List<DoctorResponse> findAll(Long specialtyId) {
        Long clinicId = tenantGuard.requireCurrentClinicId();
        return doctorRepository.findAvailableDoctors(clinicId, specialtyId).stream()
                .map(mapper::toDto).toList();
    }

    @Override
    public ScheduleResponse addSchedule(Long doctorId, ScheduleRequest r) {
        Doctor doctor = getEntity(doctorId);
        DoctorSchedule schedule = new DoctorSchedule(r.dayOfWeek(), r.startTime(), r.endTime(),
                r.slotMinutes() > 0 ? r.slotMinutes() : doctor.getSpecialty().getDefaultSlotMinutes(),
                r.roomNo() != null ? r.roomNo() : doctor.getRoomNo());
        schedule.setClinicId(doctor.getClinicId());
        if (r.capacityPerSlot() > 0) schedule.setCapacityPerSlot(r.capacityPerSlot());
        if (r.effectiveFrom() != null) schedule.setEffectiveFrom(r.effectiveFrom());
        schedule.setEffectiveTo(r.effectiveTo());

        doctor.addSchedule(schedule);
        doctorRepository.save(doctor);
        return mapper.toDto(schedule);
    }

    @Override
    public void removeSchedule(Long doctorId, Long scheduleId) {
        // ดึงแพทย์ผ่านคลินิกปัจจุบันก่อนเสมอ — ถ้า scheduleId เป็นของคลินิกอื่น
        // doctor.removeSchedule() จะหาไม่เจอในคอลเลกชันของแพทย์รายนี้แล้วไม่ทำอะไร (ปลอดภัยโดยธรรมชาติ)
        Doctor doctor = getEntity(doctorId);
        DoctorSchedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("ตารางออกตรวจ", scheduleId));
        doctor.removeSchedule(schedule);
        doctorRepository.save(doctor);
    }

    @Override
    public void addLeave(Long doctorId, LocalDate date, String reason) {
        Doctor doctor = getEntity(doctorId);
        DoctorLeave leave = DoctorLeave.fullDay(date, reason);
        leave.setClinicId(doctor.getClinicId());
        doctor.addLeave(leave);
        doctorRepository.save(doctor);
    }

    /**
     * สร้างรายการช่องเวลาของวันนั้น แล้วให้ "โซ่กฎ" ชุดเดียวกับตอนจองจริง
     * เป็นผู้ตัดสินว่าแต่ละช่องจองได้หรือไม่ — ข้อมูลบนหน้าจอจึงตรงกับผลการจองเสมอ
     */
    @Override
    @Transactional(readOnly = true)
    public List<SlotResponse> availableSlots(Long doctorId, LocalDate date, Long patientId) {
        Long clinicId = tenantGuard.requireCurrentClinicId();
        Doctor doctor = getEntity(doctorId);
        Patient patient = patientId == null ? null
                : patientRepository.findByIdAndClinicId(patientId, clinicId).orElse(null);

        List<SlotResponse> result = new ArrayList<>();
        for (DoctorSchedule schedule : doctor.getSchedules()) {
            if (!schedule.appliesOn(date)) continue;
            for (TimeSlot slot : schedule.generateSlots()) {
                String reason = null;
                boolean available;
                if (patient == null) {
                    available = doctor.isAvailableAt(date, slot);
                    if (!available) reason = "นอกเวลาออกตรวจ";
                } else {
                    BookingContext ctx = BookingContext.of(patient, doctor, date, slot,
                            AppointmentType.NEW_CASE);
                    try {
                        ruleChain.validate(ctx);
                        available = true;
                    } catch (RuntimeException ex) {
                        available = false;
                        reason = ex.getMessage();
                    }
                }
                result.add(new SlotResponse(slot.getStart(), slot.getEnd(), available, reason, schedule.getId()));
            }
        }
        result.sort(java.util.Comparator.comparing(SlotResponse::startTime));
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SpecialtyResponse> specialties() {
        Long clinicId = tenantGuard.requireCurrentClinicId();
        return specialtyRepository.findByClinicId(clinicId).stream().map(mapper::toDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Doctor getEntity(Long id) {
        Long clinicId = tenantGuard.requireCurrentClinicId();
        return doctorRepository.findByIdAndClinicId(id, clinicId)
                .orElseThrow(() -> new ResourceNotFoundException("แพทย์", id));
    }
}
