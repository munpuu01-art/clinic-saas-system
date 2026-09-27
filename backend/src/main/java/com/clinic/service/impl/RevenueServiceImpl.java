package com.clinic.service.impl;

import com.clinic.domain.billing.PlatformPayment;
import com.clinic.domain.billing.PlatformPaymentMethod;
import com.clinic.domain.billing.Subscription;
import com.clinic.domain.billing.SubscriptionStatus;
import com.clinic.domain.tenant.Clinic;
import com.clinic.dto.RecordPlatformPaymentRequest;
import com.clinic.dto.RevenueSummaryResponse;
import com.clinic.dto.RevenueSummaryResponse.ClinicRevenue;
import com.clinic.dto.RevenueSummaryResponse.PaymentRow;
import com.clinic.exception.BusinessRuleException;
import com.clinic.exception.ResourceNotFoundException;
import com.clinic.repository.ClinicRepository;
import com.clinic.repository.PlatformPaymentRepository;
import com.clinic.repository.SubscriptionRepository;
import com.clinic.service.RevenueService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RevenueServiceImpl implements RevenueService {

    private static final Logger log = LoggerFactory.getLogger(RevenueServiceImpl.class);
    private static final int RECENT_LIMIT = 20;

    private final PlatformPaymentRepository payments;
    private final ClinicRepository clinics;
    private final SubscriptionRepository subscriptions;

    public RevenueServiceImpl(PlatformPaymentRepository payments, ClinicRepository clinics,
                              SubscriptionRepository subscriptions) {
        this.payments = payments;
        this.clinics = clinics;
        this.subscriptions = subscriptions;
    }

    @Override
    @Transactional(readOnly = true)
    public RevenueSummaryResponse summary() {
        List<PlatformPayment> all = payments.findAllByOrderByPaidAtDesc();
        Map<Long, String> clinicNames = clinics.findAll().stream()
                .collect(Collectors.toMap(Clinic::getId, Clinic::getName));

        BigDecimal total = sum(all);

        LocalDateTime monthStart = LocalDateTime.now().withDayOfMonth(1).with(LocalTime.MIN);
        BigDecimal thisMonth = sum(all.stream().filter(p -> !p.getPaidAt().isBefore(monthStart)).toList());

        // MRR — นับเฉพาะคลินิกที่จ่ายเงินอยู่จริง (ACTIVE) และแพ็กเกจมีราคา
        List<Subscription> paying = subscriptions.findAll().stream()
                .filter(s -> s.getStatus() == SubscriptionStatus.ACTIVE)
                .filter(s -> s.getPlan().getPriceMonthlyThb() != null && s.getPlan().getPriceMonthlyThb().signum() > 0)
                .toList();
        BigDecimal mrr = paying.stream().map(s -> s.getPlan().getPriceMonthlyThb())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<ClinicRevenue> byClinic = all.stream()
                .collect(Collectors.groupingBy(PlatformPayment::getClinicId))
                .entrySet().stream()
                .map(e -> new ClinicRevenue(e.getKey(), sum(e.getValue()), e.getValue().size(),
                        e.getValue().get(0).getPaidAt()))   // list เรียงใหม่สุดก่อนอยู่แล้ว
                .toList();

        List<PaymentRow> recent = all.stream().limit(RECENT_LIMIT)
                .map(p -> toRow(p, clinicNames)).toList();

        return new RevenueSummaryResponse(total, thisMonth, mrr, paying.size(), all.size(), byClinic, recent);
    }

    @Override
    public PaymentRow recordManualPayment(Long clinicId, RecordPlatformPaymentRequest request) {
        Clinic clinic = clinics.findById(clinicId)
                .orElseThrow(() -> new ResourceNotFoundException("ไม่พบคลินิก id=" + clinicId));
        String planCode = subscriptions.findByClinicId(clinicId)
                .map(s -> s.getPlan().getCode()).orElse(null);
        LocalDateTime paidAt = request.paidDate() != null
                ? request.paidDate().atTime(LocalTime.now().withNano(0))
                : LocalDateTime.now();
        if (paidAt.isAfter(LocalDateTime.now().plusDays(1))) {
            throw new BusinessRuleException("FUTURE_PAYMENT", "วันที่ชำระต้องไม่เป็นวันในอนาคต");
        }
        PlatformPayment saved = payments.save(new PlatformPayment(clinicId, planCode,
                request.amountThb().setScale(2, RoundingMode.HALF_UP), paidAt,
                PlatformPaymentMethod.valueOf(request.method()), null, blankToNull(request.note())));
        return toRow(saved, Map.of(clinic.getId(), clinic.getName()));
    }

    @Override
    public void deleteManualPayment(Long paymentId) {
        PlatformPayment payment = payments.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("ไม่พบรายการชำระเงิน id=" + paymentId));
        if (!payment.isManual()) {
            throw new BusinessRuleException("STRIPE_PAYMENT_LOCKED",
                    "รายการจาก Stripe ลบไม่ได้ หากต้องการคืนเงินให้ทำผ่าน Stripe Dashboard");
        }
        payments.delete(payment);
    }

    @Override
    public void recordStripeInvoicePaid(String invoiceId, String stripeCustomerId, long amountMinorUnit, String currency) {
        if (invoiceId == null || amountMinorUnit <= 0) return;          // invoice ยอด 0 (เช่น ช่วงทดลอง) ไม่นับเป็นรายรับ
        if (payments.existsByReference(invoiceId)) return;               // Stripe ส่ง webhook ซ้ำได้ — บันทึกครั้งเดียว
        if (currency != null && !"thb".equalsIgnoreCase(currency)) {
            log.warn("ข้าม invoice {} เพราะสกุลเงิน {} ไม่ใช่ THB", invoiceId, currency);
            return;
        }

        // ถ้ายังหา subscription ไม่เจอ (invoice.paid มาถึงก่อน checkout.session.completed)
        // จะโยน exception → ตอบ 404 → Stripe ส่ง webhook นี้ซ้ำให้อัตโนมัติภายหลัง
        Subscription subscription = subscriptions.findByStripeCustomerId(stripeCustomerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ยังไม่พบคลินิกของ Stripe customer " + stripeCustomerId + " (จะลองใหม่เมื่อ Stripe ส่งซ้ำ)"));

        BigDecimal amount = BigDecimal.valueOf(amountMinorUnit).movePointLeft(2);   // สตางค์ → บาท
        payments.save(new PlatformPayment(subscription.getClinicId(), subscription.getPlan().getCode(),
                amount, LocalDateTime.now(), PlatformPaymentMethod.STRIPE, invoiceId, null));
    }

    // ---------- helpers ----------

    private static BigDecimal sum(List<PlatformPayment> list) {
        return list.stream().map(PlatformPayment::getAmountThb).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static PaymentRow toRow(PlatformPayment p, Map<Long, String> clinicNames) {
        return new PaymentRow(p.getId(), p.getClinicId(), clinicNames.getOrDefault(p.getClinicId(), "-"),
                p.getPlanCode(), p.getAmountThb(), p.getMethod().name(), p.getMethod().getLabel(),
                p.getReference(), p.getNote(), p.getPaidAt(), p.isManual());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
