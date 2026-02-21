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

    @Query("select up.nickname from UserProfile up where up.user.id = :userId")
    Optional<String> findNicknameByUserId(Long userId);
}
