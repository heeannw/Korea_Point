package com.example.demo.reservation.repository;

import com.example.demo.reservation.entity.ReservationPayment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationPaymentRepository extends JpaRepository<ReservationPayment, Long> {
    java.util.Optional<ReservationPayment> findByMerchantUid(String merchantUid);
    interface Route {
        Long getReservationId();
        Long getUserId();
    }
    @org.springframework.data.jpa.repository.Query("select p.reservation.id as reservationId, p.reservation.userId as userId from ReservationPayment p where p.merchantUid = :merchantUid or (length(p.merchantUid) = 43 and length(:merchantUid) = 40 and substring(p.merchantUid, 1, 40) = :merchantUid)")
    java.util.Optional<Route> findRouteByMerchantUid(@org.springframework.data.repository.query.Param("merchantUid") String merchantUid);
}
