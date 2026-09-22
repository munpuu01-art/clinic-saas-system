package com.clinic.repository;

import com.clinic.domain.billing.Plan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlanRepository extends JpaRepository<Plan, Long> {
    List<Plan> findByActiveTrueOrderByPriceMonthlyThbAsc();
    Optional<Plan> findByCodeIgnoreCase(String code);
}
