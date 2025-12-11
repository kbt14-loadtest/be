package com.ktb.chatapp.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UploadImageRequestDto {

    private String fileName;
    private String contentType;

}
