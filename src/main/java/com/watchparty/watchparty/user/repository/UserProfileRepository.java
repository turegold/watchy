package com.watchparty.watchparty.user.repository;

import com.watchparty.watchparty.user.dto.UserNickname;
import com.watchparty.watchparty.user.dto.UserProfileSummary;
import com.watchparty.watchparty.user.entity.User;
import com.watchparty.watchparty.user.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
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

    // 닉네임 + 프로필 이미지 key를 한 번에 (채팅 메시지 조립 시 따로 2번 조회하던 걸 1번으로)
    @Query("select new com.watchparty.watchparty.user.dto.UserProfileSummary(up.nickname, up.profileImageKey) " +
            "from UserProfile up where up.user.id = :userId")
    Optional<UserProfileSummary> findSummaryByUserId(Long userId);

    // 여러 유저의 닉네임을 한 번에 (방장 변경처럼 userId가 여러 개 필요할 때 각각 조회하지 않도록)
    @Query("select new com.watchparty.watchparty.user.dto.UserNickname(up.user.id, up.nickname) " +
            "from UserProfile up where up.user.id in :userIds")
    List<UserNickname> findNicknamesByUserIds(@Param("userIds") Collection<Long> userIds);
}
