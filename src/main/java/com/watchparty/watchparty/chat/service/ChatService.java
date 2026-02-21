package com.watchparty.watchparty.chat.service;

import com.watchparty.watchparty.chat.dto.ChatMessageResponse;
import com.watchparty.watchparty.common.exception.AppException;
import com.watchparty.watchparty.common.exception.ErrorCode;
import com.watchparty.watchparty.user.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final UserProfileRepository userProfileRepository;

    // ChatMessageResponse를 만들어 반환
    @Transactional(readOnly = true)
    public ChatMessageResponse buildChatMessage(Long roomId, Long userId, String message){
        if(roomId == null){
            throw new AppException(ErrorCode.BAD_REQUEST);
        }
        if(userId == null){
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        if(message == null || message.trim().isEmpty()){
            throw new AppException(ErrorCode.BAD_REQUEST);
        }

        // 닉네임 조회
        String nickname = userProfileRepository.findNicknameByUserId(userId)
                .orElse("Unkown");

        return ChatMessageResponse.builder()
                .roomId(roomId)
                .sendUserId(userId)
                .nickname(nickname)
                .message(message)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
