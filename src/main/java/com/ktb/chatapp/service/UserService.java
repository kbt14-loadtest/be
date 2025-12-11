package com.ktb.chatapp.service;

import com.ktb.chatapp.dto.*;
import com.ktb.chatapp.model.User;
import com.ktb.chatapp.repository.UserRepository;
import com.ktb.chatapp.util.FileUtil;
import com.ktb.chatapp.util.image.ImageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final ImageUtils imageUtils;

    @Value("${app.profile.image.max-size:5242880}") // 5MB
    private long maxProfileImageSize;

    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList(
            "jpg", "jpeg", "png", "gif", "webp"
    );

    /**
     * 현재 사용자 프로필 조회
     * @param email 사용자 이메일
     */
    public UserResponse getCurrentUserProfile(String email) {
        User user = userRepository.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));
        return UserResponse.fromWithPresigned(user, imageUtils.generatePresignedUrlWithKey(user.getProfileImageKey(), Duration.ofHours(1)));
    }

    /**
     * 사용자 프로필 업데이트
     * @param email 사용자 이메일
     */
    public UserResponse updateUserProfile(String email, UpdateProfileRequest request) {
        User user = userRepository.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));

        // 프로필 정보 업데이트
        user.setName(request.getName());
        user.setUpdatedAt(LocalDateTime.now());

        User updatedUser = userRepository.save(user);
        log.info("사용자 프로필 업데이트 완료 - ID: {}, Name: {}", user.getId(), request.getName());

        return UserResponse.fromWithPresigned(updatedUser, imageUtils.generatePresignedUrlWithKey(user.getProfileImageKey(), Duration.ofHours(1)));
    }

    /**
     * 프로필 이미지 업로드
     * 프론트로부터 file, contentType 받아서 업로드용 presignedUrl 생성
     * @param email 사용자 이메일
     */
    public ProfileImageResponse uploadProfileImage(String email, UploadImageRequestDto uploadImageRequestDto) {
        // 사용자 조회
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));

//        // 파일 유효성 검증
//        validateProfileImageFile(file);

        // 기존 프로필 이미지 삭제
        if (user.getProfileImageKey() != null && !user.getProfileImageKey().isEmpty()) {
            deleteProfileImage(email);
        }

//        // 새 파일 저장 (보안 검증 포함)
//        String profileImageUrl = fileService.storeFile(file, "profiles");

        UploadImageResponseDto uploadImageResponseDto = imageUtils.generatePresignedUrl(uploadImageRequestDto);

        // 사용자 프로필 이미지 URL 업데이트
        String presignedImageUrl = uploadImageResponseDto.getPresignedImageUrl();
        String imageKey = uploadImageResponseDto.getImageKey();

        // DB profileImageKey 저장
        user.setProfileImageKey(imageKey);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        log.info("프로필 이미지 업로드 완료 - User ID: {}, File: {}", user.getId(), presignedImageUrl);

        return new ProfileImageResponse(
                true,
                "프로필 이미지가 업데이트되었습니다.",
                presignedImageUrl
        );

    }




//    public ProfileImageResponse uploadProfileImage(String email, MultipartFile file) {
//        // 사용자 조회
//        User user = userRepository.findByEmail(email.toLowerCase())
//                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));
//
//        // 파일 유효성 검증
//        validateProfileImageFile(file);
//
//        // 기존 프로필 이미지 삭제
//        if (user.getProfileImage() != null && !user.getProfileImage().isEmpty()) {
//            deleteOldProfileImage(user.getProfileImage());
//        }
//
//        // 새 파일 저장 (보안 검증 포함)
//        String profileImageUrl = fileService.storeFile(file, "profiles");
//
//        // 사용자 프로필 이미지 URL 업데이트
//        user.setProfileImage(profileImageUrl);
//        user.setUpdatedAt(LocalDateTime.now());
//        userRepository.save(user);
//
//        log.info("프로필 이미지 업로드 완료 - User ID: {}, File: {}", user.getId(), profileImageUrl);
//
//        return new ProfileImageResponse(
//                true,
//                "프로필 이미지가 업데이트되었습니다.",
//                profileImageUrl
//        );
//    }

    /**
     * 특정 사용자 프로필 조회
     */
    public UserResponse getUserProfile(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));

        return UserResponse.fromWithPresigned(user, imageUtils.generatePresignedUrlWithKey(user.getProfileImageKey(),Duration.ofHours(1)));
    }

    /**
     * 프로필 이미지 파일 유효성 검증
     */
    private void validateProfileImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("이미지가 제공되지 않았습니다.");
        }

        // 파일 크기 검증
        if (file.getSize() > maxProfileImageSize) {
            throw new IllegalArgumentException("파일 크기는 5MB를 초과할 수 없습니다.");
        }

        // Content-Type 검증
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("이미지 파일만 업로드할 수 있습니다.");
        }

        // 파일 확장자 검증 (보안을 위해 화이트리스트 유지)
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            throw new IllegalArgumentException("이미지 파일만 업로드할 수 있습니다.");
        }

        // FileSecurityUtil의 static 메서드 호출
        String extension = FileUtil.getFileExtension(originalFilename).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("이미지 파일만 업로드할 수 있습니다.");
        }
    }

//    /**
//     * 기존 프로필 이미지 삭제
//     * DB 삭제, Bucket에서 삭제
//     */
//    private void deleteOldProfileImage(String profileImageUrl) {
//        try {
//            if (profileImageUrl != null && profileImageUrl.startsWith("/uploads/")) {
//                // URL에서 파일명 추출
//                String filename = profileImageUrl.substring("/uploads/".length());
//                Path filePath = Paths.get(uploadDir, filename);
//
//                if (Files.exists(filePath)) {
//                    Files.delete(filePath);
//                    log.info("기존 프로필 이미지 삭제 완료: {}", filename);
//                }
//
//            }
//        } catch (IOException e) {
//            log.warn("기존 프로필 이미지 삭제 실패: {}", e.getMessage());
//        }
//    }

    /**
     * 프로필 이미지 삭제
     * @param email 사용자 이메일
     */
    public void deleteProfileImage(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("사용자가 존재하지 않습니다."));
        System.out.println(user.getEmail());

        // s3 bucket에서 이미지 삭제
        imageUtils.deleteImage(user.getProfileImageKey());
        // DB에서 이미지 key 삭제
        user.deleteProfileImage();
        userRepository.save(user);
    }
//    public void deleteProfileImage(String email) {
//        User user = userRepository.findByEmail(email.toLowerCase())
//                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));
//
//        if (user.getProfileImage() != null && !user.getProfileImage().isEmpty()) {
//            deleteOldProfileImage(user.getProfileImage());
//            user.setProfileImage("");
//            user.setUpdatedAt(LocalDateTime.now());
//            userRepository.save(user);
//            log.info("프로필 이미지 삭제 완료 - User ID: {}", user.getId());
//        }
//    }

    /**
     * 회원 탈퇴 처리
     * @param email 사용자 이메일
     */
    public void deleteUserAccount(String email) {
        User user = userRepository.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));

        if (user.getProfileImageKey() != null && !user.getProfileImageKey().isEmpty()) {
            deleteProfileImage(user.getProfileImageKey());
        }

        userRepository.delete(user);
        log.info("회원 탈퇴 완료 - User ID: {}", user.getId());
    }
}
