package com.watchparty.watchparty.user.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ProfileImageUploadUrlResponse {
    private String key;        // 업로드 후 확정 요청에 그대로 돌려줄 객체 key
    private String uploadUrl;  // 클라이언트가 이 URL로 S3에 직접 PUT
}
