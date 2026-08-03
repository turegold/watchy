package com.watchparty.watchparty.user.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MyInfoResponse {
    private Long id;
    private String email;
    private String nickname;
    private Integer level;
    private Long exp;
    private String profileImageUrl;  // presigned GET URL. 이미지 없으면 null
}
