package com.example.demo.point.repository;

import com.example.demo.point.entity.PointCharge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PointChargeRepository extends JpaRepository<PointCharge, Long> {

    Optional<PointCharge> findByMerchantUid(String merchantUid);

    List<PointCharge> findTop10ByUserIdOrderByCreatedAtDesc(Long userId);
}
