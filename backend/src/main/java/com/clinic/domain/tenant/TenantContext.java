package com.clinic.domain.tenant;

/**
 * เก็บ "คลินิกปัจจุบัน" ของ request ที่กำลังประมวลผลอยู่ในเธรดนี้ (ThreadLocal)
 * ถูกตั้งค่าโดย TenantContextFilter ทันทีหลังยืนยันตัวตนสำเร็จ และต้องล้างค่าทุกครั้ง
 * ที่ request จบ (ดู finally ใน TenantContextFilter) เพราะเธรดถูกใช้ซ้ำใน thread pool
 */
public final class TenantContext {

    private static final ThreadLocal<Long> CURRENT_CLINIC = new ThreadLocal<>();

    private TenantContext() { }

    public static void set(Long clinicId) {
        CURRENT_CLINIC.set(clinicId);
    }

    /** คืนค่า null ถ้าไม่มีคลินิกปัจจุบัน (เช่น บัญชี SUPER_ADMIN ที่มองเห็นทุกคลินิก) */
    public static Long currentOrNull() {
        return CURRENT_CLINIC.get();
    }

    public static boolean hasTenant() {
        return CURRENT_CLINIC.get() != null;
    }

    public static void clear() {
        CURRENT_CLINIC.remove();
    }
}
