package com.clinic.controller;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** endpoint สำหรับบริการ uptime ping (เช่น cron-job.org) — ไม่แตะฐานข้อมูล */
@RestController
public class PingController {
    @GetMapping("/api/ping")
    public Map<String, String> ping() {
        return Map.of("status", "ok");
    }
}
