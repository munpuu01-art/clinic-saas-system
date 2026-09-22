package com.clinic.domain;

import com.clinic.domain.billing.Plan;
import com.clinic.domain.billing.Subscription;
import com.clinic.domain.billing.SubscriptionStatus;
import com.clinic.domain.tenant.Clinic;
import com.clinic.domain.tenant.ClinicStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/** TC-34..TC-36 : ทดสอบวงจรชีวิตของคลินิก (tenant) และการสมัครสมาชิก */
class ClinicTest {

    @Test
    @DisplayName("TC-34 คลินิกใหม่เริ่มที่สถานะทดลองใช้งาน และใช้งานได้ทันที")
    void newClinicStartsTrialing() {
        Clinic clinic = new Clinic("คลินิกทดสอบ", "test-clinic", "owner@test.com", null);
        assertEquals(ClinicStatus.TRIALING, clinic.getStatus());
        assertTrue(clinic.canAcceptTraffic());
        assertNotNull(clinic.getTrialEndsAt());
    }

    @Test
    @DisplayName("TC-35 คลินิกที่ถูกระงับต้องเข้าใช้งานไม่ได้")
    void suspendedClinicCannotAcceptTraffic() {
        Clinic clinic = new Clinic("คลินิกทดสอบ", "test-clinic-2", "owner@test.com", null);
        clinic.suspend();
        assertFalse(clinic.canAcceptTraffic());
        assertEquals(ClinicStatus.SUSPENDED, clinic.getStatus());
    }

    @Test
    @DisplayName("TC-36 ชำระเงินผ่าน Stripe สำเร็จต้องเปลี่ยนสถานะการสมัครสมาชิกเป็นใช้งานปกติ")
    void checkoutActivatesSubscription() {
        Plan plan = new Plan("BASIC", "เริ่มต้น", BigDecimal.valueOf(990), 3, 300, "แพ็กเกจเริ่มต้น", "price_basic");
        Subscription subscription = new Subscription(1L, plan, SubscriptionStatus.TRIALING);

        subscription.applyStripeCheckout("cus_123", "sub_123",
                LocalDateTime.now(), LocalDateTime.now().plusMonths(1));

        assertEquals(SubscriptionStatus.ACTIVE, subscription.getStatus());
        assertEquals("cus_123", subscription.getStripeCustomerId());
        assertEquals("sub_123", subscription.getStripeSubscriptionId());
    }
}
