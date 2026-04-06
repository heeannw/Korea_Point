package com.example.demo.transport.repository;

import com.example.demo.transport.entity.Route;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RouteRepository extends JpaRepository<Route, Long> {

    // 최근 검색 기록 10개
    List<Route> findTop10ByOrderBySearchedAtDesc();

    // 출발지로 검색
    List<Route> findByStartPlaceContaining(String startPlace);

    // 도착지로 검색
    List<Route> findByEndPlaceContaining(String endPlace);
}