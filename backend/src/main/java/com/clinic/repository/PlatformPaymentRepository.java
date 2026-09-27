package com.clinic.repository;

import com.clinic.domain.billing.PlatformPayment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformPaymentRepository extends JpaRepository<PlatformPayment, Long> {
    boolean existsByReference(String reference);
    List<PlatformPayment> findAllByOrderByPaidAtDesc();
}
