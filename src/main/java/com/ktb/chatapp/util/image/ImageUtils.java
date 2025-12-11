package com.ktb.chatapp.util.image;

import com.ktb.chatapp.dto.UploadImageRequestDto;
import com.ktb.chatapp.dto.UploadImageResponseDto;
import lombok.RequiredArgsConstructor;
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
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ImageUtils {

    private final S3Presigner s3Presigner;
    private final S3Client s3Client;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    // 클라이언트로부터 파일명, content type 받아서 url 생성 => 업로드용 (PUT)
    public UploadImageResponseDto generatePresignedUrl(UploadImageRequestDto uploadImageRequestDto) {

        String filename = uploadImageRequestDto.getFileName();
        String contentType = uploadImageRequestDto.getContentType();

        String folder = "public/image/profile/";
        String imageKey = folder + UUID.randomUUID() + "-" + filename;

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

        return UploadImageResponseDto.builder()
                .presignedImageUrl(s3UploadUrl.toString())
                .imageKey(imageKey)
                .build();
    }

    // DB에 저장된 이미지 Key 값을 기반으로 url 생성 => 미리보기 조회용 (GET)
    public String generatePresignedUrlWithKey(String imageKey, Duration duration) {
        if (imageKey == null || imageKey.isEmpty()) return null;

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(imageKey)
                .build();

        GetObjectPresignRequest getPresignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(duration)
                .getObjectRequest(getObjectRequest)
                .build();

        return s3Presigner.presignGetObject(getPresignRequest).url().toString();
    }

    public void deleteImage(String imageKey) {
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(imageKey)
                .build());
    }

}

