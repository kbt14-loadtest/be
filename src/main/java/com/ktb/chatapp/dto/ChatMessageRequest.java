package com.ktb.chatapp.dto;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageRequest {

    private String room;
    private String type;
    private String content;
    private String msg;
    private Map<String, Object> fileData;


    public String getNormalizedContent() {
        if (content != null && !content.trim().isEmpty()) {
            return content;
        }
        return msg != null ? msg : "";
    }
    

    public MessageContent getParsedContent() {
        return MessageContent.from(getNormalizedContent());
    }


    public String getMessageType() {
        return type != null ? type : "text";
    }


    public boolean hasFileData() {
        return fileData != null && !fileData.isEmpty();
    }

    public String getRoom() {
        if (room == null || room.trim().isEmpty()) {
            throw new IllegalArgumentException("채팅방 정보가 없습니다.");
        }
        return room;
    }
}
