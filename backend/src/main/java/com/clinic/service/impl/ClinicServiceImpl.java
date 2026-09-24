package com.clinic.service.impl;

import com.clinic.config.StripeConfig;
import com.clinic.domain.auth.Role;
import com.clinic.domain.auth.UserAccount;
import com.clinic.domain.billing.Plan;
import com.clinic.domain.billing.Subscription;
import com.clinic.domain.billing.SubscriptionStatus;
import com.clinic.domain.tenant.Clinic;
import com.clinic.domain.tenant.ClinicStatus;
import com.clinic.domain.tenant.TenantContext;
import com.clinic.dto.*;
import com.clinic.exception.BusinessRuleException;
import com.clinic.exception.ResourceNotFoundException;
import com.clinic.repository.*;
import com.clinic.security.AppUserPrincipal;
import com.clinic.security.JwtTokenService;
import com.clinic.service.ClinicService;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.SubscriptionUpdateParams;
import com.stripe.param.checkout.SessionCreateParams;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ตรรกะฝั่ง "ขายซอฟต์แวร์ให้คลินิก" ทั้งหมด — สมัครคลินิกใหม่ (self-service ผ่าน Stripe Checkout
 * หรือ Super Admin เพิ่มให้เอง) และซิงก์สถานะการสมัครสมาชิกจาก webhook ของ Stripe
 */
@Service
@Transactional
public class ClinicServiceImpl implements ClinicService {

    private final ClinicRepository clinics;
    private final PlanRepository plans;
    private final SubscriptionRepository subscriptions;
    private final UserAccountRepository accounts;
    private final DoctorRepository doctors;
    private final PatientRepository patients;
    private final PasswordEncoder encoder;
    private final JwtTokenService tokenService;
    private final StripeConfig stripeConfig;
    private final com.clinic.service.TenantGuard tenantGuard;

    public ClinicServiceImpl(ClinicRepository clinics, PlanRepository plans,
                             SubscriptionRepository subscriptions, UserAccountRepository accounts,
                             DoctorRepository doctors, PatientRepository patients,
                             PasswordEncoder encoder, JwtTokenService tokenService,
                             StripeConfig stripeConfig, com.clinic.service.TenantGuard tenantGuard) {
        this.clinics = clinics;
        this.plans = plans;
        this.subscriptions = subscriptions;
        this.accounts = accounts;
        this.doctors = doctors;
        this.patients = patients;
        this.encoder = encoder;
        this.tokenService = tokenService;
        this.stripeConfig = stripeConfig;
        this.tenantGuard = tenantGuard;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlanResponse> listPlans() {
        return plans.findByActiveTrueOrderByPriceMonthlyThbAsc().stream().map(this::toPlanResponse).toList();
    }

    @Override
    public ClinicRegisterResponse registerClinic(ClinicRegisterRequest r) {
        if (clinics.existsBySlugIgnoreCase(r.slug())) {
            throw new BusinessRuleException("SLUG_TAKEN", "รหัสคลินิกนี้ถูกใช้งานแล้ว กรุณาลองรหัสอื่น");
        }
        if (accounts.existsByUsernameIgnoreCase(r.adminUsername())) {
            throw new BusinessRuleException("USERNAME_TAKEN", "ชื่อผู้ใช้นี้ถูกใช้งานแล้ว");
        }
        Plan plan = plans.findByCodeIgnoreCase(r.planCode())
                .filter(Plan::isActive)
                .orElseThrow(() -> new BusinessRuleException("PLAN_NOT_FOUND", "ไม่พบแพ็กเกจที่เลือก"));

        Clinic clinic = clinics.save(new Clinic(r.clinicName(), r.slug(), r.contactEmail(), r.contactPhone()));

        Subscription subscription = new Subscription(clinic.getId(), plan, SubscriptionStatus.TRIALING);
        subscription.renewPeriod(LocalDateTime.now(), clinic.getTrialEndsAt());
        subscriptions.save(subscription);

        // สร้างบัญชีผู้ดูแลคลินิกทันที ใช้งานได้ระหว่างช่วงทดลอง 14 วัน ไม่ว่าจะเลือกแพ็กเกจใด
        TenantContext.set(clinic.getId());
        UserAccount admin;
        try {
            admin = accounts.save(new UserAccount(r.adminUsername(), encoder.encode(r.adminPassword()),
                    Role.ADMIN, null, "ผู้ดูแลคลินิก " + clinic.getName(), clinic.getId()));
        } finally {
            TenantContext.clear();
        }

        if (plan.isFree()) {
            return new ClinicRegisterResponse(false, null, toLoginResponse(admin, clinic));
        }

        if (!stripeConfig.isConfigured()) {
            // ยังไม่ได้ตั้งค่า Stripe ในสภาพแวดล้อมนี้ — ปล่อยให้สมัครด้วยช่วงทดลองไปก่อนแทนที่จะพัง
            return new ClinicRegisterResponse(false, null, toLoginResponse(admin, clinic));
        }

        String checkoutUrl = createCheckoutSession(clinic, plan);
        return new ClinicRegisterResponse(true, checkoutUrl, null);
    }

    private String createCheckoutSession(Clinic clinic, Plan plan) {
        try {
            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                    .setCustomerEmail(clinic.getContactEmail())
                    .setSuccessUrl(stripeConfig.getSuccessUrl())
                    .setCancelUrl(stripeConfig.getCancelUrl())
                    .putMetadata("clinicId", String.valueOf(clinic.getId()))
                    .addLineItem(SessionCreateParams.LineItem.builder()
                            .setPrice(plan.getStripePriceId())
                            .setQuantity(1L)
                            .build())
                    .build();
            Session session = Session.create(params);
            return session.getUrl();
        } catch (StripeException e) {
            throw new BusinessRuleException("STRIPE_ERROR", "ไม่สามารถสร้างหน้าชำระเงินได้: " + e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClinicResponse> listAllClinics() {
        return clinics.findAll().stream().map(this::toClinicResponse).toList();
    }

    @Override
    public ClinicResponse createClinicBySuperAdmin(CreateClinicRequest r) {
        if (clinics.existsBySlugIgnoreCase(r.slug())) {
            throw new BusinessRuleException("SLUG_TAKEN", "รหัสคลินิกนี้ถูกใช้งานแล้ว");
        }
        if (accounts.existsByUsernameIgnoreCase(r.adminUsername())) {
            throw new BusinessRuleException("USERNAME_TAKEN", "ชื่อผู้ใช้นี้ถูกใช้งานแล้ว");
        }
        Plan plan = plans.findByCodeIgnoreCase(r.planCode())
                .orElseThrow(() -> new BusinessRuleException("PLAN_NOT_FOUND", "ไม่พบแพ็กเกจที่เลือก"));

        Clinic clinic = clinics.save(new Clinic(r.clinicName(), r.slug(), r.contactEmail(), r.contactPhone()));
        clinic.activate();   // Super Admin เพิ่มให้เอง = ใช้งานได้ทันทีไม่ต้องผ่านการชำระเงิน
        clinics.save(clinic);

        Subscription subscription = new Subscription(clinic.getId(), plan, SubscriptionStatus.ACTIVE);
        subscription.renewPeriod(LocalDateTime.now(), LocalDateTime.now().plusMonths(1));
        subscriptions.save(subscription);

        TenantContext.set(clinic.getId());
        try {
            accounts.save(new UserAccount(r.adminUsername(), encoder.encode(r.adminPassword()),
                    Role.ADMIN, null, "ผู้ดูแลคลินิก " + clinic.getName(), clinic.getId()));
        } finally {
            TenantContext.clear();
        }

        return toClinicResponse(clinic);
    }

    @Override
    public ClinicResponse setClinicStatus(Long clinicId, String status) {
        Clinic clinic = clinics.findById(clinicId)
                .orElseThrow(() -> new ResourceNotFoundException("ไม่พบคลินิก id=" + clinicId));
        switch (status.toUpperCase()) {
            case "ACTIVE" -> clinic.activate();
            case "SUSPENDED" -> clinic.suspend();
            case "CANCELED" -> clinic.cancel();
            case "PAST_DUE" -> clinic.markPastDue();
            default -> throw new BusinessRuleException("INVALID_STATUS", "สถานะไม่ถูกต้อง: " + status);
        }
        return toClinicResponse(clinics.save(clinic));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlanResponse> listAllPlansForAdmin() {
        return plans.findAll().stream().map(this::toPlanResponse).toList();
    }

    /** ---------- ADMIN ของคลินิกตัวเอง: ดู/เปลี่ยน/ยกเลิกแพ็กเกจของตัวเอง ---------- */

    @Override
    @Transactional(readOnly = true)
    public SubscriptionResponse getMySubscription() {
        Long clinicId = tenantGuard.requireCurrentClinicId();
        return toSubscriptionResponse(loadClinic(clinicId), loadSubscription(clinicId));
    }

    @Override
    public ChangePlanResponse changeMyPlan(ChangePlanRequest r) {
        Long clinicId = tenantGuard.requireCurrentClinicId();
        Clinic clinic = loadClinic(clinicId);
        Subscription subscription = loadSubscription(clinicId);
        Plan currentPlan = subscription.getPlan();

        Plan newPlan = plans.findByCodeIgnoreCase(r.planCode())
                .filter(Plan::isActive)
                .orElseThrow(() -> new BusinessRuleException("PLAN_NOT_FOUND", "ไม่พบแพ็กเกจที่เลือก"));

        if (newPlan.getCode().equalsIgnoreCase(currentPlan.getCode())) {
            throw new BusinessRuleException("SAME_PLAN", "คลินิกนี้ใช้แพ็กเกจนี้อยู่แล้ว");
        }

        // กรณีที่ 1: เปลี่ยนไปแพ็กเกจฟรี — ยกเลิกการสมัครสมาชิกฝั่ง Stripe ทันที ไม่มีการเก็บเงินอีก
        if (newPlan.isFree()) {
            cancelStripeSubscriptionIfAny(subscription);
            subscription.changePlan(newPlan);
            subscription.renewPeriod(LocalDateTime.now(), null);
            subscriptions.save(subscription);
            clinic.activate();
            clinics.save(clinic);
            return new ChangePlanResponse(false, null, toSubscriptionResponse(clinic, subscription));
        }

        // กรณีที่ 2: มีการสมัครสมาชิกกับ Stripe อยู่แล้ว (กำลังใช้แพ็กเกจเสียเงิน) — สลับแพ็กเกจได้ทันที
        // Stripe จะคิดเงินส่วนต่างตามสัดส่วนวันที่เหลือให้อัตโนมัติ (proration)
        if (subscription.getStripeSubscriptionId() != null && !subscription.getStripeSubscriptionId().isBlank()) {
            if (!stripeConfig.isConfigured()) {
                throw new BusinessRuleException("STRIPE_NOT_CONFIGURED", "ระบบยังไม่ได้ตั้งค่าการชำระเงิน");
            }
            updateStripeSubscriptionPrice(subscription.getStripeSubscriptionId(), newPlan.getStripePriceId());
            subscription.changePlan(newPlan);
            subscriptions.save(subscription);
            return new ChangePlanResponse(false, null, toSubscriptionResponse(clinic, subscription));
        }

        // กรณีที่ 3: กำลังใช้แพ็กเกจฟรีอยู่ อัปเกรดเป็นแพ็กเกจเสียเงินครั้งแรก — ต้องไปกรอกบัตรที่ Stripe ก่อน
        if (!stripeConfig.isConfigured()) {
            // ยังไม่ตั้งค่า Stripe ไว้ — อนุโลมให้เปลี่ยนแพ็กเกจได้เลยโดยไม่เก็บเงินจริง (สำหรับสาธิต/ทดสอบ)
            subscription.changePlan(newPlan);
            subscription.renewPeriod(LocalDateTime.now(), LocalDateTime.now().plusMonths(1));
            subscriptions.save(subscription);
            return new ChangePlanResponse(false, null, toSubscriptionResponse(clinic, subscription));
        }
        String checkoutUrl = createCheckoutSession(clinic, newPlan);
        return new ChangePlanResponse(true, checkoutUrl, null);
    }

    @Override
    public SubscriptionResponse cancelMySubscription() {
        Long clinicId = tenantGuard.requireCurrentClinicId();
        Clinic clinic = loadClinic(clinicId);
        Subscription subscription = loadSubscription(clinicId);

        if (subscription.getPlan().isFree()) {
            throw new BusinessRuleException("ALREADY_FREE", "คลินิกนี้ใช้แพ็กเกจฟรีอยู่แล้ว ไม่มีอะไรต้องยกเลิก");
        }
        if (subscription.getStripeSubscriptionId() != null && stripeConfig.isConfigured()) {
            try {
                com.stripe.model.Subscription stripeSub =
                        com.stripe.model.Subscription.retrieve(subscription.getStripeSubscriptionId());
                stripeSub.update(SubscriptionUpdateParams.builder().setCancelAtPeriodEnd(true).build());
            } catch (StripeException e) {
                throw new BusinessRuleException("STRIPE_ERROR", "ไม่สามารถยกเลิกการสมัครสมาชิกได้: " + e.getMessage());
            }
        }
        subscription.requestCancelAtPeriodEnd();
        subscriptions.save(subscription);
        return toSubscriptionResponse(clinic, subscription);
    }

    @Override
    public SubscriptionResponse reactivateMySubscription() {
        Long clinicId = tenantGuard.requireCurrentClinicId();
        Clinic clinic = loadClinic(clinicId);
        Subscription subscription = loadSubscription(clinicId);

        if (!subscription.isCancelAtPeriodEnd()) {
            throw new BusinessRuleException("NOT_CANCELING", "แพ็กเกจนี้ไม่ได้อยู่ระหว่างรอยกเลิก");
        }
        if (subscription.getStripeSubscriptionId() != null && stripeConfig.isConfigured()) {
            try {
                com.stripe.model.Subscription stripeSub =
                        com.stripe.model.Subscription.retrieve(subscription.getStripeSubscriptionId());
                stripeSub.update(SubscriptionUpdateParams.builder().setCancelAtPeriodEnd(false).build());
            } catch (StripeException e) {
                throw new BusinessRuleException("STRIPE_ERROR", "ไม่สามารถยกเลิกคำขอยกเลิกได้: " + e.getMessage());
            }
        }
        subscription.cancelTheCancellation();
        subscriptions.save(subscription);
        return toSubscriptionResponse(clinic, subscription);
    }

    private void cancelStripeSubscriptionIfAny(Subscription subscription) {
        if (subscription.getStripeSubscriptionId() == null || !stripeConfig.isConfigured()) return;
        try {
            com.stripe.model.Subscription.retrieve(subscription.getStripeSubscriptionId()).cancel();
        } catch (StripeException e) {
            throw new BusinessRuleException("STRIPE_ERROR", "ไม่สามารถยกเลิกการสมัครสมาชิกเดิมได้: " + e.getMessage());
        }
    }

    /** เปลี่ยนราคาของการสมัครสมาชิกที่มีอยู่แล้วบน Stripe (ใช้ตอนอัปเกรด/ดาวน์เกรดระหว่างแพ็กเกจเสียเงิน) */
    private void updateStripeSubscriptionPrice(String stripeSubscriptionId, String newPriceId) {
        try {
            com.stripe.model.Subscription stripeSub = com.stripe.model.Subscription.retrieve(stripeSubscriptionId);
            String itemId = stripeSub.getItems().getData().get(0).getId();
            SubscriptionUpdateParams params = SubscriptionUpdateParams.builder()
                    .addItem(SubscriptionUpdateParams.Item.builder()
                            .setId(itemId)
                            .setPrice(newPriceId)
                            .build())
                    .setProrationBehavior(SubscriptionUpdateParams.ProrationBehavior.CREATE_PRORATIONS)
                    .build();
            stripeSub.update(params);
        } catch (StripeException e) {
            throw new BusinessRuleException("STRIPE_ERROR", "ไม่สามารถเปลี่ยนแพ็กเกจได้: " + e.getMessage());
        }
    }

    private Clinic loadClinic(Long clinicId) {
        return clinics.findById(clinicId)
                .orElseThrow(() -> new ResourceNotFoundException("ไม่พบคลินิก id=" + clinicId));
    }

    private Subscription loadSubscription(Long clinicId) {
        return subscriptions.findByClinicId(clinicId)
                .orElseThrow(() -> new ResourceNotFoundException("ไม่พบการสมัครสมาชิกของคลินิก id=" + clinicId));
    }

    private SubscriptionResponse toSubscriptionResponse(Clinic clinic, Subscription sub) {
        Plan plan = sub.getPlan();
        return new SubscriptionResponse(
                clinic.getId(), clinic.getName(), clinic.getStatus().name(),
                plan.getCode(), plan.getName(), plan.getPriceMonthlyThb(),
                plan.getMaxDoctors(), plan.getMaxActivePatients(),
                sub.getStatus().name(), sub.getStatus().getLabel(),
                sub.getCurrentPeriodStart(), sub.getCurrentPeriodEnd(),
                sub.isCancelAtPeriodEnd(),
                sub.getStripeSubscriptionId() != null && !sub.getStripeSubscriptionId().isBlank());
    }

    @Override
    public void activateSubscriptionFromCheckout(Long clinicId, String stripeCustomerId,
                                                  String stripeSubscriptionId,
                                                  LocalDateTime periodStart, LocalDateTime periodEnd) {
        Subscription subscription = subscriptions.findByClinicId(clinicId)
                .orElseThrow(() -> new ResourceNotFoundException("ไม่พบการสมัครสมาชิกของคลินิก id=" + clinicId));
        subscription.applyStripeCheckout(stripeCustomerId, stripeSubscriptionId, periodStart, periodEnd);
        subscriptions.save(subscription);

        Clinic clinic = clinics.findById(clinicId)
                .orElseThrow(() -> new ResourceNotFoundException("ไม่พบคลินิก id=" + clinicId));
        clinic.activate();
        clinics.save(clinic);
    }

    @Override
    public void syncSubscriptionStatus(String stripeSubscriptionId, String stripeStatus, LocalDateTime periodEnd) {
        subscriptions.findByStripeSubscriptionId(stripeSubscriptionId).ifPresent(subscription -> {
            switch (stripeStatus) {
                case "active", "trialing" -> subscription.renewPeriod(subscription.getCurrentPeriodStart(), periodEnd);
                case "past_due", "unpaid" -> subscription.markPastDue();
                case "canceled" -> subscription.markCanceled();
                default -> { /* incomplete / incomplete_expired ไม่ต้องทำอะไรเพิ่ม */ }
            }
            subscriptions.save(subscription);

            clinics.findById(subscription.getClinicId()).ifPresent(clinic -> {
                if (subscription.getStatus() == SubscriptionStatus.PAST_DUE) clinic.markPastDue();
                else if (subscription.getStatus() == SubscriptionStatus.CANCELED) clinic.cancel();
                else if (subscription.getStatus() == SubscriptionStatus.ACTIVE) clinic.activate();
                clinics.save(clinic);
            });
        });
    }

    @Override
    public void cancelSubscriptionByStripeId(String stripeSubscriptionId) {
        subscriptions.findByStripeSubscriptionId(stripeSubscriptionId).ifPresent(subscription -> {
            subscription.markCanceled();
            subscriptions.save(subscription);
            clinics.findById(subscription.getClinicId()).ifPresent(clinic -> {
                clinic.cancel();
                clinics.save(clinic);
            });
        });
    }

    /** ---------- helper ---------- */

    private LoginResponse toLoginResponse(UserAccount account, Clinic clinic) {
        AppUserPrincipal principal = new AppUserPrincipal(account);
        return new LoginResponse(
                tokenService.issue(principal), "Bearer", tokenService.expiresInSeconds(),
                account.getId(), account.getUsername(), account.resolveDisplayName(),
                account.getRole(), account.getRole().getLabel(), account.linkedPersonId(), null,
                account.getRole().getPermissions(), account.isMustChangePassword(),
                clinic.getId(), clinic.getName(), clinic.getStatus().name());
    }

    private PlanResponse toPlanResponse(Plan p) {
        return new PlanResponse(p.getId(), p.getCode(), p.getName(), p.getPriceMonthlyThb(),
                p.getMaxDoctors(), p.getMaxActivePatients(), p.getDescription());
    }

    private ClinicResponse toClinicResponse(Clinic clinic) {
        Subscription sub = subscriptions.findByClinicId(clinic.getId()).orElse(null);
        Plan plan = sub == null ? null : sub.getPlan();
        return new ClinicResponse(
                clinic.getId(), clinic.getName(), clinic.getSlug(), clinic.getContactEmail(),
                clinic.getContactPhone(), clinic.getStatus().name(), clinic.getStatus().getLabel(),
                clinic.getTrialEndsAt(), clinic.getCreatedAt(),
                plan == null ? null : plan.getCode(), plan == null ? null : plan.getName(),
                plan == null ? null : plan.getPriceMonthlyThb(),
                sub == null ? null : sub.getStatus().name(),
                sub == null ? null : sub.getCurrentPeriodEnd(),
                doctors.countByClinicId(clinic.getId()), patients.countByClinicId(clinic.getId()));
    }
}
