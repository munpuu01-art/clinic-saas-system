package com.clinic.security;

import com.clinic.domain.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * ตั้งค่า "คลินิกปัจจุบัน" ให้กับเธรดที่กำลังประมวลผล request นี้ — ทำงานหลัง
 * JwtAuthenticationFilter เสมอ (ต้องรู้ตัวตนผู้ใช้ก่อนถึงจะรู้ว่าเป็นคลินิกไหน)
 *
 * สำคัญมาก: ต้องล้างค่าใน finally ทุกครั้ง เพราะ Servlet container ใช้เธรดซ้ำจาก thread pool
 * ถ้าลืมล้าง คำขอถัดไปที่ใช้เธรดเดียวกันอาจติด clinicId ของคนก่อนหน้าไปโดยไม่ตั้งใจ
 */
@Component
public class TenantContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof AppUserPrincipal principal) {
                TenantContext.set(principal.getClinicId());   // null สำหรับ SUPER_ADMIN โดยตั้งใจ
            }
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
