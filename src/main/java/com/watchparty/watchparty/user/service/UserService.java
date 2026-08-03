package com.watchparty.watchparty.user.service;

import com.watchparty.watchparty.common.exception.AppException;
import com.watchparty.watchparty.common.exception.ErrorCode;
import com.watchparty.watchparty.common.storage.S3StorageService;
import com.watchparty.watchparty.user.dto.MyInfoResponse;
import com.watchparty.watchparty.user.dto.ProfileImageUploadUrlResponse;
import com.watchparty.watchparty.user.dto.UpdateNicknameRequest;
import com.watchparty.watchparty.user.entity.User;
import com.watchparty.watchparty.user.entity.UserProfile;
import com.watchparty.watchparty.user.repository.UserProfileRepository;
import com.watchparty.watchparty.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final String PROFILE_IMAGE_PREFIX = "profile-images/";

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final S3StorageService s3StorageService;

    @Transactional(readOnly = true)
    public MyInfoResponse getMyInfo(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        UserProfile profile = userProfileRepository.findByUser(user)
                .orElseThrow(() -> new AppException(ErrorCode.PROFILE_NOT_FOUND));

        String imageUrl = profile.getProfileImageKey() == null
                ? null
                : s3StorageService.createPresignedGetUrl(profile.getProfileImageKey());

        return new MyInfoResponse(
                user.getId(),
                user.getEmail(),
                profile.getNickname(),
                profile.getLevel(),
                profile.getExp(),
                imageUrl
        );
    }

    // 1) 프로필 이미지 업로드용 presigned PUT URL 발급
    @Transactional(readOnly = true)
    public ProfileImageUploadUrlResponse createProfileImageUploadUrl(Long userId, String contentType) {
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new AppException(ErrorCode.INVALID_IMAGE_TYPE);
        }
        // key는 유저별 prefix로 격리 (확정 단계에서 소유권 검증에도 사용)
        String key = PROFILE_IMAGE_PREFIX + userId + "/" + UUID.randomUUID();
        String uploadUrl = s3StorageService.createPresignedPutUrl(key, contentType);
        return new ProfileImageUploadUrlResponse(key, uploadUrl);
    }

    // 2) 업로드 완료 후 key를 프로필에 확정 저장 (+ 이전 이미지 삭제)
    @Transactional
    public void updateProfileImage(Long userId, String key) {
        // 남의 prefix key를 넣지 못하도록 소유권 검증
        if (key == null || !key.startsWith(PROFILE_IMAGE_PREFIX + userId + "/")) {
            throw new AppException(ErrorCode.INVALID_IMAGE_KEY);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        UserProfile profile = userProfileRepository.findByUser(user)
                .orElseThrow(() -> new AppException(ErrorCode.PROFILE_NOT_FOUND));

        String previousKey = profile.updateProfileImageKey(key);
        if (previousKey != null && !previousKey.equals(key)) {
            s3StorageService.deleteObject(previousKey);
        }
    }

    @Transactional
    public void updateNickname(Long userId, UpdateNicknameRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        UserProfile profile = userProfileRepository.findByUser(user)
                .orElseThrow(() -> new AppException(ErrorCode.PROFILE_NOT_FOUND));

        String nickname = request.getNickname() == null ? null : request.getNickname().trim();
        if (nickname == null || nickname.length() < 2 || nickname.length() > 50) {
            throw new AppException(ErrorCode.INVALID_NICKNAME);
        }

        userProfileRepository.findByNickname(nickname)
                .filter(existing -> !existing.getUser().getId().equals(userId))
                .ifPresent(existing -> {
                    throw new AppException(ErrorCode.DUPLICATE_NICKNAME);
                });

        profile.updateNickname(nickname);
    }
}
