package com.example.demo.transport.repository;

import com.example.demo.transport.entity.Route;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface RouteRepository extends JpaRepository<Route, Long> {
    List<Route> findTop10ByUserIdAndLanguageOrderBySearchedAtDesc(String userId, String language);
    List<Route> findByStartPlaceContaining(String startPlace);
    List<Route> findByEndPlaceContaining(String endPlace);

    @Modifying
    @Transactional
    void deleteByUserId(String userId);
}