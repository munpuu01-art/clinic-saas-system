package com.clinic.service;

import com.clinic.dto.*;

import java.time.LocalDateTime;
import java.util.List;

public interface ClinicService {
    List<PlanResponse> listPlans();
    ClinicRegisterResponse registerClinic(ClinicRegisterRequest request);

    // ---------- SUPER_ADMIN ----------
    List<ClinicResponse> listAllClinics();
    ClinicResponse createClinicBySuperAdmin(CreateClinicRequest request);
    ClinicResponse setClinicStatus(Long clinicId, String status);
    List<PlanResponse> listAllPlansForAdmin();

    // ---------- เรียกจาก Webhook handler ----------
    void activateSubscriptionFromCheckout(Long clinicId, String stripeCustomerId,
                                          String stripeSubscriptionId,
                                          LocalDateTime periodStart, LocalDateTime periodEnd);
    void syncSubscriptionStatus(String stripeSubscriptionId, String stripeStatus, LocalDateTime periodEnd);
    void cancelSubscriptionByStripeId(String stripeSubscriptionId);
}
