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

    // S3 객체 key (프로필 이미지). null이면 기본 이미지 사용
    @Column(length = 255)
    private String profileImageKey;

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

    // 프로필 이미지 key 갱신. 이전 key를 반환(호출부에서 S3 삭제에 사용)
    public String updateProfileImageKey(String newKey){
        String previousKey = this.profileImageKey;
        this.profileImageKey = newKey;
        return previousKey;
    }
}
