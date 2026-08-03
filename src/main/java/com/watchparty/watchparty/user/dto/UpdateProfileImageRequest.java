package com.watchparty.watchparty.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class UpdateProfileImageRequest {

    // presigned-url 발급 시 받았던 그 객체 key
    @NotBlank(message = "key는 필수입니다.")
    private String key;
}
