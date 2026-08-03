package com.watchparty.watchparty.user.repository;

import com.watchparty.watchparty.user.entity.User;
import com.watchparty.watchparty.user.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import javax.swing.text.html.Option;
import java.util.Optional;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

    Optional<UserProfile> findByUser(User user);

    Optional<UserProfile> findByNickname(String nickname);

    // userId로만 닉네임 뽑기
    @Query("select up.nickname from UserProfile up where up.user.id = :userId")
    Optional<String> findNicknameByUserId(Long userId);

    // userId로 프로필 이미지 key 뽑기 (없거나 null이면 empty)
    @Query("select up.profileImageKey from UserProfile up where up.user.id = :userId")
    Optional<String> findProfileImageKeyByUserId(Long userId);
}
