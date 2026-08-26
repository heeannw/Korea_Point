package com.example.demo.user.repository;

import com.example.demo.user.entity.OwnerRequest;
import com.example.demo.user.entity.OwnerRequestStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OwnerRequestRepository extends JpaRepository<OwnerRequest, Long> {

    Optional<OwnerRequest> findTopByUser_IdOrderByCreatedAtDesc(Long userId);

    boolean existsByUser_IdAndStatus(Long userId, OwnerRequestStatus status);

    @EntityGraph(attributePaths = "user")
    List<OwnerRequest> findByStatusOrderByCreatedAtAsc(OwnerRequestStatus status);
}
