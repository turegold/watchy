package com.watchparty.watchparty.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class ProfileImageUploadUrlRequest {

    // 업로드할 이미지의 MIME 타입 (예: image/png, image/jpeg). image/* 만 허용
    @NotBlank(message = "contentType은 필수입니다.")
    private String contentType;
}
