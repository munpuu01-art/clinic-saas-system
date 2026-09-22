package com.clinic.service.impl;

import com.clinic.domain.common.Address;
import com.clinic.domain.common.ContactInfo;
import com.clinic.domain.person.Patient;
import com.clinic.dto.PatientRequest;
import com.clinic.dto.PatientResponse;
import com.clinic.exception.BusinessRuleException;
import com.clinic.exception.ResourceNotFoundException;
import com.clinic.factory.DocumentNumberGenerator;
import com.clinic.mapper.DomainMapper;
import com.clinic.repository.PatientRepository;
import com.clinic.service.PatientService;
import com.clinic.service.TenantGuard;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PatientServiceImpl implements PatientService {

    private final PatientRepository patientRepository;
    private final DocumentNumberGenerator numberGenerator;
    private final DomainMapper mapper;
    private final TenantGuard tenantGuard;

    /** Constructor Injection — ทดสอบง่าย และบอกชัดว่า service นี้พึ่งพาอะไรบ้าง */
    public PatientServiceImpl(PatientRepository patientRepository,
                              DocumentNumberGenerator numberGenerator,
                              DomainMapper mapper, TenantGuard tenantGuard) {
        this.patientRepository = patientRepository;
        this.numberGenerator = numberGenerator;
        this.mapper = mapper;
        this.tenantGuard = tenantGuard;
    }

    @Override
    public PatientResponse register(PatientRequest r) {
        Long clinicId = tenantGuard.requireCurrentClinicId();
        if (r.nationalId() != null && !r.nationalId().isBlank()
                && patientRepository.findByNationalIdAndClinicId(r.nationalId(), clinicId).isPresent()) {
            throw new BusinessRuleException("DUPLICATE_NATIONAL_ID", "เลขบัตรประชาชนนี้มีในระบบแล้ว");
        }

        Patient patient = new Patient(
                numberGenerator.nextHn(), r.firstName(), r.lastName(), r.gender(),
                r.birthDate(), r.nationalId(),
                new ContactInfo(r.phone(), r.email(), r.lineId()));
        patient.setClinicId(clinicId);   // ผูกกับคลินิกปัจจุบันเสมอตอนสร้างใหม่ (explicit ดีกว่าพึ่งพา @PrePersist ล้วน ๆ)
        applyOptional(patient, r);

        return mapper.toDto(patientRepository.save(patient));
    }

    @Override
    public PatientResponse update(Long id, PatientRequest r) {
        Patient patient = getEntity(id);
        patient.setFirstName(r.firstName());
        patient.setLastName(r.lastName());
        patient.setGender(r.gender());
        patient.setBirthDate(r.birthDate());
        patient.setContact(new ContactInfo(r.phone(), r.email(), r.lineId()));
        applyOptional(patient, r);
        return mapper.toDto(patientRepository.save(patient));
    }

    private void applyOptional(Patient patient, PatientRequest r) {
        patient.setAddress(new Address(r.addressLine(), r.district(), r.province(), r.postcode()));
        patient.setBloodType(r.bloodType());
        patient.setAllergies(r.allergies());
        patient.setChronicDisease(r.chronicDisease());
        patient.setEmergencyContact(r.emergencyContact());
    }

    @Override
    @Transactional(readOnly = true)
    public PatientResponse findById(Long id) { return mapper.toDto(getEntity(id)); }

    @Override
    @Transactional(readOnly = true)
    public PatientResponse findByHn(String hn) {
        Long clinicId = tenantGuard.requireCurrentClinicId();
        return patientRepository.findByHnAndClinicId(hn, clinicId).map(mapper::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("ผู้ป่วย HN", hn));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PatientResponse> search(String keyword, Pageable pageable) {
        Long clinicId = tenantGuard.requireCurrentClinicId();
        String q = keyword == null ? "" : keyword.trim();
        return patientRepository.search(clinicId, q, pageable).map(mapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Patient getEntity(Long id) {
        Long clinicId = tenantGuard.requireCurrentClinicId();
        return patientRepository.findByIdAndClinicId(id, clinicId)
                .orElseThrow(() -> new ResourceNotFoundException("ผู้ป่วย", id));
    }
}
