package com.example.demo.reservation.repository;

import com.example.demo.reservation.entity.ReservationPayment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationPaymentRepository extends JpaRepository<ReservationPayment, Long> {
}
