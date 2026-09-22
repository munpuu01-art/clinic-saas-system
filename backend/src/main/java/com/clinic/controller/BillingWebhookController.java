package com.clinic.controller;

import com.clinic.config.StripeConfig;
import com.clinic.service.ClinicService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * รับการแจ้งเตือนจาก Stripe เมื่อสถานะการชำระเงินเปลี่ยน — endpoint นี้ "ไม่มี" JWT
 * (Stripe เรียกเราโดยตรง ไม่ผ่านเบราว์เซอร์ของผู้ใช้) จึงตรวจสอบความถูกต้องด้วยลายเซ็น
 * ดิจิทัลที่ Stripe แนบมาแทน (Stripe-Signature header + STRIPE_WEBHOOK_SECRET)
 *
 * วิธีตั้งค่าใน Stripe Dashboard: Developers → Webhooks → Add endpoint
 *   URL: https://<โดเมน backend ของคุณ>/api/webhooks/stripe
 *   Events: checkout.session.completed, customer.subscription.updated, customer.subscription.deleted
 */
@RestController
@RequestMapping("/api/webhooks")
public class BillingWebhookController {

    private final ClinicService clinicService;
    private final StripeConfig stripeConfig;

    public BillingWebhookController(ClinicService clinicService, StripeConfig stripeConfig) {
        this.clinicService = clinicService;
        this.stripeConfig = stripeConfig;
    }

    @PostMapping("/stripe")
    public ResponseEntity<String> handleStripeWebhook(@RequestBody String payload,
                                                       @RequestHeader("Stripe-Signature") String signature) {
        Event event;
        try {
            event = Webhook.constructEvent(payload, signature, stripeConfig.getWebhookSecret());
        } catch (SignatureVerificationException e) {
            // ลายเซ็นไม่ถูกต้อง — อาจเป็นคำขอปลอม ปฏิเสธทันทีโดยไม่ประมวลผลอะไรเลย
            return ResponseEntity.status(400).body("invalid signature");
        }

        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();

        switch (event.getType()) {
            case "checkout.session.completed" -> deserializer.getObject().ifPresent(obj -> {
                if (obj instanceof Session session) {
                    Long clinicId = Long.valueOf(session.getMetadata().get("clinicId"));
                    LocalDateTime start = LocalDateTime.now();
                    LocalDateTime end = start.plusMonths(1);   // ค่าเริ่มต้น เผื่อดึงรอบบิลจริงไม่ได้
                    clinicService.activateSubscriptionFromCheckout(
                            clinicId, session.getCustomer(), session.getSubscription(), start, end);
                }
            });
            case "customer.subscription.updated" -> deserializer.getObject().ifPresent(obj -> {
                if (obj instanceof Subscription sub) {
                    clinicService.syncSubscriptionStatus(sub.getId(), sub.getStatus(), periodEndOf(sub));
                }
            });
            case "customer.subscription.deleted" -> deserializer.getObject().ifPresent(obj -> {
                if (obj instanceof Subscription sub) {
                    clinicService.cancelSubscriptionByStripeId(sub.getId());
                }
            });
            default -> { /* เหตุการณ์อื่นที่เราไม่สนใจ ไม่ต้องทำอะไร */ }
        }

        return ResponseEntity.ok("received");
    }

    private LocalDateTime toDateTime(Long epochSeconds) {
        if (epochSeconds == null) return LocalDateTime.now().plusMonths(1);
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneOffset.UTC);
    }

    /**
     * ตั้งแต่ Stripe API เวอร์ชัน 2025-03-31 (Basil) เป็นต้นไป ฟิลด์ current_period_end
     * ถูกย้ายออกจาก Subscription ไปอยู่ที่ subscription item แต่ละตัวแทน (รองรับ
     * mixed-interval subscriptions) จึงต้องอ่านจาก item ตัวแรกแทนที่จะอ่านจาก subscription ตรง ๆ
     */
    private LocalDateTime periodEndOf(Subscription sub) {
        if (sub.getItems() != null && sub.getItems().getData() != null && !sub.getItems().getData().isEmpty()) {
            return toDateTime(sub.getItems().getData().get(0).getCurrentPeriodEnd());
        }
        return LocalDateTime.now().plusMonths(1);
    }
}
