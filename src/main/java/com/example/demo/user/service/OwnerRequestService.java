package com.example.demo.user.service;

import com.example.demo.user.entity.OwnerRequest;
import com.example.demo.user.entity.OwnerRequestStatus;
import com.example.demo.user.entity.User;
import com.example.demo.user.repository.OwnerRequestRepository;
import com.example.demo.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OwnerRequestService {

    private final OwnerRequestRepository ownerRequestRepository;
    private final UserRepository userRepository;

    @Value("${owner-application.upload-dir:uploads/owner-requests}")
    private String uploadDir;

    @Transactional(readOnly = true)
    public Optional<OwnerRequest> findLatestByUserId(Long userId) {
        return ownerRequestRepository.findTopByUser_IdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public List<OwnerRequest> findPendingRequests() {
        return ownerRequestRepository.findByStatusOrderByCreatedAtAsc(OwnerRequestStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public List<OwnerRequest> findRequestsByStatus(OwnerRequestStatus status) {
        if (status == OwnerRequestStatus.PENDING) {
            return findPendingRequests();
        }
        return ownerRequestRepository.findByStatusOrderByReviewedAtDescCreatedAtDesc(status);
    }

    @Transactional(readOnly = true)
    public Path findDocumentPath(Long requestId, String documentType) {
        OwnerRequest request = ownerRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사업자 신청입니다."));
        String storedPath = switch (documentType) {
            case "registration" -> request.getBusinessRegistrationFilePath();
            case "license" -> request.getBusinessLicenseFilePath();
            case "menu" -> request.getMenuFilePath();
            default -> throw new IllegalArgumentException("지원하지 않는 사업자 서류입니다.");
        };
        if (!StringUtils.hasText(storedPath)) {
            throw new IllegalArgumentException("등록된 사업자 서류가 없습니다.");
        }

        Path uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path documentPath = Paths.get(storedPath).toAbsolutePath().normalize();
        if (!documentPath.startsWith(uploadRoot) || !Files.isRegularFile(documentPath)) {
            throw new IllegalArgumentException("사업자 서류를 찾을 수 없습니다.");
        }
        return documentPath;
    }

    @Transactional
    public OwnerRequest apply(Long userId,
                              String storeName,
                              String businessRegistrationNumber,
                              MultipartFile businessRegistrationFile,
                              MultipartFile businessLicenseFile,
                              MultipartFile menuFile) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        if ("OWNER".equals(user.getRole())) {
            throw new IllegalStateException("이미 사업자 권한이 있는 계정입니다.");
        }
        if (ownerRequestRepository.existsByUser_IdAndStatus(userId, OwnerRequestStatus.PENDING)) {
            throw new IllegalStateException("이미 검토 중인 사업자 신청이 있습니다.");
        }

        String normalizedStoreName = requireText(storeName, "가게명을 입력해 주세요.");
        String normalizedBusinessNumber = requireText(businessRegistrationNumber, "사업자등록번호를 입력해 주세요.");
        requireFile(businessRegistrationFile, "사업자등록증을 등록해 주세요.");
        requireFile(businessLicenseFile, "영업신고증을 등록해 주세요.");
        requireFile(menuFile, "메뉴판 또는 메뉴 정보를 등록해 주세요.");

        return ownerRequestRepository.save(OwnerRequest.builder()
                .user(user)
                .storeName(normalizedStoreName)
                .businessRegistrationNumber(normalizedBusinessNumber)
                .businessRegistrationFilePath(storeFile(userId, businessRegistrationFile))
                .businessLicenseFilePath(storeFile(userId, businessLicenseFile))
                .menuFilePath(storeFile(userId, menuFile))
                .status(OwnerRequestStatus.PENDING)
                .build());
    }

    @Transactional
    public void approve(Long requestId, String reviewComment) {
        OwnerRequest request = ownerRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사업자 신청입니다."));
        request.setStatus(OwnerRequestStatus.APPROVED);
        request.setReviewComment(reviewComment);
        request.setReviewedAt(LocalDateTime.now());

        User user = request.getUser();
        user.setRole("OWNER");
        user.setPoint(Math.max(user.getPoint(), 1000));
    }

    @Transactional
    public void reject(Long requestId, String reviewComment) {
        OwnerRequest request = ownerRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사업자 신청입니다."));
        request.setStatus(OwnerRequestStatus.REJECTED);
        request.setReviewComment(reviewComment);
        request.setReviewedAt(LocalDateTime.now());
    }

    private String storeFile(Long userId, MultipartFile file) {
        String originalName = StringUtils.cleanPath(file.getOriginalFilename() == null ? "document" : file.getOriginalFilename());
        String extension = "";
        int dotIndex = originalName.lastIndexOf('.');
        if (dotIndex >= 0) {
            extension = originalName.substring(dotIndex);
        }

        Path directory = Paths.get(uploadDir, String.valueOf(userId), LocalDate.now().toString());
        String fileName = UUID.randomUUID() + extension;
        Path destination = directory.resolve(fileName).normalize();

        try {
            Files.createDirectories(directory);
            file.transferTo(destination);
            return destination.toString().replace('\\', '/');
        } catch (IOException ex) {
            throw new IllegalStateException("사업자 서류 저장에 실패했습니다.", ex);
        }
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private void requireFile(MultipartFile file, String message) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
    }
}
