package com.clinic.repository;

import com.clinic.domain.billing.Invoice;
import com.clinic.domain.billing.InvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    Optional<Invoice> findByIdAndClinicId(Long id, Long clinicId);
    Optional<Invoice> findByClinicIdAndAppointmentId(Long clinicId, Long appointmentId);
    Optional<Invoice> findByInvoiceNoAndClinicId(String invoiceNo, Long clinicId);
    List<Invoice> findByClinicIdAndPatientId(Long clinicId, Long patientId);
    List<Invoice> findByClinicIdAndStatus(Long clinicId, InvoiceStatus status);
}
