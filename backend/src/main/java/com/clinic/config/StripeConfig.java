package com.clinic.config;

import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * ตั้งค่า Stripe SDK ทั้งหมดไว้ที่เดียว — อ่านค่าลับจาก environment variable เสมอ
 * (ห้าม commit API key ลงโค้ดเด็ดขาด)
 *
 * ต้องตั้ง environment variable เหล่านี้ก่อนใช้งานจริง:
 *   STRIPE_SECRET_KEY      คีย์ลับของบัญชี Stripe (ขึ้นต้นด้วย sk_test_ หรือ sk_live_)
 *   STRIPE_WEBHOOK_SECRET  secret สำหรับตรวจลายเซ็น webhook (ขึ้นต้นด้วย whsec_)
 *   STRIPE_PRICE_BASIC     Price ID ของแพ็กเกจ Basic (price_xxx) ที่สร้างไว้ใน Stripe Dashboard
 *   STRIPE_PRICE_PRO       Price ID ของแพ็กเกจ Pro
 *   FRONTEND_URL           โดเมนของ frontend ใช้สร้างลิงก์ redirect กลับหลังชำระเงิน
 *
 * ถ้าไม่ตั้งค่า STRIPE_SECRET_KEY ระบบยังรันได้ปกติ (แพ็กเกจฟรียังสมัครได้) เพียงแต่
 * การซื้อแพ็กเกจแบบชำระเงินจะใช้งานไม่ได้จนกว่าจะตั้งค่าให้ครบ
 */
@Component
public class StripeConfig {

    @Value("${STRIPE_SECRET_KEY:}")
    private String secretKey;

    @Value("${STRIPE_WEBHOOK_SECRET:}")
    private String webhookSecret;

    @Value("${FRONTEND_URL:http://localhost:5173}")
    private String frontendUrl;

    @PostConstruct
    public void init() {
        if (secretKey != null && !secretKey.isBlank()) {
            Stripe.apiKey = secretKey;
        }
    }

    public boolean isConfigured() { return secretKey != null && !secretKey.isBlank(); }
    public String getWebhookSecret() { return webhookSecret; }
    public String getSuccessUrl() { return frontendUrl + "/register/success?session_id={CHECKOUT_SESSION_ID}"; }
    public String getCancelUrl() { return frontendUrl + "/register/cancel"; }
}
