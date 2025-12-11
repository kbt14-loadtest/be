package com.ktb.chatapp.dto;

import com.ktb.chatapp.model.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserResponse {
    private String id;
    private String name;
    private String email;
    private String presignedProfileImage;

    // presigned URL 없이 기본 정보만 반환
    public static UserResponse from(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .presignedProfileImage(user.getProfileImageKey())
                .build();
    }

    // presigned URL 포함하여 반환
    public static UserResponse fromWithPresigned(User user, String presignedUrl) {
        return UserResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .presignedProfileImage(presignedUrl)
                .build();
    }
}
