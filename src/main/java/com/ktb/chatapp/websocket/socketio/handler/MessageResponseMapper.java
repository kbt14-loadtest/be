package com.ktb.chatapp.websocket.socketio.handler;

import com.ktb.chatapp.dto.FileResponse;
import com.ktb.chatapp.dto.MessageResponse;
import com.ktb.chatapp.dto.UserResponse;
import com.ktb.chatapp.model.Message;
import com.ktb.chatapp.model.User;
import com.ktb.chatapp.repository.FileRepository;
import com.ktb.chatapp.util.image.ImageUtils;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 메시지를 응답 DTO로 변환하는 매퍼
 * 파일 정보, 사용자 정보 등을 포함한 MessageResponse 생성
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageResponseMapper {

    private final FileRepository fileRepository;
    private final ImageUtils imageUtils;

    /**
     * Message 엔티티를 MessageResponse DTO로 변환
     *
     * @param message 변환할 메시지 엔티티
     * @param sender 메시지 발신자 정보 (null 가능)
     * @return MessageResponse DTO
     */
    public MessageResponse mapToMessageResponse(Message message, User sender, String presignedProfileUrl) {
        MessageResponse.MessageResponseBuilder builder = MessageResponse.builder()
                .id(message.getId())
                .content(message.getContent())
                .type(message.getType())
                .timestamp(message.toTimestampMillis())
                .roomId(message.getRoomId())
                .reactions(message.getReactions() != null ?
                        message.getReactions() : new HashMap<>())
                .readers(message.getReaders() != null ?
                        message.getReaders() : new ArrayList<>());

        // 발신자 정보 설정
        if (sender != null) {
            builder.sender(UserResponse.builder()
                    .id(sender.getId())
                    .name(sender.getName())
                    .email(sender.getEmail())
                    .presignedProfileImage(presignedProfileUrl)
                    .build());
        }

        // 파일 정보 설정
        Optional.ofNullable(message.getFileId())
                .flatMap(fileRepository::findById)
                .map(file -> {
                    try {
                        // S3 presigned URL 생성
                        String presignedUrl = null;
                        if (file.getPath() != null && !file.getPath().isEmpty()) {
                            presignedUrl = imageUtils.generatePresignedUrlWithKey(file.getPath(), Duration.ofHours(1));
                            log.debug("Generated presigned URL for file: {}", file.getId());
                        } else {
                            log.warn("File {} has no path, skipping presigned URL generation", file.getId());
                        }

                        return FileResponse.builder()
                                .id(file.getId())
                                .filename(file.getFilename())
                                .originalname(file.getOriginalname())
                                .mimetype(file.getMimetype())
                                .size(file.getSize())
                                .presignedUrl(presignedUrl)
                                .build();
                    } catch (Exception e) {
                        log.error("Error generating presigned URL for file: {}", file.getId(), e);
                        // 에러가 발생해도 파일 정보는 반환 (presignedUrl만 null)
                        return FileResponse.builder()
                                .id(file.getId())
                                .filename(file.getFilename())
                                .originalname(file.getOriginalname())
                                .mimetype(file.getMimetype())
                                .size(file.getSize())
                                .presignedUrl(null)
                                .build();
                    }
                })
                .ifPresent(builder::file);

        // 메타데이터 설정
        if (message.getMetadata() != null) {
            builder.metadata(message.getMetadata());
        }

        return builder.build();
    }
}
