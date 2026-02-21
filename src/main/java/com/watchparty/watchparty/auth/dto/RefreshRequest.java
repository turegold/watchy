package com.watchparty.watchparty.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;


// 클라이언트가 /api/auth/refresh로 보낼 요청 바디 DTO
@Getter
@NoArgsConstructor
public class RefreshRequest {

    @NotBlank(message = "refreshToken은 필수입니다.")
    private String refreshToken;
}
