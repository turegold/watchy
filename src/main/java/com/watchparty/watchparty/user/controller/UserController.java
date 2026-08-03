package com.watchparty.watchparty.user.controller;

import com.watchparty.watchparty.common.exception.AppException;
import com.watchparty.watchparty.common.exception.ErrorCode;
import com.watchparty.watchparty.common.response.ApiResponse;
import com.watchparty.watchparty.user.dto.MyInfoResponse;
import com.watchparty.watchparty.user.dto.ProfileImageUploadUrlRequest;
import com.watchparty.watchparty.user.dto.ProfileImageUploadUrlResponse;
import com.watchparty.watchparty.user.dto.UpdateNicknameRequest;
import com.watchparty.watchparty.user.dto.UpdateProfileImageRequest;
import com.watchparty.watchparty.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ApiResponse<MyInfoResponse> getMyInfo(Authentication authentication) {
        Long userId = extractUserId(authentication);
        MyInfoResponse response = userService.getMyInfo(userId);
        return ApiResponse.ok("내 정보 조회에 성공했습니다.", response);
    }

    @PatchMapping("/users/nickname")
    public ApiResponse<Void> updateNickname(Authentication authentication,
                                            @Valid @RequestBody UpdateNicknameRequest request) {
        Long userId = extractUserId(authentication);
        userService.updateNickname(userId, request);
        return ApiResponse.okMessage("닉네임이 수정되었습니다.");
    }

    // 프로필 이미지 업로드용 presigned URL 발급
    @PostMapping("/users/profile-image/upload-url")
    public ApiResponse<ProfileImageUploadUrlResponse> createProfileImageUploadUrl(
            Authentication authentication,
            @Valid @RequestBody ProfileImageUploadUrlRequest request) {
        Long userId = extractUserId(authentication);
        ProfileImageUploadUrlResponse response =
                userService.createProfileImageUploadUrl(userId, request.getContentType());
        return ApiResponse.ok("업로드 URL이 발급되었습니다.", response);
    }

    // 업로드 완료 후 프로필 이미지 확정
    @PutMapping("/users/profile-image")
    public ApiResponse<Void> updateProfileImage(
            Authentication authentication,
            @Valid @RequestBody UpdateProfileImageRequest request) {
        Long userId = extractUserId(authentication);
        userService.updateProfileImage(userId, request.getKey());
        return ApiResponse.okMessage("프로필 이미지가 변경되었습니다.");
    }

    private Long extractUserId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof Long userId) {
            return userId;
        }

        if (principal instanceof String principalString) {
            try {
                return Long.parseLong(principalString);
            } catch (NumberFormatException ignored) {
                throw new AppException(ErrorCode.UNAUTHORIZED);
            }
        }

        throw new AppException(ErrorCode.UNAUTHORIZED);
    }
}
