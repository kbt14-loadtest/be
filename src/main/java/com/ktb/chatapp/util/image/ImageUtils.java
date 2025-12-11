package com.ktb.chatapp.util.image;

import com.ktb.chatapp.dto.UploadImageRequestDto;
import com.ktb.chatapp.dto.UploadImageResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URL;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageUtils {

    private final S3Presigner s3Presigner;
    private final S3Client s3Client;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    // presigned URL 캐시 (30분 TTL)
    private final Map<String, CachedPresignedUrl> urlCache = new ConcurrentHashMap<>();

    private static class CachedPresignedUrl {
        final String url;
        final long expiryTime;

        CachedPresignedUrl(String url, long expiryTime) {
            this.url = url;
            this.expiryTime = expiryTime;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiryTime;
        }
    }

    // 클라이언트로부터 파일명, content type 받아서 url 생성 => 업로드용 (PUT) - 프로필 이미지
    public UploadImageResponseDto generatePresignedUrl(UploadImageRequestDto uploadImageRequestDto) {

        String filename = uploadImageRequestDto.getFileName();
        String contentType = uploadImageRequestDto.getContentType();

        String folder = "public/image/profile/";
        String imageKey = folder + UUID.randomUUID() + "-" + filename;

        log.info("Generating presigned URL for profile image - filename: {}, contentType: {}, key: {}",
                filename, contentType, imageKey);

        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(imageKey)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(5))
                .putObjectRequest(objectRequest)
                .build();

        URL s3UploadUrl = s3Presigner.presignPutObject(presignRequest).url();

        log.info("Presigned URL generated successfully for profile image - key: {}", imageKey);

        return UploadImageResponseDto.builder()
                .presignedImageUrl(s3UploadUrl.toString())
                .imageKey(imageKey)
                .build();
    }

    // 채팅방 파일 업로드용 presigned URL 생성
    public UploadImageResponseDto generatePresignedUrlForChatFile(String filename, String contentType) {
        String folder = "public/chat/files/";
        String fileKey = folder + UUID.randomUUID() + "-" + filename;

        log.info("Generating presigned URL for chat file - filename: {}, contentType: {}, key: {}",
                filename, contentType, fileKey);

        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(fileKey)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(5))
                .putObjectRequest(objectRequest)
                .build();

        URL s3UploadUrl = s3Presigner.presignPutObject(presignRequest).url();

        log.info("Presigned URL generated successfully for chat file - key: {}", fileKey);

        return UploadImageResponseDto.builder()
                .presignedImageUrl(s3UploadUrl.toString())
                .imageKey(fileKey)
                .build();
    }

    // DB에 저장된 이미지 Key 값을 기반으로 url 생성 => 미리보기 조회용 (GET)
    public String generatePresignedUrlWithKey(String imageKey, Duration duration) {
        if (imageKey == null || imageKey.isEmpty()) {
            log.debug("imageKey is null or empty, returning null");
            return null;
        }

        // 캐시 확인
        String cacheKey = imageKey + ":" + duration.toMinutes();
        CachedPresignedUrl cached = urlCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            log.debug("Presigned URL retrieved from cache - key: {}", imageKey);
            return cached.url;
        }

        log.debug("Generating presigned GET URL - key: {}, duration: {}", imageKey, duration);

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(imageKey)
                .build();

        GetObjectPresignRequest getPresignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(duration)
                .getObjectRequest(getObjectRequest)
                .build();

        String presignedUrl = s3Presigner.presignGetObject(getPresignRequest).url().toString();

        // 캐시에 저장 (URL 만료 시간보다 5분 일찍 만료되도록 설정)
        long cacheExpiryTime = System.currentTimeMillis() + duration.toMillis() - Duration.ofMinutes(5).toMillis();
        urlCache.put(cacheKey, new CachedPresignedUrl(presignedUrl, cacheExpiryTime));

        log.debug("Presigned GET URL generated and cached - key: {}", imageKey);

        return presignedUrl;
    }

    public void deleteImage(String imageKey) {
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(imageKey)
                .build());

        // 캐시에서도 제거
        urlCache.keySet().removeIf(key -> key.startsWith(imageKey + ":"));
        log.debug("Image deleted and cache cleared - key: {}", imageKey);
    }

    /**
     * 만료된 캐시 엔트리 정리
     */
    public void cleanExpiredCache() {
        int beforeSize = urlCache.size();
        urlCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        int afterSize = urlCache.size();
        if (beforeSize > afterSize) {
            log.info("Cleaned expired cache entries - removed: {}, remaining: {}", beforeSize - afterSize, afterSize);
        }
    }
}

