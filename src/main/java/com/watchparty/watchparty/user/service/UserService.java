package com.watchparty.watchparty.user.service;

import com.watchparty.watchparty.common.exception.AppException;
import com.watchparty.watchparty.common.exception.ErrorCode;
import com.watchparty.watchparty.user.dto.MyInfoResponse;
import com.watchparty.watchparty.user.dto.UpdateNicknameRequest;
import com.watchparty.watchparty.user.entity.User;
import com.watchparty.watchparty.user.entity.UserProfile;
import com.watchparty.watchparty.user.repository.UserProfileRepository;
import com.watchparty.watchparty.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;

    @Transactional(readOnly = true)
    public MyInfoResponse getMyInfo(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        UserProfile profile = userProfileRepository.findByUser(user)
                .orElseThrow(() -> new AppException(ErrorCode.PROFILE_NOT_FOUND));

        return new MyInfoResponse(
                user.getId(),
                user.getEmail(),
                profile.getNickname(),
                profile.getLevel(),
                profile.getExp()
        );
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
