package com.watchparty.watchparty.user.repository;

import com.watchparty.watchparty.user.entity.User;
import com.watchparty.watchparty.user.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

    Optional<UserProfile> findByUser(User user);

    Optional<UserProfile> findByNickname(String nickname);
}
