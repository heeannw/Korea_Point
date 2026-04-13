package com.example.demo.user.repository;

import com.example.demo.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // OAuth 로그인 시 기존 유저 조회 (provider + providerId 조합)
    Optional<User> findByProviderAndProviderId(String provider, String providerId);

    Optional<User> findByEmail(String email);

    // 포인트 증감 (delta 양수: 충전, 음수: 차감)
    @Modifying
    @Query("UPDATE User u SET u.point = u.point + :delta WHERE u.id = :id")
    void updatePoint(@Param("id") Long id, @Param("delta") int delta);
}
