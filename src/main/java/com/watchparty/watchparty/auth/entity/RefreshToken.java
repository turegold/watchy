package com.watchparty.watchparty.auth.entity;


import com.watchparty.watchparty.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Entity
@Table(
        name = "refresh_tokens",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_refresh_token_user", columnNames = "user_id"),
                @UniqueConstraint(name = "uk_refresh_token_token", columnNames = "token")
        }
)
public class RefreshToken {
    // iD
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // user_id
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_refresh_token_user")
    )
    private User user;

    // refresh token 문자열
    @Column(nullable = false, length = 500)
    private String token;

    // 만료 일시
    @Column(nullable = false)
    private LocalDateTime expiryDate;

    // 기존 refresh 토큰이 있으면, 갱신할 때 사용
    public void update(String token, LocalDateTime expiryDate){
        this.token = token;
        this.expiryDate = expiryDate;
    }

    // 현재 시간이 expiryDate를 넘었는지 확인
    public boolean isExpired(LocalDateTime now){
        return now.isAfter(expiryDate);
    }
}
