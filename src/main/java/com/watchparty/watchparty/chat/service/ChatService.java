package com.watchparty.watchparty.chat.service;

import com.watchparty.watchparty.chat.dto.ChatEventResponse;
import com.watchparty.watchparty.chat.dto.ChatMessageResponse;
import com.watchparty.watchparty.common.exception.AppException;
import com.watchparty.watchparty.common.exception.ErrorCode;
import com.watchparty.watchparty.common.storage.S3StorageService;
import com.watchparty.watchparty.user.dto.UserProfileSummary;
import com.watchparty.watchparty.user.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final UserProfileRepository userProfileRepository;
    private final S3StorageService s3StorageService;

    // ChatMessageResponse를 만들어 반환
    @Transactional(readOnly = true)
    public ChatEventResponse buildChatMessage(Long roomId, Long userId, String message){
        if(roomId == null){
            throw new AppException(ErrorCode.BAD_REQUEST);
        }
        if(userId == null){
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        String trimmed = message == null ? "" : message.trim();
        if(trimmed.isEmpty()){
            throw new AppException(ErrorCode.BAD_REQUEST);
        }

        // 닉네임 + 프로필 이미지 key를 한 번에 조회 (예전엔 여기서 쿼리 2번 나갔음)
        UserProfileSummary summary = userProfileRepository.findSummaryByUserId(userId)
                .orElse(new UserProfileSummary("Unkown", null));

        String nickname = summary.nickname();

        // 프로필 이미지(있으면 presigned GET URL, 없으면 null)
        String profileImageKey = summary.profileImageKey();
        String profileImageUrl = (profileImageKey == null || profileImageKey.isBlank())
                ? null
                : s3StorageService.createPresignedGetUrl(profileImageKey);

        log.debug("[chat] build done nickname={}, trimmedLen={}", nickname, trimmed.length());

        ChatMessageResponse chat =  ChatMessageResponse.builder()
                .roomId(roomId)
                .sendUserId(userId)
                .nickname(nickname)
                .profileImageUrl(profileImageUrl)
                .message(trimmed)
                .createdAt(LocalDateTime.now())
                .build();

        return ChatEventResponse.chat(roomId, chat);
    }
}
