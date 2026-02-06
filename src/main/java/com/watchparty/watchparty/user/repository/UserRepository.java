package com.watchparty.watchparty.user.repository;

import com.watchparty.watchparty.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existByEmail(String email);
}
