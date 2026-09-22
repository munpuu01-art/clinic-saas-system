package com.clinic.service.impl;

import com.clinic.domain.appointment.Appointment;
import com.clinic.domain.appointment.AppointmentStatus;
import com.clinic.domain.medical.MedicalRecord;
import com.clinic.domain.medical.Vitals;
import com.clinic.dto.MedicalRecordRequest;
import com.clinic.dto.MedicalRecordResponse;
import com.clinic.exception.BusinessRuleException;
import com.clinic.exception.ResourceNotFoundException;
import com.clinic.mapper.DomainMapper;
import com.clinic.repository.AppointmentRepository;
import com.clinic.repository.MedicalRecordRepository;
import com.clinic.service.MedicalRecordService;
import com.clinic.service.TenantGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class MedicalRecordServiceImpl implements MedicalRecordService {

    private final MedicalRecordRepository recordRepository;
    private final AppointmentRepository appointmentRepository;
    private final DomainMapper mapper;
    private final TenantGuard tenantGuard;

    public MedicalRecordServiceImpl(MedicalRecordRepository recordRepository,
                                    AppointmentRepository appointmentRepository,
                                    DomainMapper mapper, TenantGuard tenantGuard) {
        this.recordRepository = recordRepository;
        this.appointmentRepository = appointmentRepository;
        this.mapper = mapper;
        this.tenantGuard = tenantGuard;
    }

    /** บันทึกผลตรวจได้เฉพาะนัดที่กำลังตรวจหรือตรวจเสร็จแล้ว */
    @Override
    public MedicalRecordResponse saveForAppointment(Long appointmentId, MedicalRecordRequest r) {
        Long clinicId = tenantGuard.requireCurrentClinicId();
        Appointment appointment = appointmentRepository.findByIdAndClinicId(appointmentId, clinicId)
                .orElseThrow(() -> new ResourceNotFoundException("นัดหมาย", appointmentId));

        if (appointment.getStatus() != AppointmentStatus.IN_PROGRESS
                && appointment.getStatus() != AppointmentStatus.COMPLETED) {
            throw new BusinessRuleException("RECORD_NOT_ALLOWED",
                    "บันทึกผลตรวจได้เมื่อเริ่มตรวจแล้วเท่านั้น (สถานะปัจจุบัน: "
                            + appointment.getStatus().getLabel() + ")");
        }

        MedicalRecord record = recordRepository.findByClinicIdAndAppointmentId(clinicId, appointmentId)
                .orElseGet(() -> {
                    MedicalRecord created = new MedicalRecord(appointment, r.chiefComplaint());
                    created.setClinicId(clinicId);
                    return created;
                });

        record.setChiefComplaint(r.chiefComplaint());
        record.setDiagnosis(r.diagnosis());
        record.setTreatment(r.treatment());
        record.setPrescription(r.prescription());
        record.setFollowUpDate(r.followUpDate());
        record.setVitals(new Vitals(r.temperatureC(), r.systolic(), r.diastolic(),
                r.pulse(), r.weightKg(), r.heightCm()));

        return mapper.toDto(recordRepository.save(record));
    }

    @Override
    @Transactional(readOnly = true)
    public MedicalRecordResponse findByAppointment(Long appointmentId) {
        Long clinicId = tenantGuard.requireCurrentClinicId();
        return recordRepository.findByClinicIdAndAppointmentId(clinicId, appointmentId).map(mapper::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("เวชระเบียนของนัดหมาย", appointmentId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<MedicalRecordResponse> historyOfPatient(Long patientId) {
        Long clinicId = tenantGuard.requireCurrentClinicId();
        return recordRepository.findByClinicIdAndPatientIdOrderByCreatedAtDesc(clinicId, patientId)
                .stream().map(mapper::toDto).toList();
    }
}
