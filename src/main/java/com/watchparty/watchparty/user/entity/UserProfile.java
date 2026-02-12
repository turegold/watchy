package com.watchparty.watchparty.user.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_profile")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Column(nullable = false)
    private int level;

    @Column(nullable = false)
    private long exp;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public UserProfile(User user, String nickname) {
        this.user = user;
        this.nickname = nickname;
        this.level = 1;
        this.exp = 0L;
        this.createdAt = LocalDateTime.now();
    }

    public void addExp(long amount){
        this.exp += amount;
        updateLevel();
    }

    public void updateLevel(){
        this.level = (int) (this.exp / 1000) + 1;
    }

    public void updateNickname(String nickname){
        this.nickname = nickname;
    }
}
