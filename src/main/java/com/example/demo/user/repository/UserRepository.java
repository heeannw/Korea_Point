package com.example.demo.user.repository;

import com.example.demo.user.entity.AccountStatus;
import com.example.demo.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // OAuth 濡쒓렇????湲곗〈 ?좎? 議고쉶 (provider + providerId 議고빀)
    Optional<User> findByProviderAndProviderId(String provider, String providerId);

    Optional<User> findByEmail(String email);

    List<User> findByProviderAndEmailIgnoreCase(String provider, String email);

    boolean existsByProviderAndProviderId(String provider, String providerId);

    @Query("""
        SELECT u FROM User u
        WHERE (:keyword IS NULL OR :keyword = '' OR
               LOWER(u.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
               LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')))
          AND (:role IS NULL OR u.role = :role)
          AND (:provider IS NULL OR u.provider = :provider)
          AND (:accountStatus IS NULL OR u.accountStatus = :accountStatus)
          AND (:createdFrom IS NULL OR u.createdAt >= :createdFrom)
          AND (:createdTo IS NULL OR u.createdAt < :createdTo)
    """)
    Page<User> searchForAdmin(@Param("keyword") String keyword,
                              @Param("role") String role,
                              @Param("provider") String provider,
                              @Param("accountStatus") AccountStatus accountStatus,
                              @Param("createdFrom") java.time.LocalDateTime createdFrom,
                              @Param("createdTo") java.time.LocalDateTime createdTo,
                              Pageable pageable);

    long countByRole(String role);

    long countByAccountStatus(AccountStatus accountStatus);

    List<User> findTop5ByOrderByCreatedAtDesc();

    // ?ъ씤??利앷컧 (delta ?묒닔: 異⑹쟾, ?뚯닔: 李④컧)
    @Modifying
    @Query("UPDATE User u SET u.point = u.point + :delta WHERE u.id = :id")
    void updatePoint(@Param("id") Long id, @Param("delta") int delta);
}
