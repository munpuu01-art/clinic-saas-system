package com.clinic.service.impl;

import com.clinic.domain.appointment.Appointment;
import com.clinic.domain.billing.Invoice;
import com.clinic.domain.billing.InvoiceItem;
import com.clinic.domain.billing.Payment;
import com.clinic.dto.InvoiceItemRequest;
import com.clinic.dto.InvoiceResponse;
import com.clinic.dto.PaymentRequest;
import com.clinic.exception.ResourceNotFoundException;
import com.clinic.factory.DocumentNumberGenerator;
import com.clinic.mapper.DomainMapper;
import com.clinic.repository.AppointmentRepository;
import com.clinic.repository.InvoiceRepository;
import com.clinic.service.BillingService;
import com.clinic.service.TenantGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@Transactional
public class BillingServiceImpl implements BillingService {

    private final InvoiceRepository invoiceRepository;
    private final AppointmentRepository appointmentRepository;
    private final DocumentNumberGenerator numberGenerator;
    private final DomainMapper mapper;
    private final TenantGuard tenantGuard;

    public BillingServiceImpl(InvoiceRepository invoiceRepository,
                              AppointmentRepository appointmentRepository,
                              DocumentNumberGenerator numberGenerator,
                              DomainMapper mapper, TenantGuard tenantGuard) {
        this.invoiceRepository = invoiceRepository;
        this.appointmentRepository = appointmentRepository;
        this.numberGenerator = numberGenerator;
        this.mapper = mapper;
        this.tenantGuard = tenantGuard;
    }

    /** เปิดใบแจ้งค่าบริการ พร้อมใส่ค่าตรวจแพทย์เป็นรายการแรกให้อัตโนมัติ */
    @Override
    public InvoiceResponse createForAppointment(Long appointmentId) {
        Long clinicId = tenantGuard.requireCurrentClinicId();
        Appointment appointment = appointmentRepository.findByIdAndClinicId(appointmentId, clinicId)
                .orElseThrow(() -> new ResourceNotFoundException("นัดหมาย", appointmentId));

        Invoice invoice = invoiceRepository.findByClinicIdAndAppointmentId(clinicId, appointmentId)
                .orElseGet(() -> {
                    Invoice inv = new Invoice(
                            numberGenerator.nextInvoiceNo(appointment.getAppointmentDate()), appointment);
                    inv.setClinicId(clinicId);
                    BigDecimal fee = appointment.getFee() != null
                            ? appointment.getFee() : appointment.getDoctor().effectiveFee();
                    inv.addItem(new InvoiceItem("ค่าตรวจ " + appointment.getDoctor().getSpecialty().getName(), 1, fee));
                    return inv;
                });

        return mapper.toDto(invoiceRepository.save(invoice));
    }

    @Override
    public InvoiceResponse addItem(Long invoiceId, InvoiceItemRequest r) {
        Invoice invoice = getInvoice(invoiceId);
        invoice.addItem(new InvoiceItem(r.description(), r.quantity(), r.unitPrice()));
        return mapper.toDto(invoiceRepository.save(invoice));
    }

    @Override
    public InvoiceResponse issue(Long invoiceId) {
        Invoice invoice = getInvoice(invoiceId);
        invoice.issue();
        return mapper.toDto(invoiceRepository.save(invoice));
    }

    @Override
    public InvoiceResponse pay(Long invoiceId, PaymentRequest r) {
        Invoice invoice = getInvoice(invoiceId);
        invoice.pay(new Payment(r.amount(), r.method(), r.referenceNo()));
        return mapper.toDto(invoiceRepository.save(invoice));
    }

    @Override
    @Transactional(readOnly = true)
    public InvoiceResponse findByAppointment(Long appointmentId) {
        Long clinicId = tenantGuard.requireCurrentClinicId();
        return invoiceRepository.findByClinicIdAndAppointmentId(clinicId, appointmentId).map(mapper::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("ใบแจ้งค่าบริการของนัดหมาย", appointmentId));
    }

    private Invoice getInvoice(Long id) {
        Long clinicId = tenantGuard.requireCurrentClinicId();
        return invoiceRepository.findByIdAndClinicId(id, clinicId)
                .orElseThrow(() -> new ResourceNotFoundException("ใบแจ้งค่าบริการ", id));
    }
}
